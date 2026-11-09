pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Alarme"

include(":app")
include(":core:model")
include(":core:common")
include(":core:data")
include(":core:designsystem")
include(":core:alarm-engine")
include(":core:audio")
include(":core:sensors")
include(":feature:alarmlist")
include(":feature:editor")
include(":feature:missions")
include(":feature:ring")
include(":feature:ringtones")
include(":feature:settings")
include(":feature:backup")
include(":feature:widget")
