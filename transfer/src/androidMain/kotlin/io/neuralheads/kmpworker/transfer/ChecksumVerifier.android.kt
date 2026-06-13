package io.neuralheads.kmpworker.transfer

import java.io.File
import java.security.MessageDigest

actual object ChecksumVerifier {

    actual fun sha256(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    actual fun verify(filePath: String, expected: String): Boolean {
        val hash = sha256(filePath) ?: return false
        val expectedHash = expected.removePrefix("sha256:")
        return hash.equals(expectedHash, ignoreCase = true)
    }
}
