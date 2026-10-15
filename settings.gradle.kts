pluginManagement { repositories { gradlePluginPortal(); mavenCentral() }
    plugins {
        kotlin("jvm") version "2.4.21"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "schedule-lab"
include("app")
