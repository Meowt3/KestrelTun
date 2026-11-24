package com.flclash.nativevpn

import com.flclash.nativevpn.core.PingEngine
import com.flclash.nativevpn.model.AppThemeMode
import com.flclash.nativevpn.model.AppThemePalette
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.model.ThemeConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KestrelCoreTest {

    private fun node(id: String, name: String) = ProxyNode(
        id = id,
        name = name,
        type = "vless",
        server = "1.2.3.4",
        port = 443,
        rawYaml = "{name: \"$name\", type: vless, server: 1.2.3.4, port: 443}"
    )

    @Test
    fun sortNodesByLivePing_ordersSuccessAscThenTestingThenIdleThenTimeout() {
        val nodes = listOf(
            node("1", "Z-Timeout"),
            node("2", "B-Slow"),
            node("3", "A-Fast"),
            node("4", "C-Testing"),
            node("5", "D-Idle"),
            node("6", "E-FastTie")
        )
        val pings = mapOf(
            "1" to PingState.Timeout,
            "2" to PingState.Success(140),
            "3" to PingState.Success(55),
            "4" to PingState.Testing,
            "5" to PingState.Idle,
            "6" to PingState.Success(55)
        )

        val sorted = PingEngine.sortNodesByLivePing(nodes, pings, sortEnabled = true)
        assertEquals(
            listOf("3", "6", "2", "4", "5", "1"),
            sorted.map { it.id }
        )
    }

    @Test
    fun sortNodesByLivePing_preservesOrderWhenDisabled() {
        val nodes = listOf(node("1", "First"), node("2", "Second"), node("3", "Third"))
        val pings = mapOf("1" to PingState.Timeout, "2" to PingState.Success(20), "3" to PingState.Success(50))
        val result = PingEngine.sortNodesByLivePing(nodes, pings, sortEnabled = false)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
    }

    @Test
    fun themeConfig_supportsAmoledModeWithAllPalettesAndLegacyDeserialization() {
        val json = Json { ignoreUnknownKeys = true }
        // Legacy saved config with palette = AMOLED must still deserialize safely
        val legacy = json.decodeFromString<ThemeConfig>("""{"mode":"DARK","palette":"AMOLED"}""")
        assertEquals(AppThemeMode.DARK, legacy.mode)
        assertEquals(AppThemePalette.AMOLED, legacy.palette)

        // New AMOLED mode combined with any color palette
        val modern = ThemeConfig(mode = AppThemeMode.AMOLED, palette = AppThemePalette.CRIMSON)
        val encoded = json.encodeToString(modern)
        val decoded = json.decodeFromString<ThemeConfig>(encoded)
        assertEquals(AppThemeMode.AMOLED, decoded.mode)
        assertEquals(AppThemePalette.CRIMSON, decoded.palette)
        assertTrue(AppThemePalette.entries.size >= 9)
    }

    @Test
    fun pingEngineConstants_matchSafeFastDefaults() {
        assertEquals(64, PingEngine.SAFE_FAST_CONCURRENCY)
        assertEquals(3000, PingEngine.DEFAULT_TIMEOUT_MS)
        assertEquals("https://www.gstatic.com/generate_204", PingEngine.DEFAULT_TEST_URL)
    }

    @Test
    fun smartQueue_putsPreviouslyAliveNodesFirstAndTimeoutsLast() {
        val nodes = listOf(node("dead", "Dead"), node("new", "New"), node("slow", "Slow"), node("fast", "Fast"))
        val prev = mapOf(
            "dead" to PingState.Timeout,
            "slow" to PingState.Success(220),
            "fast" to PingState.Success(45)
        )
        val ordered = PingEngine.orderNodesForSmartQueue(nodes, prev)
        assertEquals(listOf("fast", "slow", "new", "dead"), ordered.map { it.id })
    }
}
