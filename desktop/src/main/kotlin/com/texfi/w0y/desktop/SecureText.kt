package com.texfi.w0y.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Защита сессии (cookie YouTube) на диске. Открытым текстом она больше не лежит:
 *  • Windows — DPAPI (привязка к учётной записи Windows), префикс «dpapi:»;
 *  • Linux — AES-GCM, ключ в связке ключей системы (Secret Service через `secret-tool`), а если её нет —
 *    в файле `~/.config/w0y/session.key` с правами только для владельца; префикс «enc1:».
 * Старая запись без префикса читается как есть и при ближайшем сохранении шифруется.
 */
object SecureText {
    private const val DPAPI = "dpapi:"
    private const val AES = "enc1:"
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun protect(plain: String): String {
        if (plain.isEmpty() || plain.startsWith(DPAPI) || plain.startsWith(AES)) return plain
        return runCatching {
            if (Platform.isWindows) DPAPI + b64.encodeToString(Crypt32Util.cryptProtectData(plain.toByteArray()))
            else AES + aesEncrypt(plain.toByteArray())
        }.getOrDefault(plain)
    }

    /** Расшифровать; не поддающееся расшифровке (чужая учётка, испорченный файл) — null, то есть «вход не выполнен». */
    fun unprotect(stored: String): String? =
        when {
            stored.startsWith(DPAPI) -> runCatching { String(Crypt32Util.cryptUnprotectData(unb64.decode(stored.removePrefix(DPAPI)))) }.getOrNull()
            stored.startsWith(AES) -> runCatching { String(aesDecrypt(stored.removePrefix(AES))) }.getOrNull()
            else -> stored
        }

    private fun aesEncrypt(data: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, iv)) }
        return b64.encodeToString(iv + cipher.doFinal(data))
    }

    private fun aesDecrypt(text: String): ByteArray {
        val raw = unb64.decode(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        }
        return cipher.doFinal(raw, 12, raw.size - 12)
    }

    @Volatile
    private var cachedKey: ByteArray? = null

    @Synchronized
    private fun key(): ByteArray {
        cachedKey?.let { return it }
        val fromRing = if (System.getProperty("w0y.nokeyring") == null) keyringKey() else null
        val key = fromRing ?: fileKey()
        cachedKey = key
        return key
    }

    /** Ключ из Secret Service: читает или заводит. Любая неудача — null, дальше работает файл. */
    private fun keyringKey(): ByteArray? {
        if (!Platform.isLinux || !Platform.has("secret-tool")) return null
        return runCatching {
            fun run(vararg args: String, input: String? = null): String? {
                val p = ProcessBuilder(listOf("secret-tool") + args).redirectErrorStream(false).start()
                if (input != null) p.outputStream.use { it.write(input.toByteArray()) } else p.outputStream.close()
                // Связка ключей может ждать разблокировки: дольше пяти секунд не ждём, берём файл.
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    return null
                }
                val out = p.inputStream.bufferedReader().readText().trim()
                return if (p.exitValue() == 0) out else null
            }
            val existing = run("lookup", "service", "w0y", "kind", "session-key")
            if (!existing.isNullOrBlank()) return unb64.decode(existing)
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            if (run("store", "--label=w0y session key", "service", "w0y", "kind", "session-key", input = b64.encodeToString(fresh)) != null) fresh else null
        }.getOrNull()
    }

    private fun fileKey(): ByteArray {
        val file = (System.getProperty("w0y.config")?.let { File(it) } ?: Platform.configDir().toFile()).resolve("session.key")
        if (file.exists()) runCatching { return unb64.decode(file.readText().trim()) }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.parentFile.mkdirs()
        file.writeText(b64.encodeToString(fresh))
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        return fresh
    }
}
