package com.flclash.nativevpn.data

import android.util.Base64
import com.flclash.nativevpn.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

object SubscriptionParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawContent: String, subscriptionId: String): List<ProxyNode> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Clash / mihomo YAML config (supports every mihomo protocol natively)
        if (trimmed.contains("proxies:")) {
            val yamlNodes = parseClashYaml(trimmed, subscriptionId)
            if (yamlNodes.isNotEmpty()) return yamlNodes
        }

        // 2. Base64 or plain URI list
        val decoded = tryDecodeBase64(trimmed) ?: trimmed
        val lines = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val parsedMaps = lines.mapNotNull { line ->
            val lower = line.lowercase()
            when {
                lower.startsWith("vless://") -> parseVlessMap(line)
                lower.startsWith("vmess://") -> parseVmessMap(line)
                lower.startsWith("trojan://") -> parseTrojanMap(line)
                lower.startsWith("ss://") -> parseShadowsocksMap(line)
                lower.startsWith("ssr://") -> parseShadowsocksRMap(line)
                lower.startsWith("hysteria2://") || lower.startsWith("hy2://") -> parseHysteria2Map(line)
                lower.startsWith("hysteria://") -> parseHysteria1Map(line)
                lower.startsWith("tuic://") -> parseTuicMap(line)
                lower.startsWith("wireguard://") || lower.startsWith("wg://") -> parseWireGuardMap(line)
                lower.startsWith("socks5://") || lower.startsWith("socks://") -> parseSocksMap(line)
                lower.startsWith("http://") || lower.startsWith("https://") -> parseHttpProxyMap(line)
                else -> null
            }
        }
        return buildUniqueNodes(parsedMaps, subscriptionId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseClashYaml(yamlText: String, subscriptionId: String): List<ProxyNode> {
        return runCatching {
            val yaml = Yaml()
            val root = yaml.load<Any>(yamlText) as? Map<String, Any?> ?: return emptyList()
            val proxies = root["proxies"] as? List<Map<String, Any?>> ?: return emptyList()
            val cleanMaps = proxies.mapNotNull { rawMap ->
                val mutable = LinkedHashMap<String, Any>()
                for ((k, v) in rawMap) {
                    if (v != null) mutable[k] = v
                }
                val name = sanitizeName(mutable["name"]?.toString().orEmpty())
                val type = mutable["type"]?.toString()?.trim().orEmpty()
                val server = mutable["server"]?.toString()?.trim().orEmpty()
                val port = mutable["port"]?.toString()?.toIntOrNull() ?: 443
                if (name.isEmpty() || type.isEmpty() || server.isEmpty()) return@mapNotNull null
                mutable["name"] = name
                mutable["type"] = type
                mutable["server"] = server
                mutable["port"] = port
                mutable
            }
            buildUniqueNodes(cleanMaps, subscriptionId)
        }.getOrDefault(emptyList())
    }

    private fun buildUniqueNodes(
        maps: List<LinkedHashMap<String, Any>>,
        subscriptionId: String
    ): List<ProxyNode> {
        val seen = HashMap<String, Int>()
        return maps.map { map ->
            val baseName = sanitizeName(map["name"]?.toString().orEmpty()).ifBlank { "Node" }
            val count = seen.getOrDefault(baseName, 0)
            seen[baseName] = count + 1
            val uniqueName = if (count == 0) baseName else "$baseName #${count + 1}"
            // Guarantee the YAML map itself carries the exact deduplicated name
            map["name"] = uniqueName

            val type = map["type"]?.toString()?.lowercase().orEmpty()
            val server = map["server"]?.toString().orEmpty()
            val port = map["port"]?.toString()?.toIntOrNull() ?: 443

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = uniqueName,
                type = type,
                server = server,
                port = port,
                rawYaml = dumpFlowMap(map),
                subscriptionId = subscriptionId
            )
        }
    }

    private fun sanitizeName(raw: String): String =
        raw.replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .replace("\"", "'")
            .trim()

    private fun parseVlessMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }

        val security = params["security"].orEmpty().lowercase()
        val network = (params["type"] ?: "tcp").lowercase()
        val sni = params["sni"] ?: params["peer"] ?: host
        val fp = params["fp"] ?: "chrome"
        val flow = params["flow"].orEmpty()
        val pbk = params["pbk"].orEmpty()
        val sid = params["sid"].orEmpty()
        val path = params["path"] ?: "/"
        val hostHeader = params["host"].orEmpty()
        val serviceName = params["serviceName"].orEmpty()
        val alpn = params["alpn"].orEmpty()

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "vless",
            "server" to host,
            "port" to port,
            "uuid" to uuid,
            "udp" to true,
            "tls" to (security == "tls" || security == "reality"),
            "network" to network,
            "servername" to sni,
            "client-fingerprint" to fp,
            "skip-cert-verify" to (params["allowInsecure"] == "1" || params["insecure"] == "1")
        )
        if (flow.isNotEmpty()) map["flow"] = flow
        if (alpn.isNotEmpty()) map["alpn"] = alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (security == "reality" && pbk.isNotEmpty()) {
            val reality = linkedMapOf<String, Any>("public-key" to pbk)
            if (sid.isNotEmpty()) reality["short-id"] = sid
            map["reality-opts"] = reality
        }
        when (network) {
            "ws", "httpupgrade" -> {
                val wsOpts = linkedMapOf<String, Any>("path" to path)
                if (hostHeader.isNotEmpty()) wsOpts["headers"] = mapOf("Host" to hostHeader)
                if (network == "httpupgrade") wsOpts["v2ray-http-upgrade"] = true
                map["ws-opts"] = wsOpts
                map["network"] = "ws"
            }
            "grpc" -> {
                if (serviceName.isNotEmpty()) map["grpc-opts"] = mapOf("grpc-service-name" to serviceName)
            }
            "h2" -> {
                val h2Opts = linkedMapOf<String, Any>("path" to path)
                if (hostHeader.isNotEmpty()) h2Opts["host"] = listOf(hostHeader)
                map["h2-opts"] = h2Opts
            }
        }
        map
    }.getOrNull()

    private fun parseVmessMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val payload = uriStr.substringAfter("://").trim()
        val decoded = tryDecodeBase64(payload) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        val host = obj["add"]?.jsonPrimitive?.content ?: return null
        val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
        val uuid = obj["id"]?.jsonPrimitive?.content ?: return null
        val aid = obj["aid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val net = (obj["net"]?.jsonPrimitive?.content ?: "tcp").lowercase()
        val tls = obj["tls"]?.jsonPrimitive?.content.equals("tls", ignoreCase = true)
        val sni = obj["sni"]?.jsonPrimitive?.content?.ifBlank { host } ?: host
        val name = sanitizeName(obj["ps"]?.jsonPrimitive?.content.orEmpty()).ifBlank { "$host:$port" }

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "vmess",
            "server" to host,
            "port" to port,
            "uuid" to uuid,
            "alterId" to aid,
            "cipher" to (obj["scy"]?.jsonPrimitive?.content?.ifBlank { "auto" } ?: "auto"),
            "udp" to true,
            "tls" to tls,
            "servername" to sni,
            "network" to net
        )
        if (net == "ws") {
            val wsOpts = linkedMapOf<String, Any>("path" to (obj["path"]?.jsonPrimitive?.content ?: "/"))
            val wsHost = obj["host"]?.jsonPrimitive?.content.orEmpty()
            if (wsHost.isNotEmpty()) wsOpts["headers"] = mapOf("Host" to wsHost)
            map["ws-opts"] = wsOpts
        } else if (net == "grpc") {
            val svc = obj["path"]?.jsonPrimitive?.content.orEmpty()
            if (svc.isNotEmpty()) map["grpc-opts"] = mapOf("grpc-service-name" to svc)
        }
        map
    }.getOrNull()

    private fun parseTrojanMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }
        val sni = params["sni"] ?: params["peer"] ?: host
        val network = (params["type"] ?: "tcp").lowercase()

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "trojan",
            "server" to host,
            "port" to port,
            "password" to password,
            "sni" to sni,
            "udp" to true,
            "skip-cert-verify" to (params["allowInsecure"] == "1" || params["insecure"] == "1")
        )
        if (network == "ws") {
            map["network"] = "ws"
            val wsOpts = linkedMapOf<String, Any>("path" to (params["path"] ?: "/"))
            params["host"]?.takeIf { it.isNotEmpty() }?.let { wsOpts["headers"] = mapOf("Host" to it) }
            map["ws-opts"] = wsOpts
        } else if (network == "grpc") {
            map["network"] = "grpc"
            params["serviceName"]?.takeIf { it.isNotEmpty() }?.let {
                map["grpc-opts"] = mapOf("grpc-service-name" to it)
            }
        }
        map
    }.getOrNull()

    private fun parseShadowsocksMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val withoutScheme = uriStr.substringAfter("://")
        val hashParts = withoutScheme.split("#", limit = 2)
        val mainPart = hashParts[0]
        val fragmentName = if (hashParts.size > 1) sanitizeName(decodeUrl(hashParts[1])) else ""

        val (method, password, host, port) = if (mainPart.contains("@")) {
            val atParts = mainPart.split("@", limit = 2)
            val userInfo = tryDecodeBase64(atParts[0]) ?: decodeUrl(atParts[0])
            val creds = userInfo.split(":", limit = 2)
            val hostPort = atParts[1].substringBefore("?").split(":", limit = 2)
            Quad(creds[0], creds.getOrElse(1) { "" }, hostPort[0], hostPort.getOrElse(1) { "443" }.toInt())
        } else {
            val decoded = tryDecodeBase64(mainPart) ?: return null
            val atParts = decoded.split("@", limit = 2)
            val creds = atParts[0].split(":", limit = 2)
            val hostPort = atParts[1].split(":", limit = 2)
            Quad(creds[0], creds[1], hostPort[0], hostPort[1].toInt())
        }

        val name = fragmentName.ifBlank { "$host:$port" }
        linkedMapOf<String, Any>(
            "name" to name,
            "type" to "ss",
            "server" to host,
            "port" to port,
            "cipher" to method,
            "password" to password,
            "udp" to true
        )
    }.getOrNull()

    private fun parseShadowsocksRMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val decoded = tryDecodeBase64(uriStr.substringAfter("://")) ?: return null
        val parts = decoded.split("/?", limit = 2)
        val main = parts[0].split(":")
        if (main.size < 6) return null
        val host = main[0]
        val port = main[1].toIntOrNull() ?: 443
        val protocol = main[2]
        val cipher = main[3]
        val obfs = main[4]
        val password = tryDecodeBase64(main[5]) ?: main[5]
        val params = if (parts.size > 1) parseQuery(parts[1]) else emptyMap()
        val remarks = sanitizeName(params["remarks"]?.let { tryDecodeBase64(it) ?: it }.orEmpty()).ifBlank { "$host:$port" }

        val map = linkedMapOf<String, Any>(
            "name" to remarks,
            "type" to "ssr",
            "server" to host,
            "port" to port,
            "cipher" to cipher,
            "password" to password,
            "protocol" to protocol,
            "obfs" to obfs,
            "udp" to true
        )
        params["obfsparam"]?.let { tryDecodeBase64(it) ?: it }?.takeIf { it.isNotEmpty() }?.let { map["obfs-param"] = it }
        params["protoparam"]?.let { tryDecodeBase64(it) ?: it }?.takeIf { it.isNotEmpty() }?.let { map["protocol-param"] = it }
        map
    }.getOrNull()

    private fun parseHysteria1Map(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "hysteria",
            "server" to host,
            "port" to port,
            "auth-str" to (params["auth"] ?: uri.userInfo.orEmpty()),
            "up" to (params["upmbps"]?.let { "$it Mbps" } ?: "50 Mbps"),
            "down" to (params["downmbps"]?.let { "$it Mbps" } ?: "100 Mbps"),
            "sni" to (params["peer"] ?: params["sni"] ?: host),
            "skip-cert-verify" to (params["insecure"] == "1")
        )
        params["obfs"]?.takeIf { it.isNotEmpty() }?.let { map["obfs"] = it }
        params["alpn"]?.takeIf { it.isNotEmpty() }?.let { map["alpn"] = it.split(",") }
        map
    }.getOrNull()

    private fun parseHysteria2Map(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val normalized = uriStr.replaceFirst(Regex("^hy2://", RegexOption.IGNORE_CASE), "hysteria2://")
        val uri = URI(normalized)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "hysteria2",
            "server" to host,
            "port" to port,
            "password" to password,
            "sni" to (params["sni"] ?: host),
            "skip-cert-verify" to (params["insecure"] == "1")
        )
        params["obfs"]?.takeIf { it.isNotEmpty() }?.let {
            map["obfs"] = it
            params["obfs-password"]?.let { pwd -> map["obfs-password"] = pwd }
        }
        params["pinSHA256"]?.takeIf { it.isNotEmpty() }?.let { map["fingerprint"] = it }
        map
    }.getOrNull()

    private fun parseTuicMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val userParts = (uri.userInfo ?: return null).split(":", limit = 2)
        val uuid = userParts[0]
        val password = userParts.getOrElse(1) { "" }
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "tuic",
            "server" to host,
            "port" to port,
            "uuid" to uuid,
            "password" to password,
            "sni" to (params["sni"] ?: host),
            "congestion-controller" to (params["congestion_control"] ?: "bbr"),
            "udp-relay-mode" to (params["udp_relay_mode"] ?: "native"),
            "skip-cert-verify" to (params["allow_insecure"] == "1" || params["insecure"] == "1")
        )
        params["alpn"]?.takeIf { it.isNotEmpty() }?.let { map["alpn"] = it.split(",") }
        map
    }.getOrNull()

    private fun parseWireGuardMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val normalized = uriStr.replaceFirst(Regex("^wg://", RegexOption.IGNORE_CASE), "wireguard://")
        val uri = URI(normalized)
        val privateKey = decodeUrl(uri.userInfo)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 51820
        val params = parseQuery(uri.rawQuery)
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "$host:$port" }
        val address = (params["address"] ?: "10.0.0.2/32").split(",").first().substringBefore("/")

        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "wireguard",
            "server" to host,
            "port" to port,
            "ip" to address,
            "private-key" to privateKey,
            "public-key" to (params["publickey"] ?: params["public-key"].orEmpty()),
            "udp" to true,
            "mtu" to (params["mtu"]?.toIntOrNull() ?: 1280)
        )
        params["presharedkey"]?.takeIf { it.isNotEmpty() }?.let { map["pre-shared-key"] = it }
        params["reserved"]?.takeIf { it.isNotEmpty() }?.let { res ->
            val ints = res.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (ints.size == 3) map["reserved"] = ints
        }
        map
    }.getOrNull()

    private fun parseSocksMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 1080
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "SOCKS5 $host:$port" }
        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "socks5",
            "server" to host,
            "port" to port,
            "udp" to true
        )
        uri.userInfo?.split(":", limit = 2)?.let { creds ->
            if (creds[0].isNotEmpty()) map["username"] = creds[0]
            if (creds.size > 1 && creds[1].isNotEmpty()) map["password"] = creds[1]
        }
        map
    }.getOrNull()

    private fun parseHttpProxyMap(uriStr: String): LinkedHashMap<String, Any>? = runCatching {
        val uri = URI(uriStr)
        val host = uri.host ?: return null
        val isTls = uri.scheme.equals("https", ignoreCase = true)
        val port = if (uri.port > 0) uri.port else if (isTls) 443 else 8080
        val name = sanitizeName(decodeUrl(uri.rawFragment)).ifBlank { "HTTP $host:$port" }
        val map = linkedMapOf<String, Any>(
            "name" to name,
            "type" to "http",
            "server" to host,
            "port" to port,
            "tls" to isTls
        )
        uri.userInfo?.split(":", limit = 2)?.let { creds ->
            if (creds[0].isNotEmpty()) map["username"] = creds[0]
            if (creds.size > 1 && creds[1].isNotEmpty()) map["password"] = creds[1]
        }
        map
    }.getOrNull()

    fun dumpFlowMap(map: Map<String, Any>): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.FLOW
            isAllowUnicode = true
            width = 4096
        }
        return Yaml(options).dump(map).trim()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.isEmpty() || parts[0].isBlank()) null
            else decodeUrl(parts[0]) to decodeUrl(parts.getOrElse(1) { "" })
        }.toMap()
    }

    private fun decodeUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    private fun tryDecodeBase64(input: String): String? {
        val clean = input.replace("\n", "").replace("\r", "").trim()
        if (clean.length < 8) return null
        return runCatching {
            val bytes = Base64.decode(clean, Base64.DEFAULT or Base64.URL_SAFE)
            val text = String(bytes, Charsets.UTF_8)
            if (text.contains("://") || text.contains("{") || text.contains("proxies:") || text.contains(":")) text else null
        }.getOrNull()
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Int)
}
