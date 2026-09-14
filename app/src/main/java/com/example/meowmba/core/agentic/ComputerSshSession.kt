package com.example.meowmba.core.agentic

import com.jcraft.jsch.*
import java.security.MessageDigest
import java.util.Base64

/** Verifies the key from the actual SSH handshake, before sending credentials. */
object ComputerSshSession {
    fun fingerprint(key: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

    fun accepts(saved: String?, key: ByteArray, firstConnect: Boolean): Boolean {
        if (saved.isNullOrBlank()) return firstConnect
        val expected = saved.trim().removeSuffix("=")
        val sha = fingerprint(key)
        val md5 = MessageDigest.getInstance("MD5").digest(key).joinToString(":") { "%02x".format(it) }
        return expected == sha || expected.removePrefix("MD5:").equals(md5, ignoreCase = true)
    }

    fun connect(profile: ConnectionProfile, secret: String, save: (ConnectionProfile) -> Unit): Session {
        val jsch = JSch()
        var observed: ByteArray? = null
        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                if (!accepts(profile.hostKeyFingerprint, key, profile.pinHostKeyOnFirstConnect)) return HostKeyRepository.CHANGED
                observed = key.copyOf()
                return HostKeyRepository.OK
            }
            override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
            override fun remove(host: String, type: String?) = Unit
            override fun remove(host: String, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "Meow pinned connection profile"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        val privateKey = secret.trimStart().startsWith("-----BEGIN ") && secret.contains("PRIVATE KEY-----")
        if (privateKey) jsch.addIdentity(profile.id, secret.toByteArray(Charsets.UTF_8), null, null)
        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (!privateKey) session.setPassword(secret)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", if (privateKey) "publickey" else "password,keyboard-interactive")
        session.timeout = 20_000
        try {
            session.connect(20_000)
            val key = checkNotNull(observed) { "SSH server key was not verified" }
            if (profile.hostKeyFingerprint.isNullOrBlank()) save(profile.copy(hostKeyFingerprint = fingerprint(key)))
            return session
        } catch (e: Exception) {
            session.disconnect()
            throw e
        }
    }
}
