package io.neuralheads.kmpworker.transfer

/**
 * Verifies file integrity using SHA-256 checksums.
 *
 * Implemented per-platform using native crypto APIs.
 */
expect object ChecksumVerifier {
    /**
     * Computes SHA-256 hash of the file at [filePath].
     * @return Hex-encoded hash string, or null if file doesn't exist.
     */
    fun sha256(filePath: String): String?

    /**
     * Verifies that the file at [filePath] matches [expected] checksum.
     * @param expected Format: "sha256:hexstring"
     * @return True if checksum matches.
     */
    fun verify(filePath: String, expected: String): Boolean
}
