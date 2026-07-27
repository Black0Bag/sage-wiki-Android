import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Signing keystore configuration ───────────────────────────────────────────
// Reads sensitive values from (in priority order):
//   1. environment variables  SAGEWIKI_KEYSTORE_PASSWORD / SAGEWIKI_KEY_PASSWORD
//   2. root project local.properties  (sagewiki.keystore.password / sagewiki.key.password)
//   3. fallback hard-coded password   zsm1216300859
val keystoreProperties = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        load(FileInputStream(localProps))
    }
}

fun readSigningValue(envKey: String, propKey: String, fallback: String): String =
    System.getenv(envKey)
        ?: keystoreProperties.getProperty(propKey)
        ?: fallback

val sagewikiKeystorePassword = readSigningValue(
    "SAGEWIKI_KEYSTORE_PASSWORD",
    "sagewiki.keystore.password",
    "zsm1216300859"
)
val sagewikiKeyPassword = readSigningValue(
    "SAGEWIKI_KEY_PASSWORD",
    "sagewiki.key.password",
    "zsm1216300859"
)

android {
    namespace = "com.sagewiki.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sagewiki.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 30000
        versionName = "3.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/sagewiki-release.jks")
            storePassword = sagewikiKeystorePassword
            keyAlias = "sagewiki"
            keyPassword = sagewikiKeyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用正式 Release 签名（CI 通过 Secrets 注入 keystore + 环境变量传密码）
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil2:0.27.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
