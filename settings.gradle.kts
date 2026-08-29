pluginManagement {
    // CI（GitHub Actions 自动注入 CI=true）在海外，阿里云镜像同步滞后且访问慢，
    // 直接走官方源；本地开发保持阿里云镜像优先以加速下载。
    val onCi = System.getenv("CI") == "true"
    repositories {
        if (onCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val onCi = System.getenv("CI") == "true"
    repositories {
        if (onCi) {
            google()
            mavenCentral()
        } else {
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
}

rootProject.name = "TaiXu"
include(":app")
include(":baselineprofile")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:security")
include(":runtime")
include(":project-template")
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
include(":feature:custom_iteration")
include(":feature:navigation")
