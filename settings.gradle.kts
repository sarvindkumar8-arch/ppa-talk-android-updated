pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Use a mirror first to reduce Maven Central rate-limit failures in CI.
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2") }
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "PPATalk"
include(":app")
