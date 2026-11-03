plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "club.daishenghui.minguo"
    compileSdk = 35

    defaultConfig {
        applicationId = "club.daishenghui.minguo"
        minSdk = 24 // Android 7.0
        targetSdk = 35
        versionCode = 14
        versionName = "3.9.0"
    }

    // 签名：默认用仓库里的 keystore/minguo.jks（每次编译签名一致，可直接覆盖升级）；
    // 在 GitHub Secrets 里配置了自己的密钥时优先用自己的（见 README）
    fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("minguo") {
            storeFile = file(env("SIGNING_STORE_FILE") ?: "../keystore/minguo.jks")
            storePassword = env("SIGNING_STORE_PASSWORD") ?: "minguo123"
            keyAlias = env("SIGNING_KEY_ALIAS") ?: "minguo"
            keyPassword = env("SIGNING_KEY_PASSWORD") ?: "minguo123"
        }
    }

    buildTypes {
        // 正式版
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("minguo")
        }
        // 调试版：独立包名，可与正式版同时安装，带完整日志
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("minguo")
        }
    }

    // 按 CPU 架构分包，同时保留一个通用包
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        // Android 7.x 没有 java.time，用脱糖库补上
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
