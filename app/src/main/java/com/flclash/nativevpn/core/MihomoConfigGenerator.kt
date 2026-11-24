package com.flclash.nativevpn.core

import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.model.AutoPingConfig
import com.flclash.nativevpn.model.CustomProxyGroup
import com.flclash.nativevpn.model.GeminiBypassMode
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.model.RoutingConfig
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

object MihomoConfigGenerator {

    const val MIXED_PORT = 17890
    const val DNS_PORT = 11053
    const val CONTROLLER_HOST = "127.0.0.1"
    const val CONTROLLER_PORT = 19090
    const val MAIN_GROUP_NAME = "PROXY"
    const val AUTO_GROUP_NAME = "AUTO-FASTEST"
    const val GEMINI_GROUP_NAME = "GEMINI"

    private val GEMINI_DOMAINS = listOf(
        "gemini.google.com",
        "generativelanguage.googleapis.com",
        "aistudio.google.com",
        "makersuite.google.com",
        "alkalimakersuite-pa.clients6.google.com",
        "geller-pa.googleapis.com",
        "proactivebackend-pa.googleapis.com",
        "assistant-s3-pa.googleapis.com",
        "bard.google.com",
        "deepmind.com",
        "deepmind.google",
        "notebooklm.google.com",
        "notebooklm.google",
        "aisandbox-pa.googleapis.com",
        "robinfrontend-pa.googleapis.com",
        "waa-pa.googleapis.com",
        "ai.google.dev",
        "signaler-pa.clients6.google.com",
        "clients6.google.com",
        "ogs.google.com"
    )

    private val BUILTIN_AD_DOMAINS = listOf(
        "adservice.google.com",
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "pagead2.googlesyndication.com",
        "an.yandex.ru",
        "mc.yandex.ru",
        "appmetrica.yandex.ru",
        "ads.vk.com",
        "top-fwz1.mail.ru",
        "ad.mail.ru",
        "ads.tiktok.com",
        "analytics.tiktok.com",
        "graph.instagram.com"
    )

    private val BUILTIN_RU_SUFFIXES = listOf(
        "ru",
        "su",
        "xn--p1ai",
        "gosuslugi.ru",
        "sberbank.ru",
        "tbank.ru",
        "vk.com",
        "vk.me",
        "yandex.ru",
        "ya.ru",
        "ozon.ru",
        "wildberries.ru",
        "avito.ru",
        "kinopoisk.ru",
        "2gis.ru",
        "mail.ru",
        "dzen.ru",
        "rutube.ru"
    )

    @Suppress("UNCHECKED_CAST")
    @Volatile
    private var lastIdToExactName: Map<String, String> = emptyMap()

    fun resolveExactName(node: ProxyNode): String =
        lastIdToExactName[node.id] ?: sanitizeName(node.name, node.server, node.port)

    fun resolveExactNameByIdOrName(
        selectedId: String?,
        rawName: String?,
        allNodes: List<ProxyNode>
    ): String? {
        if (!selectedId.isNullOrBlank()) {
            lastIdToExactName[selectedId]?.let { return it }
            allNodes.firstOrNull { it.id == selectedId }?.let { return resolveExactName(it) }
        }
        if (!rawName.isNullOrBlank()) {
            allNodes.firstOrNull { it.name == rawName }?.let { return resolveExactName(it) }
            return rawName.replace("\r", " ").replace("\n", " ").replace("\t", " ").replace("\"", "'").trim()
        }
        return null
    }

    private fun sanitizeName(name: String, server: String, port: Int): String =
        name.replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .replace("\"", "'")
            .trim()
            .ifBlank { "$server:$port" }

    private fun normalizeProxies(nodes: List<ProxyNode>): Pair<List<Pair<String, String>>, Map<String, String>> {
        val yaml = Yaml()
        val flowDumper = Yaml(DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.FLOW
            isAllowUnicode = true
            width = 4096
        })
        val seenNames = HashSet<String>()
        val resultList = ArrayList<Pair<String, String>>(nodes.size)
        val idToExactName = HashMap<String, String>(nodes.size)

