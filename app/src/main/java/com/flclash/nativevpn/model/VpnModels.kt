package com.flclash.nativevpn.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class GeminiBypassMode {
    CUSTOM_DNS,
    PROXY_DOH
}

@Serializable
enum class OutboundMode {
    RULE,
    GLOBAL,
    DIRECT
}

@Serializable
enum class ProxyGroupType {
    MANUAL,
    AUTO
}

@Serializable
enum class ProxySortMode {
    DEFAULT,
    LATENCY,
    NAME
}

@Serializable
enum class ProxyLayoutMode {
    LOOSE,      // 1 column
    STANDARD,   // 2 columns
    DENSE       // 3 columns
}

@Serializable
enum class ProxyCardSize {
    STANDARD,
    COMPACT,
    MINIMAL
}

@Immutable
@Serializable
data class ProxyViewConfig(
    val sortMode: ProxySortMode = ProxySortMode.LATENCY,
    val layoutMode: ProxyLayoutMode = ProxyLayoutMode.STANDARD,
    val cardSize: ProxyCardSize = ProxyCardSize.MINIMAL
)

@Immutable
@Serializable
data class ProxyNode(
    val id: String,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val rawYaml: String,
    val subscriptionId: String = "default"
) {
    val stableKey: String
        get() = "${name.trim()}|${server.trim()}:$port"
}

@Immutable
sealed interface PingState {
    data object Idle : PingState
    data object Testing : PingState
    data class Success(val latencyMs: Int) : PingState
    data object Timeout : PingState
}

@Immutable
@Serializable
data class CustomProxyGroup(
    val id: String,
    val name: String,
    val selectedNodeKeys: List<String> = emptyList(),
    val type: ProxyGroupType = ProxyGroupType.MANUAL,
    val autoIntervalMinutes: Int = 15
)

@Immutable
@Serializable
data class SplitTunnelConfig(
    val enabled: Boolean = false,
    val bypassSelected: Boolean = true,
    val selectedPackages: List<String> = emptyList()
)

@Immutable
@Serializable
data class RoutingConfig(
    val outboundMode: OutboundMode = OutboundMode.RULE,
    val adBlockEnabled: Boolean = true,
    val ruBypassEnabled: Boolean = true,
    val geminiBypassEnabled: Boolean = true,
    val geminiBypassMode: GeminiBypassMode = GeminiBypassMode.PROXY_DOH,
    val geminiCustomDns: String = "https://dns.comss.one/dns-query",
    val directDns: String = "77.88.8.8",
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val useRuleProviders: Boolean = true,
    val allowLan: Boolean = false,
    val customProxyDomains: List<String> = emptyList(),
    val customDirectDomains: List<String> = emptyList(),
    val autoUpdateSubscriptionsOnStart: Boolean = true
)

@Immutable
@Serializable
data class AutoPingConfig(
    val enabled: Boolean = true,
    val testImmediatelyOnStart: Boolean = true,
    val intervalMinutes: Int = 15,
    val autoSelectFastest: Boolean = true,
    val autoModeGroupId: String? = null,
    val pauseWhenScreenOff: Boolean = true,
    val respectBatterySaver: Boolean = true,
    val smartCheckCurrentFirst: Boolean = true,
    val goodLatencyThresholdMs: Int = 150,
    val toleranceMs: Int = 10,
    val sortLiveDuringTest: Boolean = true,
    val hideTimeouts: Boolean = false,
    val closeConnectionsOnSwitch: Boolean = true,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val timeoutMs: Int = 3000,
    val maxConcurrency: Int = 64
)

@Immutable
@Serializable
data class SubscriptionProfile(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdatedMillis: Long = 0L,
    val nodeCount: Int = 0,
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireEpochSeconds: Long = 0L
)

@Immutable
@Serializable
data class TrafficStats(
    val uploadBytesPerSec: Long = 0L,
    val downloadBytesPerSec: Long = 0L
)

@Immutable
@Serializable
data class ActiveConnectionItem(
    val id: String,
    val host: String,
    val network: String,
    val rule: String,
    val chain: String,
    val uploadBytes: Long,
    val downloadBytes: Long
)

@Immutable
@Serializable
data class NetworkCheckResult(
    val ip: String = "",
    val country: String = "",
    val geminiReachable: Boolean? = null,
    val geminiDetail: String = "",
    val isChecking: Boolean = false
)

@Immutable
@Serializable
data class InstalledAppItem(
    val packageName: String,
    val label: String
)

enum class CoreEngineMode {
    STOPPED,
    STARTING,
    RUNNING_FULL_TUN,
    RUNNING_PROXY_VPN,
    MISSING_CORE_BINARY,
    ERROR
}

@Immutable
data class VpnRuntimeState(
    val isConnected: Boolean = false,
    val engineMode: CoreEngineMode = CoreEngineMode.STOPPED,
    val activeNodeName: String? = null,
    val statusMessage: String = "Отключено",
    val coreBinaryFound: Boolean = false,
    val tunBinaryFound: Boolean = false,
    val connectedAtElapsedMs: Long = 0L,
    val traffic: TrafficStats = TrafficStats()
)

@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

@Serializable
enum class AppThemePalette {
    DYNAMIC,
    OCEAN,
    EMERALD,
    TEAL,
    VIOLET,
    CRIMSON,
    AMBER,
    ROSE,
    MONOCHROME,
    AMOLED // kept for safe backward-compat deserialization of older saved prefs
}

@Immutable
@Serializable
data class ThemeConfig(
    val mode: AppThemeMode = AppThemeMode.SYSTEM,
    val palette: AppThemePalette = AppThemePalette.DYNAMIC
)
