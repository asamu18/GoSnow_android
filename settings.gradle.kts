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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // 🔹 Mapbox Maven 仓库
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // 不要把 token 写死在这里，下面会从 gradle.properties / 环境变量里取
                username = "mapbox"
                password = (extra["MAPBOX_DOWNLOADS_TOKEN"] as String?
                    ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
                    ?: "")
            }
        }
    }
}

rootProject.name = "gosnow"
include(":app")
