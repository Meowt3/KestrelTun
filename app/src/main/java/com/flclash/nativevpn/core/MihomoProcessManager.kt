package com.flclash.nativevpn.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MihomoProcessManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mihomoProcess: Process? = null

    private val logBuffer = ArrayDeque<String>(64)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val workDir: File
        get() = File(context.filesDir, "mihomo").apply { mkdirs() }

    val configFile: File
        get() = File(workDir, "config.yaml")

    private val pidFile: File
        get() = File(workDir, "mihomo.pid")

    fun findMihomoBinary(): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidates = listOf(
            File(nativeDir, "libmihomo.so"),
            File(nativeDir, "libclash.so"),
            File(workDir, "mihomo")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 1024L }
    }

    fun findTunBinary(): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidates = listOf(
            File(nativeDir, "libhev-socks5-tunnel.so"),
            File(nativeDir, "libtun2socks.so")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 1024L }
    }

    fun writeConfig(yamlContent: String): File {
        configFile.writeText(yamlContent, Charsets.UTF_8)
        return configFile
    }

    fun startMihomo(yamlContent: String): Boolean {
        stopMihomo()
        val binary = findMihomoBinary()
        if (binary == null) {
            appendLog("[ERROR] Не найден libmihomo.so в nativeLibraryDir")
            return false
        }
        binary.setExecutable(true, false)
        writeConfig(yamlContent)

        return runCatching {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "-d", workDir.absolutePath,
                "-f", configFile.absolutePath
            )
            pb.directory(workDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            mihomoProcess = proc
            saveProcessPid(proc)
            appendLog("[CORE] Запущен ${binary.name} (порт ${MihomoConfigGenerator.MIXED_PORT})")
            streamProcessLogs("mihomo", proc)
            true
        }.onFailure { err ->
            appendLog("[ERROR] Ошибка запуска mihomo: ${err.message}")
        }.getOrDefault(false)
    }

    fun stopAll() {
        stopMihomo()
    }

    private fun stopMihomo() {
        mihomoProcess?.let { proc ->
            runCatching {
                proc.destroy()
                if (!proc.waitFor(400, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                    proc.waitFor(200, TimeUnit.MILLISECONDS)
                }
            }
            appendLog("[CORE] Ядро mihomo остановлено")
        }
        mihomoProcess = null
        killSavedOrphanPid()
    }

    private fun saveProcessPid(proc: Process) {
        val pid = extractPid(proc)
        if (pid != null && pid > 0) {
            runCatching { pidFile.writeText(pid.toString(), Charsets.UTF_8) }
        }
    }

    private fun killSavedOrphanPid() {
        if (!pidFile.exists()) return
        val oldPid = runCatching { pidFile.readText().trim().toIntOrNull() }.getOrNull()
        runCatching { pidFile.delete() }
        if (oldPid != null && oldPid > 0 && oldPid != android.os.Process.myPid()) {
            runCatching {
                android.os.Process.sendSignal(oldPid, 9) // SIGKILL orphan mihomo of same UID
                Thread.sleep(120)
            }
        }
    }

    private fun extractPid(proc: Process): Int? {
        return runCatching {
            val m = Regex("""pid=(\d+)""").find(proc.toString())
            m?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.getOrNull()
    }

    private fun streamProcessLogs(tag: String, proc: Process) {
        scope.launch {
            runCatching {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        appendLog("[$tag] $line")
                    }
                }
            }
        }
    }

    fun appendLog(line: String) {
        synchronized(logBuffer) {
            if (logBuffer.size >= 60) logBuffer.removeFirst()
            logBuffer.addLast(line)
            if (_logs.subscriptionCount.value > 0) {
                _logs.value = logBuffer.toList()
            }
        }
    }

    fun syncLogsToFlow() {
        synchronized(logBuffer) {
            _logs.value = logBuffer.toList()
        }
    }

    fun getLogsSnapshot(): List<String> = synchronized(logBuffer) {
        logBuffer.toList()
    }

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _logs.value = emptyList()
        }
    }
}
