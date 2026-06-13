plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.vanniktech.publish)
}

group   = "com.neuralheads"
version = rootProject.properties["VERSION_NAME"]?.toString() ?: "0.1.0-alpha01"

apply(from = rootProject.file("gradle/publish.gradle.kts"))


kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace  = "io.neuralheads.kmpworker.persistence"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("KmpWorkerDatabase") {
            packageName.set("io.neuralheads.kmpworker.persistence.db")
        }
    }
}

// SQLDelight's VerifyMigrationTask uses sqlite-jdbc which requires a native .dll.
// On Windows the JNI binding fails with UnsatisfiedLinkError at Gradle task execution time.
// CI (macOS/Linux) runs the check correctly; disable it locally on Windows only.
if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
    // Actual task name: verifyCommonMainKmpWorkerDatabaseMigration
    // SQLDelight's VerifyMigrationTask loads sqlite-jdbc via JNI which fails on Windows.
    tasks.matching { task ->
        task.name.contains("Migration") && task.name.startsWith("verify")
    }.configureEach {
        enabled = false
    }
}