        for (node in nodes) {
            val parsedMap = runCatching { yaml.load<Any>(node.rawYaml) as? Map<String, Any?> }.getOrNull()
            if (parsedMap == null) continue
            val cleanMap = LinkedHashMap<String, Any>()
            for ((k, v) in parsedMap) {
                if (v != null) cleanMap[k] = v
            }
            var candidate = sanitizeName(node.name, node.server, node.port)

            if (candidate in seenNames) {
                var idx = 2
                while ("$candidate #$idx" in seenNames) idx++
                candidate = "$candidate #$idx"
            }
            seenNames.add(candidate)
            cleanMap["name"] = candidate
            val dumped = flowDumper.dump(cleanMap).trim()
            resultList.add(candidate to dumped)
            idToExactName[node.id] = candidate
        }
        return resultList to idToExactName
    }

    fun generate(
        allNodes: List<ProxyNode>,
        customGroups: List<CustomProxyGroup>,
        activeGroupId: String,
        routing: RoutingConfig,
        autoPing: AutoPingConfig,
        repository: SettingsRepository
    ): String {
        val (normalizedProxies, idToExactName) = normalizeProxies(allNodes)
        lastIdToExactName = idToExactName
        val allExactNames = normalizedProxies.map { it.first }
        val validNamesSet = allExactNames.toHashSet()

        val sb = StringBuilder()

        sb.appendLine("# Generated by Kestrel VPN (mihomo core)")
        sb.appendLine("mixed-port: $MIXED_PORT")
        sb.appendLine("allow-lan: ${routing.allowLan}")
        sb.appendLine("bind-address: ${if (routing.allowLan) "\"*\"" else "127.0.0.1"}")
        sb.appendLine("mode: ${routing.outboundMode.name.lowercase()}")
        sb.appendLine("log-level: warning")
        sb.appendLine("ipv6: false")
        sb.appendLine("external-controller: $CONTROLLER_HOST:$CONTROLLER_PORT")
        sb.appendLine("unified-delay: true")
        sb.appendLine("tcp-concurrent: false")
        sb.appendLine("find-process-mode: off")
        sb.appendLine("keep-alive-idle: 15")
        sb.appendLine("keep-alive-interval: 15")
        sb.appendLine("profile:")
        sb.appendLine("  store-selected: false")
        sb.appendLine("  store-fake-ip: false")
        sb.appendLine()

        val directDns = routing.directDns.ifBlank { "77.88.8.8" }
        val remoteDns = routing.remoteDns.ifBlank { "https://1.1.1.1/dns-query" }

        // FlClash-aligned DNS:
        // - proxy-server-nameserver resolves proxy server domains directly via local ISP DNS (no bootstrap deadlock)
        // - respect-rules: true routes remote DoH (1.1.1.1 / dns.google) THROUGH PROXY so RU ISP/TSPU cannot block it
        // - direct-nameserver resolves DIRECT (.ru) domains locally & fast
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  listen: 127.0.0.1:$DNS_PORT")
        sb.appendLine("  ipv6: false")
        sb.appendLine("  enhanced-mode: redir-host")
        sb.appendLine("  respect-rules: true")
        sb.appendLine("  default-nameserver:")
        sb.appendLine("    - ${yamlScalar(directDns)}")
        sb.appendLine("    - 77.88.8.1")
        sb.appendLine("    - 1.1.1.1")
        sb.appendLine("  proxy-server-nameserver:")
        sb.appendLine("    - ${yamlScalar(directDns)}")
        sb.appendLine("    - 77.88.8.1")
        sb.appendLine("    - 223.5.5.5")
        sb.appendLine("  direct-nameserver:")
        sb.appendLine("    - ${yamlScalar(directDns)}")
        sb.appendLine("    - 77.88.8.1")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - ${yamlScalar(remoteDns)}")
        sb.appendLine("    - https://dns.google/dns-query")

        val dnsPolicies = mutableListOf<Pair<String, String>>()
        if (routing.geminiBypassEnabled && routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS) {
            val customGeminiDns = routing.geminiCustomDns.ifBlank { "https://dns.comss.one/dns-query" }
            for (domain in GEMINI_DOMAINS) {
                dnsPolicies += "\"$domain\"" to yamlScalar(customGeminiDns)
            }
        }
        if (routing.adBlockEnabled && routing.useRuleProviders) {
            dnsPolicies += "\"rule-set:ads\"" to "rcode://success"
        }
        if (routing.ruBypassEnabled) {
            dnsPolicies += "\"+.ru\"" to yamlScalar(directDns)
            dnsPolicies += "\"+.su\"" to yamlScalar(directDns)
            dnsPolicies += "\"+.xn--p1ai\"" to yamlScalar(directDns)
        }

        if (dnsPolicies.isNotEmpty()) {
            sb.appendLine("  nameserver-policy:")
            for ((matcher, resolver) in dnsPolicies) {
                sb.appendLine("    $matcher: $resolver")
            }
        }
        sb.appendLine()

        sb.appendLine("proxies:")
        if (normalizedProxies.isEmpty()) {
            sb.appendLine("  []")
        } else {
            for ((_, rawYaml) in normalizedProxies) {
                sb.appendLine("  - $rawYaml")
            }
        }
        sb.appendLine()

        // Always resolve the exact selected node by ID first, then name
        val savedExact = resolveExactNameByIdOrName(
            selectedId = repository.selectedNodeId.value,
            rawName = repository.selectedNodeName.value,
            allNodes = allNodes
        )?.takeIf { it in validNamesSet }

        // IMPORTANT: MAIN_GROUP_NAME (PROXY) ALWAYS contains ALL normalized proxies so selectProxy()
        // NEVER fails when switching servers across groups, and savedExact is placed at index 0.
        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: $MAIN_GROUP_NAME")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        if (savedExact != null) {
            sb.appendLine("      - ${yamlScalar(savedExact)}")
        }
        for (nodeName in allExactNames) {
            if (nodeName != savedExact) {
                sb.appendLine("      - ${yamlScalar(nodeName)}")
            }
        }
        sb.appendLine("      - DIRECT")

        val validCustomGroups = customGroups.mapNotNull { group ->
            val names = repository.resolveNodesForCustomGroup(group, allNodes)
                .mapNotNull { idToExactName[it.id] }
                .filter { it in validNamesSet }
            if (names.isEmpty()) null else group.name.trim() to names
        }.filter { it.first.isNotEmpty() && it.first !in validNamesSet && it.first != MAIN_GROUP_NAME && it.first != GEMINI_GROUP_NAME }

        for ((groupName, groupNodeNames) in validCustomGroups) {
            sb.appendLine("  - name: ${yamlScalar(groupName)}")
            sb.appendLine("    type: select")
            sb.appendLine("    proxies:")
            for (nodeName in groupNodeNames) {
                sb.appendLine("      - ${yamlScalar(nodeName)}")
            }
        }

        if (routing.geminiBypassEnabled) {
            sb.appendLine("  - name: $GEMINI_GROUP_NAME")
            sb.appendLine("    type: select")
            sb.appendLine("    proxies:")
            if (routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS) {
                sb.appendLine("      - DIRECT")
                sb.appendLine("      - $MAIN_GROUP_NAME")
            } else {
                sb.appendLine("      - $MAIN_GROUP_NAME")
                sb.appendLine("      - DIRECT")
            }
        }
        sb.appendLine()

        if (routing.useRuleProviders && (routing.adBlockEnabled || routing.ruBypassEnabled)) {
            sb.appendLine("rule-providers:")
            if (routing.adBlockEnabled) {
                sb.appendLine("  ads:")
                sb.appendLine("    type: http")
                sb.appendLine("    behavior: domain")
                sb.appendLine("    format: mrs")
                sb.appendLine("    path: ./ruleset/ads.mrs")
                sb.appendLine("    url: \"https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/category-ads-all.mrs\"")
                sb.appendLine("    interval: 86400")
            }
            if (routing.ruBypassEnabled) {
                sb.appendLine("  ru-domain:")
                sb.appendLine("    type: http")
                sb.appendLine("    behavior: domain")
                sb.appendLine("    format: mrs")
                sb.appendLine("    path: ./ruleset/ru-domain.mrs")
                sb.appendLine("    url: \"https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/category-ru.mrs\"")
                sb.appendLine("    interval: 86400")
                sb.appendLine("  ru-ip:")
                sb.appendLine("    type: http")
                sb.appendLine("    behavior: ipcidr")
                sb.appendLine("    format: mrs")
                sb.appendLine("    path: ./ruleset/ru-ip.mrs")
                sb.appendLine("    url: \"https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/ru.mrs\"")
                sb.appendLine("    interval: 86400")
            }
            sb.appendLine()
        }

        sb.appendLine("rules:")
        sb.appendLine("  - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve")
        sb.appendLine("  - IP-CIDR,10.0.0.0/8,DIRECT,no-resolve")
        sb.appendLine("  - IP-CIDR,172.16.0.0/12,DIRECT,no-resolve")
        sb.appendLine("  - IP-CIDR,192.168.0.0/16,DIRECT,no-resolve")

        // User custom domain overrides (highest priority)
        for (domain in routing.customProxyDomains) {
            val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
            if (clean.isNotBlank()) {
                sb.appendLine("  - DOMAIN-SUFFIX,$clean,$MAIN_GROUP_NAME")
            }
        }
        for (domain in routing.customDirectDomains) {
            val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
            if (clean.isNotBlank()) {
                sb.appendLine("  - DOMAIN-SUFFIX,$clean,DIRECT")
            }
        }

        // Block QUIC (UDP:443) so Gemini Android app & Chrome immediately use TCP:443 HTTP/2
        // instead of hanging on blackholed QUIC packets over mobile LTE + TUN.
        sb.appendLine("  - AND,((NETWORK,UDP),(DST-PORT,443)),REJECT")

        // Gemini rules MUST come before AdBlock so ads ruleset never blocks waa-pa / signaler-pa chat APIs
        if (routing.geminiBypassEnabled) {
            for (domain in GEMINI_DOMAINS) {
                sb.appendLine("  - DOMAIN-SUFFIX,$domain,$GEMINI_GROUP_NAME")
            }
        }

        if (routing.adBlockEnabled) {
            if (routing.useRuleProviders) {
                sb.appendLine("  - RULE-SET,ads,REJECT")
            }
            for (adDomain in BUILTIN_AD_DOMAINS) {
                sb.appendLine("  - DOMAIN-SUFFIX,$adDomain,REJECT")
            }
        }

        if (routing.ruBypassEnabled) {
            for (ruSuffix in BUILTIN_RU_SUFFIXES) {
                sb.appendLine("  - DOMAIN-SUFFIX,$ruSuffix,DIRECT")
            }
            if (routing.useRuleProviders) {
                sb.appendLine("  - RULE-SET,ru-domain,DIRECT")
                sb.appendLine("  - RULE-SET,ru-ip,DIRECT,no-resolve")
            }
        }

        sb.appendLine("  - MATCH,$MAIN_GROUP_NAME")
        return sb.toString()
    }

    private fun yamlScalar(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
