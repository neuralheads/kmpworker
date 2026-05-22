/**
 * Credential-injection convention for all publishable KMPWorker modules.
 * Applied via: apply(from = rootProject.file("gradle/publish.gradle.kts"))
 *
 * Reads SIGNING_KEY, SIGNING_PASSWORD, MAVEN_CENTRAL_USERNAME, MAVEN_CENTRAL_PASSWORD
 * from env → local.properties → gradle.properties and injects them as project
 * extra properties so vanniktech picks them up at configuration time.
 *
 * NOTE: coordinates() is set in each module's own build.gradle.kts via
 * `mavenPublishing { coordinates(...) }` — the vanniktech type is NOT available
 * in apply(from=) script plugin classpath.
 */

fun secret(key: String): String? {
    System.getenv(key)?.let { return it }
    try {
        val lp = java.util.Properties()
        lp.load(rootProject.file("local.properties").inputStream())
        lp.getProperty(key)?.let { return it }
    } catch (_: Exception) {}
    return providers.gradleProperty(key).orNull
}

val signingKey  = secret("SIGNING_KEY")
val signingPass = secret("SIGNING_PASSWORD") ?: ""
val centralUser = secret("MAVEN_CENTRAL_USERNAME")
val centralPass = secret("MAVEN_CENTRAL_PASSWORD")

if (signingKey  != null) project.extensions.extraProperties["signingInMemoryKey"]       = signingKey
project.extensions.extraProperties["signingInMemoryKeyPassword"]                          = signingPass
if (centralUser != null) project.extensions.extraProperties["mavenCentralUsername"]      = centralUser
if (centralPass != null) project.extensions.extraProperties["mavenCentralPassword"]      = centralPass
