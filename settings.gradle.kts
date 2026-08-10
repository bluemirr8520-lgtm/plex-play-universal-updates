pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule(
                        "org.jetbrains.kotlin:compose-compiler-gradle-plugin:" +
                            requested.version,
                    )
            }
        }
    }
    repositories {
        maven { url = uri(rootDir.resolve(".gradle-build/local-maven")) }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(rootDir.resolve(".gradle-build/local-maven")) }
        google()
        mavenCentral()
    }
}

rootProject.name = "PlexPlayUniversal"
include(":app")
