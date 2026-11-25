package com.flclash.nativevpn.core

import com.flclash.nativevpn.model.ActiveConnectionItem
import com.flclash.nativevpn.model.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MihomoApiClient(
    private val baseUrl: String = "http://${MihomoConfigGenerator.CONTROLLER_HOST}:${MihomoConfigGenerator.CONTROLLER_PORT}"
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Shared OkHttpClient base: derived clients share one ConnectionPool & Dispatcher
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 96
        })
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    private val groupDelayClient = client.newBuilder()
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val streamClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun isCoreAlive(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/version").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Native mihomo Go batch delay test (FlClash / FlClashX style):
     * Tests every proxy in [groupName] concurrently inside Go goroutines in a single HTTP call.
     * Returns map of exactProxyName -> latencyMs, or null if endpoint fails.
     */
    suspend fun testGroupDelay(groupName: String, testUrl: String, timeoutMs: Int): Map<String, Int>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedGroup = URLEncoder.encode(groupName, "UTF-8").replace("+", "%20")
                val encodedUrl = URLEncoder.encode(testUrl, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/group/$encodedGroup/delay?timeout=$timeoutMs&url=$encodedUrl&expected=204")
                    .get()
                    .build()
                groupDelayClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val obj = json.parseToJsonElement(body).jsonObject
                    val result = HashMap<String, Int>(obj.size)
                    for ((name, elem) in obj) {
                        val ms = elem.jsonPrimitive.intOrNull
                        if (ms != null && ms > 0) {
                            result[name] = ms
                        }
                    }
                    result
                }
            }.getOrNull()
        }

    suspend fun testProxyDelay(proxyName: String, testUrl: String, timeoutMs: Int): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedName = URLEncoder.encode(proxyName, "UTF-8").replace("+", "%20")
                val encodedUrl = URLEncoder.encode(testUrl, "UTF-8")
                val req = Request.Builder()
                    .url("$baseUrl/proxies/$encodedName/delay?timeout=$timeoutMs&url=$encodedUrl&expected=204")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val obj = json.parseToJsonElement(body).jsonObject
                    obj["delay"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                }
            }.getOrNull()
        }

    suspend fun selectProxy(groupName: String, proxyName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedGroup = URLEncoder.encode(groupName, "UTF-8").replace("+", "%20")
                val escapedName = proxyName.replace("\\", "\\\\").replace("\"", "\\\"")
                val body = "{\"name\":\"$escapedName\"}"
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/proxies/$encodedGroup")
                    .put(body)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun getSelectedProxy(groupName: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedGroup = URLEncoder.encode(groupName, "UTF-8").replace("+", "%20")
                val req = Request.Builder().url("$baseUrl/proxies/$encodedGroup").get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val obj = json.parseToJsonElement(body).jsonObject
                    obj["now"]?.jsonPrimitive?.content
                }
            }.getOrNull()
        }

    suspend fun setOutboundMode(mode: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = "{\"mode\":\"${mode.lowercase()}\"}"
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/configs")
                    .patch(body)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun closeAllConnections(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/connections").delete().build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun getConnections(): List<ActiveConnectionItem> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/connections").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val arr = root["connections"]?.jsonArray ?: return@use emptyList()
                arr.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val meta = obj["metadata"]?.jsonObject
                    val host = meta?.get("host")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: meta?.get("destinationIP")?.jsonPrimitive?.content.orEmpty()
                    val network = meta?.get("network")?.jsonPrimitive?.content?.uppercase() ?: "TCP"
                    val rule = obj["rule"]?.jsonPrimitive?.content.orEmpty()
                    val chains = obj["chains"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.content }
                        ?.joinToString(" → ").orEmpty()
                    val up = obj["upload"]?.jsonPrimitive?.longOrNull ?: 0L
                    val down = obj["download"]?.jsonPrimitive?.longOrNull ?: 0L
                    ActiveConnectionItem(
                        id = id,
                        host = host.ifBlank { "unknown" },
                        network = network,
                        rule = rule,
                        chain = chains.ifBlank { "DIRECT" },
                        uploadBytes = up,
                        downloadBytes = down
                    )
                }.take(60)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun reloadConfig(configFilePath: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val escapedPath = configFilePath.replace("\\", "\\\\").replace("\"", "\\\"")
                val body = "{\"path\":\"$escapedPath\"}"
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/configs?force=true")
                    .put(body)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    fun observeTraffic(): Flow<TrafficStats> = flow {
        val req = Request.Builder().url("$baseUrl/traffic").get().build()
        runCatching {
            streamClient.newCall(req).execute().use { resp ->
                val source = resp.body?.source() ?: return@use
                while (currentCoroutineContext().isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                    if (obj != null) {
                        val up = obj["up"]?.jsonPrimitive?.longOrNull ?: 0L
                        val down = obj["down"]?.jsonPrimitive?.longOrNull ?: 0L
                        emit(TrafficStats(uploadBytesPerSec = up, downloadBytesPerSec = down))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
