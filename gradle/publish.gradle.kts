/**
 * Convention script applied to all publishable KMPWorker modules.
 * Applied via: apply(from = rootProject.file("gradle/publish.gradle.kts"))
 *
 * Vanniktech 0.33.0 reads SONATYPE_HOST / SONATYPE_AUTOMATIC_RELEASE /
 * RELEASE_SIGNING_ENABLED from gradle.properties during apply() to finalise
 * the publishing type. Those properties must NOT be touched here.
 *
 * However, the GPG signing key (signingInMemoryKey) is stored in
 * ~/.gradle/gradle.properties as a true multi-line value that Java's
 * Properties.load() silently truncates to just the header line. We read
 * the full key with a custom line-scanner and inject it as an extra property
 * so Bouncy Castle (used by vanniktech) receives a complete ASCII-armoured key.
 *
 * Credentials (mavenCentralUsername/Password) are already available as
 * single-line Gradle project properties from ~/.gradle/gradle.properties and
 * do NOT need to be injected here.
 */

/**
 * Reads the full PGP private key block from a properties file that stores
 * the value as genuine multi-line text (not backslash-escaped).
 *
 * Returns a single String with lines joined by the literal two-char
 * sequence "\n" so vanniktech's InMemoryPgpSignatoryProvider (which calls
 * key.replace("\\n", "\n") before parsing) receives the correct armour.
 */
fun readMultilineSigningKey(): String? {
    val gradleProps = File(System.getProperty("user.home") + "/.gradle/gradle.properties")
    if (!gradleProps.exists()) return null

    val lines = gradleProps.readLines()
    var collecting = false
    val keyLines = mutableListOf<String>()

    for (line in lines) {
        when {
            line.startsWith("signingInMemoryKey=") -> {
                val value = line.removePrefix("signingInMemoryKey=")
                keyLines.add(value)
                collecting = !value.trimEnd().endsWith("-----END PGP PRIVATE KEY BLOCK-----")
            }
            collecting -> {
                keyLines.add(line)
                if (line.trimEnd().endsWith("-----END PGP PRIVATE KEY BLOCK-----")) {
                    collecting = false
                }
            }
        }
    }

    return keyLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

val signingKey = readMultilineSigningKey()
if (signingKey != null) {
    // extraProperties injection is safe for credentials in vanniktech 0.33.0;
    // only SONATYPE_HOST / SONATYPE_AUTOMATIC_RELEASE / RELEASE_SIGNING_ENABLED
    // are finalised during apply() and must come from gradle.properties.
    project.extensions.extraProperties["signingInMemoryKey"] = signingKey
}
