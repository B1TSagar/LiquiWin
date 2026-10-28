pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LiquiWin"

include(
    ":app",
    ":core:design",
    ":core:data",
    ":core:tiles",
    ":core:testing",
    ":feature:start",
    ":feature:applist",
    ":feature:search",
    ":feature:notifications",
    ":tools:notifier",
)
