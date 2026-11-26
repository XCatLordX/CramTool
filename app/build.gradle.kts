import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cramtool.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cramtool.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.8.0"
    }

    // 正式簽章：優先讀環境變數（GitHub Actions），否則讀專案根目錄的 keystore.properties（已加入 .gitignore）
    // 金鑰與密碼絕不可提交到 Git
    val keyProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keyProps.getProperty(prop)
    val releaseStoreFile = signingValue("CRAMTOOL_KEYSTORE_FILE", "storeFile")
    val hasReleaseKey = releaseStoreFile != null && file(releaseStoreFile).exists()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("CRAMTOOL_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("CRAMTOOL_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("CRAMTOOL_KEY_PASSWORD", "keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 先不開啟 R8 壓縮：無法在實機驗證壓縮後是否正常，之後另外處理
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("CramTool: 找不到正式簽章設定，release APK 將不會簽章（無法安裝）。")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    // UI 流程測試（Robolectric 在 JVM 上跑真正的 Compose 畫面）
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
    }
}
