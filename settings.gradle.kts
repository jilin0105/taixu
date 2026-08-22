pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = false
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = false
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "TaiXu"
include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:security")
include(":runtime")
include(":tools")
include(":harness")
include(":feature:theme")
include(":feature:components")
include(":feature:home")
include(":feature:chat")
include(":feature:terminal")
include(":feature:workspace")
include(":feature:settings")
include(":feature:developer")
include(":feature:navigation")
