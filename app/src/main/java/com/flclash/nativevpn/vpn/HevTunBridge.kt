package com.flclash.nativevpn.vpn

import hev.htproxy.TProxyService
import java.io.File

object HevTunBridge {

    fun isAvailable(): Boolean = runCatching {
        TProxyService.TProxyIsRunning()
        true
    }.getOrDefault(false)

    fun start(workDir: File, tunFd: Int, socksPort: Int): Result<Unit> = runCatching {
        val configFile = File(workDir, "hev-tun.yaml")
        val yaml = """
            tunnel:
              mtu: 1500
              ipv4: 10.0.0.2
              ipv6: 'fdfe:dcba:9876::1'
            socks5:
              port: $socksPort
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 198.18.0.0
              netmask: 255.254.0.0
              cache-size: 4096
            misc:
              task-stack-size: 32768
              connect-timeout: 5000
              read-write-timeout: 300000
              log-level: error
        """.trimIndent()
        configFile.writeText(yaml, Charsets.UTF_8)

        val ok = TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
        if (!ok) {
            error("TProxyStartService returned false")
        }
    }

    fun stop() {
        runCatching {
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        }
    }
}
