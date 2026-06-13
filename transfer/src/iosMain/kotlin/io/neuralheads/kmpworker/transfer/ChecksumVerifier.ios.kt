@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.neuralheads.kmpworker.transfer

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

actual object ChecksumVerifier {

    actual fun sha256(filePath: String): String? {
        val data = NSData.dataWithContentsOfFile(filePath) ?: return null
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        data.bytes?.let { bytes ->
            digest.usePinned { pinned ->
                CC_SHA256(bytes, data.length.convert(), pinned.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toString(16).padStart(2, '0') }
    }

    actual fun verify(filePath: String, expected: String): Boolean {
        val hash = sha256(filePath) ?: return false
        val expectedHash = expected.removePrefix("sha256:")
        return hash.equals(expectedHash, ignoreCase = true)
    }
}
