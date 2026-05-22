import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.sqldelight)           apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka)                apply false
    alias(libs.plugins.vanniktech.publish)   apply false
}

// Dokka only when explicitly running :generateDocs — not pulled into publish tasks.
tasks.register("generateDocs") {
    group       = "documentation"
    description = "Generates Dokka HTML API documentation for all modules"
    dependsOn(subprojects.mapNotNull { sub ->
        sub.tasks.findByName("dokkaHtml")?.let { "${sub.path}:dokkaHtml" }
    })
}

// ── Secrets ────────────────────────────────────────────────────────────────────

val localProps = java.util.Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use(props::load)
}

fun getSecret(key: String): String? =
    System.getenv(key) ?: localProps.getProperty(key)

val globalSigningKey  = getSecret("SIGNING_KEY")
val globalSigningPass = getSecret("SIGNING_PASSWORD") ?: ""
val globalCentralUser = getSecret("MAVEN_CENTRAL_USERNAME")
val globalCentralPass = getSecret("MAVEN_CENTRAL_PASSWORD")

// ── Group/version + credentials on all projects BEFORE plugin evaluation ───────

val versionName = properties["VERSION_NAME"]?.toString() ?: "0.1.0-alpha01"
val groupId     = properties["GROUP"]?.toString()        ?: "io.neuralheads"

allprojects {
    group   = groupId
    version = versionName

    if (globalSigningKey  != null) extensions.extraProperties["signingInMemoryKey"]       = globalSigningKey
    extensions.extraProperties["signingInMemoryKeyPassword"]                                = globalSigningPass
    if (globalCentralUser != null) extensions.extraProperties["mavenCentralUsername"]      = globalCentralUser
    if (globalCentralPass != null) extensions.extraProperties["mavenCentralPassword"]      = globalCentralPass
}

// ── Maven publishing — POM metadata + publish target ─────────────────────────
//
// coordinates() is NOT called here — it lives in gradle/publish.gradle.kts which
// runs as part of each module's own evaluation (after plugin apply, before any
// configure() reads/finalizes the groupId property).
//
// configure(KotlinMultiplatform(...)) / configure(AndroidSingleVariantLibrary(...))
// are also NOT called here — vanniktech 0.30 auto-detects the project type.
// The javaDocReleaseGeneration crash is suppressed per-module instead.

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()

            pom {
                val artifactId =
                    if (project.name == "umbrella") "kmpworker"
                    else "kmpworker-${project.name}"

                name.set("KMPWorker — $artifactId")
                description.set(
                    "Reliability-first Kotlin Multiplatform background task processing " +
                    "library. Wraps WorkManager (Android) and BGTaskScheduler (iOS) " +
                    "behind a single, tested, coroutine-native API."
                )
                inceptionYear.set("2024")
                url.set("https://github.com/neuralheads/kmpworker")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("neuralheads")
                        name.set("NeuralHeads")
                        url.set("https://github.com/neuralheads")
                    }
                }

                scm {
                    url.set("https://github.com/neuralheads/kmpworker")
                    connection.set("scm:git:git://github.com/neuralheads/kmpworker.git")
                    developerConnection.set("scm:git:ssh://git@github.com/neuralheads/kmpworker.git")
                }
            }
        }

        // Suppress Dokka/ASM9 crash with sealed classes (PermittedSubclasses requires ASM9).
        // The javadoc jar is generated on CI (macOS) where Dokka works correctly.
        project.afterEvaluate {
            project.tasks.matching { it.name == "javaDocReleaseGeneration" }
                .configureEach { enabled = false }
        }
    }
}
