rootProject.name = "kmpworker"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":core",
    ":scheduler",
    ":persistence",
    ":android",
    ":ios",
    ":queue",
    ":testing",
    ":umbrella",
    ":sample"
)
