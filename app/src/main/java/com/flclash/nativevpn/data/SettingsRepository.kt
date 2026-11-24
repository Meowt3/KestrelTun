package com.flclash.nativevpn.data

import android.content.Context
import android.content.SharedPreferences
import com.flclash.nativevpn.model.AutoPingConfig
import com.flclash.nativevpn.model.CustomProxyGroup
import com.flclash.nativevpn.model.ProxyGroupType
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.model.ProxyViewConfig
import com.flclash.nativevpn.model.RoutingConfig
import com.flclash.nativevpn.model.SplitTunnelConfig
import com.flclash.nativevpn.model.SubscriptionProfile
import com.flclash.nativevpn.model.ThemeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodesFile = File(context.applicationContext.filesDir, "nodes_v2.json")

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("flclash_native_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _nodes = MutableStateFlow(loadNodes())
    val nodes: StateFlow<List<ProxyNode>> = _nodes.asStateFlow()

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<List<SubscriptionProfile>> = _subscriptions.asStateFlow()

    private val _customGroups = MutableStateFlow(loadCustomGroups())
    val customGroups: StateFlow<List<CustomProxyGroup>> = _customGroups.asStateFlow()

    private val _activeGroupId = MutableStateFlow(prefs.getString(KEY_ACTIVE_GROUP_ID, GROUP_ALL_ID) ?: GROUP_ALL_ID)
    val activeGroupId: StateFlow<String> = _activeGroupId.asStateFlow()

    private val _selectedNodeId = MutableStateFlow(prefs.getString(KEY_SELECTED_NODE_ID, null))
    val selectedNodeId: StateFlow<String?> = _selectedNodeId.asStateFlow()

    private val _selectedNodeName = MutableStateFlow(prefs.getString(KEY_SELECTED_NODE, null))
    val selectedNodeName: StateFlow<String?> = _selectedNodeName.asStateFlow()

    private val _routingConfig = MutableStateFlow(loadRoutingConfig())
    val routingConfig: StateFlow<RoutingConfig> = _routingConfig.asStateFlow()

    private val _autoPingConfig = MutableStateFlow(loadAutoPingConfig())
    val autoPingConfig: StateFlow<AutoPingConfig> = _autoPingConfig.asStateFlow()

    private val _splitTunnelConfig = MutableStateFlow(loadSplitTunnelConfig())
    val splitTunnelConfig: StateFlow<SplitTunnelConfig> = _splitTunnelConfig.asStateFlow()

    private val _themeConfig = MutableStateFlow(loadThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    private val _proxyViewConfig = MutableStateFlow(loadProxyViewConfig())
    val proxyViewConfig: StateFlow<ProxyViewConfig> = _proxyViewConfig.asStateFlow()

    fun saveNodes(list: List<ProxyNode>) {
        _nodes.value = list
        val encoded = json.encodeToString(list)
        ioScope.launch {
            runCatching { nodesFile.writeText(encoded, Charsets.UTF_8) }
        }
    }

    fun saveSubscriptions(list: List<SubscriptionProfile>) {
        _subscriptions.value = list
        prefs.edit().putString(KEY_SUBSCRIPTIONS, json.encodeToString(list)).apply()
    }

    fun saveCustomGroups(list: List<CustomProxyGroup>) {
        _customGroups.value = list
        prefs.edit().putString(KEY_CUSTOM_GROUPS, json.encodeToString(list)).apply()
    }

    fun setActiveGroupId(groupId: String) {
        _activeGroupId.value = groupId
        prefs.edit().putString(KEY_ACTIVE_GROUP_ID, groupId).apply()
    }

    fun setSelectedNode(node: ProxyNode) {
        _selectedNodeId.value = node.id
        _selectedNodeName.value = node.name
        prefs.edit()
            .putString(KEY_SELECTED_NODE_ID, node.id)
            .putString(KEY_SELECTED_NODE, node.name)
            .apply()
    }

    fun setSelectedNodeName(name: String?) {
        _selectedNodeName.value = name
        prefs.edit().putString(KEY_SELECTED_NODE, name).apply()
    }

    fun updateRoutingConfig(transform: (RoutingConfig) -> RoutingConfig) {
        val updated = transform(_routingConfig.value)
        _routingConfig.value = updated
        prefs.edit().putString(KEY_ROUTING, json.encodeToString(updated)).apply()
    }

    fun updateAutoPingConfig(transform: (AutoPingConfig) -> AutoPingConfig) {
        val updated = transform(_autoPingConfig.value)
        _autoPingConfig.value = updated
        prefs.edit().putString(KEY_AUTO_PING, json.encodeToString(updated)).apply()
    }

    fun updateThemeConfig(transform: (ThemeConfig) -> ThemeConfig) {
        val updated = transform(_themeConfig.value)
        _themeConfig.value = updated
        prefs.edit().putString(KEY_THEME, json.encodeToString(updated)).apply()
    }

    fun updateSplitTunnelConfig(transform: (SplitTunnelConfig) -> SplitTunnelConfig) {
        val updated = transform(_splitTunnelConfig.value)
        _splitTunnelConfig.value = updated
        prefs.edit().putString(KEY_SPLIT_TUNNEL, json.encodeToString(updated)).apply()
    }

    fun updateProxyViewConfig(transform: (ProxyViewConfig) -> ProxyViewConfig) {
        val updated = transform(_proxyViewConfig.value)
        _proxyViewConfig.value = updated
        prefs.edit().putString(KEY_PROXY_VIEW, json.encodeToString(updated)).apply()
    }

    fun isGroupAuto(groupId: String): Boolean {
        if (groupId == GROUP_AUTO_ID) return true
        if (groupId == GROUP_ALL_ID) return false
        return _customGroups.value.firstOrNull { it.id == groupId }?.type == ProxyGroupType.AUTO
    }

    fun getGroupAutoIntervalMinutes(groupId: String): Int {
        if (groupId == GROUP_AUTO_ID) return _autoPingConfig.value.intervalMinutes.coerceAtLeast(1)
        val custom = _customGroups.value.firstOrNull { it.id == groupId }
        return (custom?.autoIntervalMinutes ?: _autoPingConfig.value.intervalMinutes).coerceAtLeast(1)
    }

    fun resolveNodesForGroup(groupId: String, allNodes: List<ProxyNode> = _nodes.value): List<ProxyNode> {
        if (groupId == GROUP_ALL_ID || groupId == GROUP_AUTO_ID) return allNodes
        val group = _customGroups.value.firstOrNull { it.id == groupId } ?: return allNodes
        return resolveNodesForCustomGroup(group, allNodes)
    }

    fun resolveNodesForCustomGroup(group: CustomProxyGroup, allNodes: List<ProxyNode> = _nodes.value): List<ProxyNode> {
        val keySet = group.selectedNodeKeys.toSet()
        return allNodes.filter { it.stableKey in keySet }
    }

    private fun loadNodes(): List<ProxyNode> {
        val fileRaw = runCatching { if (nodesFile.exists()) nodesFile.readText(Charsets.UTF_8) else null }.getOrNull()
        val raw = fileRaw ?: prefs.getString(KEY_NODES, null) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<List<ProxyNode>>(raw) }.getOrDefault(emptyList())
        if (fileRaw == null && parsed.isNotEmpty()) {
            ioScope.launch {
                runCatching {
                    nodesFile.writeText(raw, Charsets.UTF_8)
                    prefs.edit().remove(KEY_NODES).apply()
                }
            }
        }
        return parsed
    }

    private fun loadSubscriptions(): List<SubscriptionProfile> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SubscriptionProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadCustomGroups(): List<CustomProxyGroup> {
        val raw = prefs.getString(KEY_CUSTOM_GROUPS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CustomProxyGroup>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadRoutingConfig(): RoutingConfig {
        val raw = prefs.getString(KEY_ROUTING, null) ?: return RoutingConfig()
        return runCatching { json.decodeFromString<RoutingConfig>(raw) }.getOrDefault(RoutingConfig())
    }

    private fun loadAutoPingConfig(): AutoPingConfig {
        val raw = prefs.getString(KEY_AUTO_PING, null) ?: return AutoPingConfig()
        return runCatching { json.decodeFromString<AutoPingConfig>(raw) }.getOrDefault(AutoPingConfig())
    }

    private fun loadThemeConfig(): ThemeConfig {
        val raw = prefs.getString(KEY_THEME, null) ?: return ThemeConfig()
        return runCatching { json.decodeFromString<ThemeConfig>(raw) }.getOrDefault(ThemeConfig())
    }

    private fun loadSplitTunnelConfig(): SplitTunnelConfig {
        val raw = prefs.getString(KEY_SPLIT_TUNNEL, null) ?: return SplitTunnelConfig()
        return runCatching { json.decodeFromString<SplitTunnelConfig>(raw) }.getOrDefault(SplitTunnelConfig())
    }

    private fun loadProxyViewConfig(): ProxyViewConfig {
        val raw = prefs.getString(KEY_PROXY_VIEW, null) ?: return ProxyViewConfig()
        return runCatching { json.decodeFromString<ProxyViewConfig>(raw) }.getOrDefault(ProxyViewConfig())
    }

    companion object {
        const val GROUP_ALL_ID = "__ALL__"
        const val GROUP_AUTO_ID = "__AUTO__"
        private const val KEY_NODES = "nodes_v2"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_v2"
        private const val KEY_CUSTOM_GROUPS = "custom_groups_v3"
        private const val KEY_ACTIVE_GROUP_ID = "active_group_id_v1"
        private const val KEY_SELECTED_NODE_ID = "selected_node_id_v1"
        private const val KEY_SELECTED_NODE = "selected_node_v1"
        private const val KEY_ROUTING = "routing_config_v2"
        private const val KEY_AUTO_PING = "auto_ping_config_v2"
        private const val KEY_SPLIT_TUNNEL = "split_tunnel_v1"
        private const val KEY_THEME = "theme_config_v1"
        private const val KEY_PROXY_VIEW = "proxy_view_config_v1"
    }
}
