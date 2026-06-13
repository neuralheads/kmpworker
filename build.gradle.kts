import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.sqldelight)           apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish)   apply false
}

// Dokka only when explicitly running :generateDocs â€” not pulled into publish tasks.
tasks.register("generateDocs") {
    group       = "documentation"
    description = "Generates Dokka HTML API documentation for all modules"
    dependsOn("dokkaHtmlMultiModule")
    doLast {
        copy {
            from(layout.buildDirectory.dir("dokka/htmlMultiModule"))
            into(layout.buildDirectory.dir("dokka/html"))
        }
    }
}

// ————————————————————————————————————————————————————————————————————————————————————————————

val versionName = properties["VERSION_NAME"]?.toString() ?: "0.1.0-alpha01"
val groupId     = properties["GROUP"]?.toString()        ?: "com.neuralheads"

allprojects {
    group   = groupId
    version = versionName
}

// ————————————————————————————————————————————————————————————————————————————————————————————
//
// coordinates() is NOT called here — it lives in gradle/publish.gradle.kts which
// runs as part of each module's own evaluation (after plugin apply, before any
// configure() reads/finalizes the groupId property).
//
// configure(KotlinMultiplatform(...)) / configure(AndroidSingleVariantLibrary(...))
// are also NOT called here â€” vanniktech 0.30 auto-detects the project type.
// The javaDocReleaseGeneration crash is suppressed per-module instead.

subprojects {
    apply(plugin = "org.jetbrains.dokka")
    plugins.withId("com.vanniktech.maven.publish") {
        configure<MavenPublishBaseExtension> {
            // publishToMavenCentral() and signAllPublications() are configured
            // via gradle.properties (SONATYPE_HOST, SONATYPE_AUTOMATIC_RELEASE,
            // RELEASE_SIGNING_ENABLED) — vanniktech 0.33.0 reads them during apply().
            pom {
                val artifactId =
                    if (project.name == "umbrella") "kmpworker"
                    else "kmpworker-${project.name}"

                name.set("KMPWorker â€” $artifactId")
                description.set(
                    "Reliability-first Kotlin Multiplatform background task processing " +
                    "library. Provides a single coroutine-native API for scheduling one-time, " +
                    "periodic, and chained tasks across Android and iOS, with SQLDelight " +
                    "persistence, offline queue, retry policies, and Flow-based state monitoring."
                )
                inceptionYear.set("2026")
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

            // Fix vanniktech staging directory URI on Windows.
            // Java's File.absolutePath uses backslashes; vanniktech constructs the staging
            // repo URI as "file://" + absolutePath which produces "file://C:\..." — invalid
            // on Windows (needs "file:///C:/..."). Patch it here at execution time so
            // Gradle's MavenResolver can resolve the URI to a File.
            project.tasks.withType(PublishToMavenRepository::class.java).configureEach {
                val pub = this  // capture PublishToMavenRepository before doFirst changes receiver
                doFirst {
                    val current = pub.repository.url
                    val str = current.toString()
                    val isWindows = System.getProperty("os.name").lowercase().contains("win")
                    if (isWindows && str.startsWith("file:") && !str.startsWith("file:///")) {
                        pub.repository.setUrl(
                            URI("file:///" + str.removePrefix("file://").replace('\\', '/'))
                        )
                    }
                }
            }

            // Add a pre-correct file:/// local Maven repository so we can publish
            // signed artifacts without hitting the Windows staging URI bug in vanniktech.
            // All publications already carry .asc artifacts (from signAllPublications());
            // publishing to ANY Maven repo includes them automatically.
            extensions.findByType(PublishingExtension::class.java)?.repositories {
                maven {
                    name = "LocalRelease"
                    url = URI("file:///C:/kmpworker-release/")
                }
            }
        }
    }
}
