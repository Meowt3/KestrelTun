package com.flclash.nativevpn.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flclash.nativevpn.FlClashApp
import com.flclash.nativevpn.core.MihomoConfigGenerator
import com.flclash.nativevpn.core.PingEngine
import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.data.SubscriptionParser
import com.flclash.nativevpn.model.ActiveConnectionItem
import com.flclash.nativevpn.model.AutoPingConfig
import com.flclash.nativevpn.model.CustomProxyGroup
import com.flclash.nativevpn.model.GeminiBypassMode
import com.flclash.nativevpn.model.InstalledAppItem
import com.flclash.nativevpn.model.NetworkCheckResult
import com.flclash.nativevpn.model.OutboundMode
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.model.ProxySortMode
import com.flclash.nativevpn.model.ProxyViewConfig
import com.flclash.nativevpn.model.RoutingConfig
import com.flclash.nativevpn.model.SplitTunnelConfig
import com.flclash.nativevpn.model.SubscriptionProfile
import com.flclash.nativevpn.model.ThemeConfig
import com.flclash.nativevpn.vpn.FlClashVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FlClashApp
    val repository: SettingsRepository = app.repository
    private val json = Json { ignoreUnknownKeys = true }

    val runtimeState = FlClashVpnService.runtimeState
    val allNodes = repository.nodes
    val subscriptions = repository.subscriptions
    val customGroups = repository.customGroups
    val activeGroupId = repository.activeGroupId
    val selectedNodeId = repository.selectedNodeId
    val selectedNodeName = repository.selectedNodeName
    val routingConfig = repository.routingConfig
    val autoPingConfig = repository.autoPingConfig
    val splitTunnelConfig = repository.splitTunnelConfig
    val themeConfig = repository.themeConfig
    val proxyViewConfig = repository.proxyViewConfig
    val pingStates = app.pingEngine.pingStates
    val isTestingPing = app.pingEngine.isTesting
    val pingCompleted = app.pingEngine.completedCount
    val pingTotal = app.pingEngine.totalCount
    val pingFinishedVersion = app.pingEngine.finishedVersion
    val logs = app.processManager.logs
    val autoStatusText = app.autoTestScheduler.lastStatusText

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _connections = MutableStateFlow<List<ActiveConnectionItem>>(emptyList())
    val connections: StateFlow<List<ActiveConnectionItem>> = _connections.asStateFlow()

    private val _networkCheck = MutableStateFlow(NetworkCheckResult())
    val networkCheck: StateFlow<NetworkCheckResult> = _networkCheck.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    init {
        if (routingConfig.value.autoUpdateSubscriptionsOnStart && didColdStartRefresh.compareAndSet(false, true)) {
            refreshAllSubscriptions(silent = true)
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val displayedNodes: StateFlow<List<ProxyNode>> = combine(
        allNodes,
        customGroups,
        activeGroupId,
        pingStates,
        autoPingConfig,
        proxyViewConfig,
        searchQuery,
        isTestingPing
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val nodes = values[0] as List<ProxyNode>
        val groupId = values[2] as String
        @Suppress("UNCHECKED_CAST")
        val pings = values[3] as Map<String, PingState>
        val pingCfg = values[4] as AutoPingConfig
        val viewCfg = values[5] as ProxyViewConfig
        val query = (values[6] as String).trim()
        val testing = values[7] as Boolean

        var list = repository.resolveNodesForGroup(groupId, nodes)
        if (pingCfg.hideTimeouts && !testing) {
            list = list.filter { pings[it.id] !is PingState.Timeout }
        }
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.type.contains(query, ignoreCase = true) ||
                    it.server.contains(query, ignoreCase = true)
            }
        }
        when (viewCfg.sortMode) {
            ProxySortMode.DEFAULT -> list
            ProxySortMode.NAME -> list.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ProxySortMode.LATENCY -> {
                val shouldSortNow = !testing || pingCfg.sortLiveDuringTest
                PingEngine.sortNodesByLivePing(list, pings, shouldSortNow)
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun observeTraffic() = app.apiClient.observeTraffic()

    fun clearLogs() {
        app.processManager.clearLogs()
    }

    fun buildDiagnosticLogText(): String {
        val sb = StringBuilder()
        val rt = runtimeState.value
        val routing = routingConfig.value
        sb.appendLine("=== Kestrel VPN Diagnostic Log ===")
        sb.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Connected: ${rt.isConnected} (${rt.engineMode})")
        sb.appendLine("Selected UI Node: ${selectedNodeName.value ?: "-"} (id=${selectedNodeId.value ?: "-"})")
        sb.appendLine("Outbound Mode: ${routing.outboundMode}")
        sb.appendLine("AdBlock: ${routing.adBlockEnabled}, RU Bypass: ${routing.ruBypassEnabled}, Gemini: ${routing.geminiBypassEnabled} (${routing.geminiBypassMode})")
        sb.appendLine("Nodes: ${allNodes.value.size}, Subscriptions: ${subscriptions.value.size}")
        sb.appendLine("=== Core Logs (${logs.value.size} lines) ===")
        logs.value.forEach { sb.appendLine(it) }
        return sb.toString()
    }

    fun downloadLogsToDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "kestrel-mihomo-log-$timestamp.txt"
            val content = buildDiagnosticLogText()
            val saved = runCatching {
                val resolver = app.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore URI is null")
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open OutputStream")
                true
            }.getOrDefault(false)

            _uiMessage.value = if (saved) {
                "Лог сохранён в Загрузки: $fileName"
            } else {
                "Не удалось сохранить файл лога"
            }
        }
    }

    fun consumeMessage() {
        _uiMessage.value = null
    }

    fun setActiveGroup(groupId: String) {
        repository.setActiveGroupId(groupId)
        if (repository.isGroupAuto(groupId)) {
            if (runtimeState.value.isConnected) {
                app.autoTestScheduler.startOrRestart(runImmediately = autoPingConfig.value.testImmediatelyOnStart)
            } else {
                app.autoTestScheduler.selectFastestFromCurrentResults()
            }
        } else {
            app.autoTestScheduler.stop()
        }
    }

    fun setBaseAutoIntervalMinutes(minutes: Int) {
        val safe = minutes.coerceIn(1, 1440)
        repository.updateAutoPingConfig { it.copy(intervalMinutes = safe) }
        if (runtimeState.value.isConnected && repository.isGroupAuto(activeGroupId.value)) {
            app.autoTestScheduler.startOrRestart(runImmediately = false)
        }
    }

    fun toggleSplitTunnel(enabled: Boolean) {
        repository.updateSplitTunnelConfig { it.copy(enabled = enabled) }
        _uiMessage.value = if (enabled) "Разделение по приложениям включено (применится при переподключении)" else "Разделение по приложениям выключено"
    }

    fun smartSelectRuApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != app.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            val ruPatterns = listOf(
                "ru.sberbank", "com.idamob.tinkoff", "com.tbank", "ru.alfabank",
                "ru.vtb", "ru.rostel", "ru.gosuslugi", "ru.ozon", "com.wildberries",
                "ru.yandex", "com.vkontakte", "com.vk.", "ru.mail", "ru.dublgis",
                "ru.avito", "ru.kinopoisk", "ru.tele2", "ru.mts", "ru.megafon",
                "ru.beeline", "ru.raiffeisen", "ru.gazprombank", "ru.pochta",
                "ru.magnit", "ru.pyaterochka", "ru.x5", "ru.cdek", "ru.rzd",
                "ru.mos.", "ru.nalog", "ru.beru", "ru.kufar", "ru.cian",
                "ru.auto.ara", "ru.litres", "ru.rutube", "ru.dzen", "ru.ok.android"
            )
            val matched = installed
                .map { it.packageName }
                .filter { pkg ->
                    val low = pkg.lowercase()
                    low.startsWith("ru.") || ruPatterns.any { low.startsWith(it) }
                }
                .distinct()

            repository.updateSplitTunnelConfig { current ->
                current.copy(
                    enabled = true,
                    bypassSelected = true,
                    selectedPackages = (current.selectedPackages + matched).distinct()
                )
            }
            _uiMessage.value = "Автовыбор локальных приложений: добавлено ${matched.size} прил. (переподключи VPN)"
        }
    }

    fun saveCustomDomains(proxyRaw: String, directRaw: String) {
        val parse: (String) -> List<String> = { raw ->
            raw.split(",", "\n", ";", " ")
                .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/") }
                .filter { it.isNotBlank() && it.contains(".") }
                .distinct()
        }
        val proxyList = parse(proxyRaw)
        val directList = parse(directRaw)
        repository.updateRoutingConfig {
            it.copy(customProxyDomains = proxyList, customDirectDomains = directList)
        }
        triggerCoreReloadIfRunning()
        _uiMessage.value = "Домены сохранены (VPN: ${proxyList.size}, Напрямую: ${directList.size})"
    }

    fun setOutboundMode(mode: OutboundMode) {
        repository.updateRoutingConfig { it.copy(outboundMode = mode) }
        viewModelScope.launch(Dispatchers.IO) {
            if (runtimeState.value.isConnected && app.apiClient.isCoreAlive()) {
                val ok = app.apiClient.setOutboundMode(mode.name.lowercase())
                if (ok) {
                    app.apiClient.closeAllConnections()
                    app.processManager.appendLog("[MODE] Режим переключён на: ${mode.name}")
                } else {
                    triggerCoreReloadIfRunning()
                }
            }
        }
    }

    fun selectNode(node: ProxyNode) {
        repository.setSelectedNode(node)
        FlClashVpnService.updateActiveNode(node.name)
        viewModelScope.launch(Dispatchers.IO) {
            if (app.apiClient.isCoreAlive()) {
                val exactName = MihomoConfigGenerator.resolveExactName(node)
                val switched = app.apiClient.selectProxy(
                    MihomoConfigGenerator.MAIN_GROUP_NAME,
                    exactName
                )
                app.apiClient.selectProxy("GLOBAL", exactName)
                if (!switched) {
                    app.processManager.appendLog("[PROXY] selectProxy не сработал для '$exactName', перезагружаю конфиг...")
                    triggerCoreReloadIfRunning()
                } else {
                    if (autoPingConfig.value.closeConnectionsOnSwitch) {
                        app.apiClient.closeAllConnections()
                    }
                    val actual = app.apiClient.getSelectedProxy(MihomoConfigGenerator.MAIN_GROUP_NAME)
                    app.processManager.appendLog("[PROXY] Переключено на: $exactName (в ядре: ${actual ?: "-"})")
                }
            }
        }
    }

    fun startLivePingTest() {
        val targetNodes = repository.resolveNodesForGroup(activeGroupId.value)
        if (targetNodes.isEmpty()) {
            _uiMessage.value = "Нет серверов в активной группе"
            return
        }
        val cfg = autoPingConfig.value
        app.pingEngine.testNodesLive(targetNodes, cfg) { results ->
            if (repository.isGroupAuto(activeGroupId.value)) {
                val best = targetNodes.mapNotNull { node ->
                    val s = results[node.id] as? PingState.Success ?: return@mapNotNull null
                    node to s.latencyMs
                }.minWithOrNull(compareBy<Pair<ProxyNode, Int>> { it.second }.thenBy { it.first.name.lowercase() })
                if (best != null) {
                    if (best.first.id != selectedNodeId.value || best.first.name != selectedNodeName.value) {
                        selectNode(best.first)
                    }
                    app.autoTestScheduler.markSelected(best.first, best.second)
                    _uiMessage.value = "Авто #1: ${best.first.name} (${best.second} мс)"
                }
            }
        }
    }

    fun testSingleNode(node: ProxyNode) {
        app.pingEngine.testSingleNode(node, autoPingConfig.value)
    }

    fun selectFastestNow() {
        val best = app.autoTestScheduler.selectFastestFromCurrentResults()
        if (best != null) {
            _uiMessage.value = "Переключено на ${best.first.name} (${best.second} мс)"
        } else {
            startLivePingTest()
        }
    }

    fun toggleAdBlock(enabled: Boolean) {
        repository.updateRoutingConfig { it.copy(adBlockEnabled = enabled) }
        triggerCoreReloadIfRunning()
    }

    fun toggleRuBypass(enabled: Boolean) {
        repository.updateRoutingConfig { it.copy(ruBypassEnabled = enabled) }
        triggerCoreReloadIfRunning()
    }

    fun toggleGeminiBypass(enabled: Boolean) {
        repository.updateRoutingConfig { it.copy(geminiBypassEnabled = enabled) }
        triggerCoreReloadIfRunning()
    }

    fun setGeminiBypassMode(mode: GeminiBypassMode) {
        repository.updateRoutingConfig { it.copy(geminiBypassMode = mode) }
        triggerCoreReloadIfRunning()
    }

    fun updateRoutingFields(transform: (RoutingConfig) -> RoutingConfig) {
        repository.updateRoutingConfig(transform)
        triggerCoreReloadIfRunning()
    }

    fun updateAutoPing(transform: (AutoPingConfig) -> AutoPingConfig) {
        repository.updateAutoPingConfig(transform)
        if (runtimeState.value.isConnected) {
            app.autoTestScheduler.startOrRestart()
        }
    }

    fun updateTheme(transform: (ThemeConfig) -> ThemeConfig) {
        repository.updateThemeConfig(transform)
    }

    fun updateProxyView(transform: (ProxyViewConfig) -> ProxyViewConfig) {
        repository.updateProxyViewConfig(transform)
    }

    fun updateSplitTunnel(transform: (SplitTunnelConfig) -> SplitTunnelConfig) {
        repository.updateSplitTunnelConfig(transform)
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != app.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { info ->
                    InstalledAppItem(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString()
                    )
                }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    fun saveCustomGroup(group: CustomProxyGroup) {
        val current = customGroups.value.toMutableList()
        val idx = current.indexOfFirst { it.id == group.id }
        if (idx >= 0) current[idx] = group else current.add(group)
        repository.saveCustomGroups(current)
        triggerCoreReloadIfRunning()
        if (runtimeState.value.isConnected && activeGroupId.value == group.id) {
            app.autoTestScheduler.startOrRestart(runImmediately = false)
        }
        _uiMessage.value = "Группа «${group.name}» сохранена"
    }

    fun deleteCustomGroup(groupId: String) {
        repository.saveCustomGroups(customGroups.value.filterNot { it.id == groupId })
        if (activeGroupId.value == groupId) {
            repository.setActiveGroupId(SettingsRepository.GROUP_ALL_ID)
        }
        triggerCoreReloadIfRunning()
    }

    fun importSubscriptionFromUrl(name: String, url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            val result = fetchSubscription(trimmedUrl)
            _isImporting.value = false
            result.onSuccess { fetched ->
                val subId = UUID.randomUUID().toString()
                val parsed = SubscriptionParser.parse(fetched.body, subId)
                if (parsed.isEmpty()) {
                    _uiMessage.value = "Подписка загружена, но серверы не распознаны"
                    return@onSuccess
                }
                val profile = SubscriptionProfile(
                    id = subId,
                    name = name.ifBlank { "Подписка ${subscriptions.value.size + 1}" },
                    url = trimmedUrl,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    nodeCount = parsed.size,
                    usedBytes = fetched.usedBytes,
                    totalBytes = fetched.totalBytes,
                    expireEpochSeconds = fetched.expireEpochSeconds
                )
                repository.saveSubscriptions(subscriptions.value + profile)
                repository.saveNodes(allNodes.value + parsed)
                if (selectedNodeName.value == null) {
                    repository.setSelectedNode(parsed.first())
                }
                triggerCoreReloadIfRunning()
                _uiMessage.value = "Импортировано серверов: ${parsed.size}"
            }.onFailure { err ->
                _uiMessage.value = "Ошибка загрузки: ${err.message}"
            }
        }
    }

    fun refreshAllSubscriptions(silent: Boolean = false) {
        val httpProfiles = subscriptions.value.filter { it.url.startsWith("http", ignoreCase = true) }
        if (httpProfiles.isEmpty()) {
            if (!silent) _uiMessage.value = "Нет URL-подписок для обновления"
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            var updatedCount = 0
            var totalNodesCount = 0
            for (profile in httpProfiles) {
                val result = fetchSubscription(profile.url)
                result.onSuccess { fetched ->
                    val parsed = SubscriptionParser.parse(fetched.body, profile.id)
                    if (parsed.isNotEmpty()) {
                        val otherNodes = allNodes.value.filterNot { it.subscriptionId == profile.id }
                        val merged = otherNodes + parsed
                        repository.saveNodes(merged)
                        selectedNodeName.value?.let { curName ->
                            merged.firstOrNull { it.name == curName }?.let { repository.setSelectedNode(it) }
                        }
                        val updatedProfile = profile.copy(
                            lastUpdatedMillis = System.currentTimeMillis(),
                            nodeCount = parsed.size,
                            usedBytes = if (fetched.totalBytes > 0) fetched.usedBytes else profile.usedBytes,
                            totalBytes = if (fetched.totalBytes > 0) fetched.totalBytes else profile.totalBytes,
                            expireEpochSeconds = if (fetched.expireEpochSeconds > 0) fetched.expireEpochSeconds else profile.expireEpochSeconds
                        )
                        repository.saveSubscriptions(
                            subscriptions.value.map { if (it.id == profile.id) updatedProfile else it }
                        )
                        updatedCount++
                        totalNodesCount += parsed.size
                    }
                }
            }
            _isImporting.value = false
            if (updatedCount > 0) {
                triggerCoreReloadIfRunning()
                if (!silent) {
                    _uiMessage.value = "Подписка обновлена ($totalNodesCount серв.)"
                } else {
                    app.processManager.appendLog("[SUB] Автообновление при запуске: $updatedCount подп., $totalNodesCount серв.")
                }
            } else if (!silent) {
                _uiMessage.value = "Не удалось обновить подписку"
            }
        }
    }

    fun refreshSubscription(profile: SubscriptionProfile) {
        if (!profile.url.startsWith("http", ignoreCase = true)) {
            _uiMessage.value = "Локальный импорт нельзя обновить по URL"
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            val result = fetchSubscription(profile.url)
            _isImporting.value = false
            result.onSuccess { fetched ->
                val parsed = SubscriptionParser.parse(fetched.body, profile.id)
                if (parsed.isEmpty()) {
                    _uiMessage.value = "Не удалось распознать серверы при обновлении"
                    return@onSuccess
                }
                val otherNodes = allNodes.value.filterNot { it.subscriptionId == profile.id }
                repository.saveNodes(otherNodes + parsed)

                val updatedProfile = profile.copy(
                    lastUpdatedMillis = System.currentTimeMillis(),
                    nodeCount = parsed.size,
                    usedBytes = if (fetched.totalBytes > 0) fetched.usedBytes else profile.usedBytes,
                    totalBytes = if (fetched.totalBytes > 0) fetched.totalBytes else profile.totalBytes,
                    expireEpochSeconds = if (fetched.expireEpochSeconds > 0) fetched.expireEpochSeconds else profile.expireEpochSeconds
                )
                repository.saveSubscriptions(
                    subscriptions.value.map { if (it.id == profile.id) updatedProfile else it }
                )
                triggerCoreReloadIfRunning()
                _uiMessage.value = "Обновлено «${profile.name}»: ${parsed.size} серверов"
            }.onFailure { err ->
                _uiMessage.value = "Ошибка обновления: ${err.message}"
            }
        }
    }

    fun importRawText(rawText: String, sourceName: String = "Ручной импорт") {
        val subId = UUID.randomUUID().toString()
        val parsed = SubscriptionParser.parse(rawText, subId)
        if (parsed.isEmpty()) {
            _uiMessage.value = "Не найдено валидных ссылок или Clash YAML"
            return
        }
        val profile = SubscriptionProfile(
            id = subId,
            name = sourceName,
            url = "local://import",
            lastUpdatedMillis = System.currentTimeMillis(),
            nodeCount = parsed.size
        )
        repository.saveSubscriptions(subscriptions.value + profile)
        repository.saveNodes(allNodes.value + parsed)
        if (selectedNodeName.value == null) {
            repository.setSelectedNode(parsed.first())
        }
        triggerCoreReloadIfRunning()
        _uiMessage.value = "Добавлено серверов: ${parsed.size}"
    }

    fun deleteSubscription(subscriptionId: String) {
        repository.saveSubscriptions(subscriptions.value.filterNot { it.id == subscriptionId })
        repository.saveNodes(allNodes.value.filterNot { it.subscriptionId == subscriptionId })
        triggerCoreReloadIfRunning()
    }

    fun refreshConnections() {
        viewModelScope.launch {
            _connections.value = app.apiClient.getConnections()
        }
    }

    fun closeConnectionsNow() {
        viewModelScope.launch {
            app.apiClient.closeAllConnections()
            _connections.value = emptyList()
            _uiMessage.value = "Все активные соединения сброшены"
        }
    }

    fun checkNetworkAndGemini() {
        viewModelScope.launch {
            _networkCheck.value = _networkCheck.value.copy(isChecking = true)
            val result = withContext(Dispatchers.IO) {
                val clientBuilder = httpClient.newBuilder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                if (runtimeState.value.isConnected) {
                    clientBuilder.proxy(
                        Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress("127.0.0.1", MihomoConfigGenerator.MIXED_PORT)
                        )
                    )
                }
                val checkClient = clientBuilder.build()

                var ip = "Не определено"
                var country = ""
                val ipEndpoints = listOf(
                    "https://api.ip.sb/geoip" to ("ip" to "country"),
                    "https://ipwho.is/" to ("ip" to "country"),
                    "https://api.ipify.org?format=json" to ("ip" to "")
                )
                for ((url, keys) in ipEndpoints) {
                    val ok = runCatching {
                        val req = Request.Builder().url(url).header("User-Agent", "curl/8.0").get().build()
                        checkClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@use false
                            val body = resp.body?.string().orEmpty()
                            val obj = json.parseToJsonElement(body).jsonObject
                            val foundIp = obj[keys.first]?.jsonPrimitive?.content
                            if (!foundIp.isNullOrBlank()) {
                                ip = foundIp
                                country = if (keys.second.isNotEmpty()) obj[keys.second]?.jsonPrimitive?.content.orEmpty() else ""
                                true
                            } else false
                        }
                    }.getOrDefault(false)
                    if (ok) break
                }

                var geminiOk = false
                var geminiDetail = "Нет ответа"
                runCatching {
                    val req = Request.Builder().url("https://gemini.google.com/").get().build()
                    checkClient.newCall(req).execute().use { resp ->
                        geminiOk = resp.code in 200..399
                        geminiDetail = "HTTP ${resp.code}"
                    }
                }.onFailure { err ->
                    geminiDetail = err.message?.take(40) ?: "Ошибка сети"
                }

                NetworkCheckResult(
                    ip = ip,
                    country = country,
                    geminiReachable = geminiOk,
                    geminiDetail = geminiDetail,
                    isChecking = false
                )
            }
            _networkCheck.value = result
        }
    }

    fun getLocalLanIp(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress.orEmpty()
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            "192.168.x.x"
        }.getOrDefault("192.168.x.x")
    }

    fun generatePreviewYaml(): String = MihomoConfigGenerator.generate(
        allNodes = allNodes.value,
        customGroups = customGroups.value,
        activeGroupId = activeGroupId.value,
        routing = routingConfig.value,
        autoPing = autoPingConfig.value,
        repository = repository
    )

    fun stopVpnService() {
        val intent = Intent(app, FlClashVpnService::class.java).apply {
            action = FlClashVpnService.ACTION_STOP
        }
        app.startService(intent)
    }

    fun startVpnService() {
        val intent = Intent(app, FlClashVpnService::class.java).apply {
            action = FlClashVpnService.ACTION_START
        }
        app.startForegroundService(intent)
    }

    private fun triggerCoreReloadIfRunning() {
        if (!runtimeState.value.isConnected) return
        val intent = Intent(app, FlClashVpnService::class.java).apply {
            action = FlClashVpnService.ACTION_RELOAD
        }
        app.startService(intent)
    }

    private suspend fun fetchSubscription(url: String): Result<FetchedSubscription> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ClashMeta/1.19 FlClashNative/2.0")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val userInfo = resp.header("Subscription-Userinfo").orEmpty()
                    val parsedInfo = parseUserInfoHeader(userInfo)
                    FetchedSubscription(
                        body = resp.body?.string().orEmpty(),
                        usedBytes = parsedInfo.first,
                        totalBytes = parsedInfo.second,
                        expireEpochSeconds = parsedInfo.third
                    )
                }
            }
        }

    private fun parseUserInfoHeader(header: String): Triple<Long, Long, Long> {
        if (header.isBlank()) return Triple(0L, 0L, 0L)
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        header.split(";").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) {
                val v = kv[1].trim().toLongOrNull() ?: 0L
                when (kv[0].trim().lowercase()) {
                    "upload" -> upload = v
                    "download" -> download = v
                    "total" -> total = v
                    "expire" -> expire = v
                }
            }
        }
        return Triple(upload + download, total, expire)
    }

    private data class FetchedSubscription(
        val body: String,
        val usedBytes: Long,
        val totalBytes: Long,
        val expireEpochSeconds: Long
    )

    companion object {
        private val didColdStartRefresh = AtomicBoolean(false)
    }
}
