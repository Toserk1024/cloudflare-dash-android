// 仓库顺序约定：**官方仓库优先，国内镜像仅作兜底**。
// 原因：Gradle 在某个仓库返回 5xx/502 时会「禁用该仓库」，一旦镜像排在前面且不可用，
// 会导致整条 classpath / 依赖解析失败（CI 上曾因 maven.aliyun.com 502 直接构建失败）。
// CI（GitHub Actions）访问官方源是最快最稳的路径；镜像放在最后只在国内本地构建时兜底。
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

        // ===== 兜底镜像（官方不可用时才会命中）=====
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/gradle-plugin/")
        maven("https://repo.huaweicloud.com/repository/maven/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MPAndroidChart 4.x 仅发布在 JitPack（官方仓库拿不到），排在官方源之后
        maven("https://jitpack.io")

        // ===== 兜底镜像（官方不可用时才会命中）=====
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven/")
    }
}

rootProject.name = "CloudflareDash3rd"
include(":app")