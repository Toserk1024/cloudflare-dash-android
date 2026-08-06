import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ===== 动态版本号（构建时生成）=====
val versionPropsFile = File(rootProject.projectDir, "version.properties")

fun currentBuildSeq(): Int = try {
    Properties().apply { load(versionPropsFile.inputStream()) }
        .getProperty("buildSeq")?.toInt() ?: 0
} catch (e: Exception) {
    0
}

val buildDate = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
val buildSeq = currentBuildSeq() + 1

// 每次构建后序号 +1 写回 version.properties（CI 用 actions/cache 持久化跨构建保留）
tasks.register("bumpVersion") {
    doLast {
        Properties().apply {
            setProperty("buildSeq", (currentBuildSeq() + 1).toString())
            store(versionPropsFile.outputStream(), "auto-increment build sequence")
        }
    }
}
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn("bumpVersion")
}

android {
    namespace = "io.github.toserk1024.cfdash"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.toserk1024.cfdash"
        minSdk = 24
        targetSdk = 35
        // versionCode：构建时间(unix秒)前9位截取，规避 32 位 Int 极限（如 1785916376 → 178591637）
        versionCode = ((System.currentTimeMillis() / 1000) / 10).toInt()
        // versionName：v日期_自增序号（如 v2026.08.08_123）
        versionName = "v${buildDate}_$buildSeq"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 开启 R8 压缩（节约打包资源）
            isMinifyEnabled = true
            // 复用默认 debug 签名，产物可直接安装（非正式发布）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
