/**
 * Umbrella artifact for KMPWorker.
 *
 * One dependency gives consumers everything:
 * ```kotlin
 * implementation("io.neuralheads:kmpworker:0.1.0-alpha02")
 * // Testing:
 * testImplementation("com.neuralheads:kmpworker-testing:0.1.0-alpha02")
 * ```
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

group   = "com.neuralheads"
version = rootProject.properties["VERSION_NAME"]?.toString() ?: "0.1.0-alpha01"

apply(from = rootProject.file("gradle/publish.gradle.kts"))


kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":scheduler"))
            api(project(":persistence"))
            api(project(":queue"))
        }
        commonTest.dependencies {
            api(project(":testing"))
        }
        androidMain.dependencies {
            api(project(":android"))
        }

        // applyDefaultHierarchyTemplate=false → must wire iosMain explicitly
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(project(":ios"))
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

android {
    namespace  = "io.neuralheads.kmpworker"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
