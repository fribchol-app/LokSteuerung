plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fribchol.loksteuerung"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fribchol.loksteuerung"
        // Android 10 (API 29) ist nötig, damit sich die App per Code mit dem Lok-WLAN verbinden kann
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Der QR-Scanner (Play Services) bringt eine sehr alte Fragment-Version mit.
    // Diese neuere Version ersetzt sie, sonst bricht der Release-Build mit einem Lint-Fehler ab.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // WebSocket-Verbindung zum ESP32
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR-Scanner von Google Play Services (braucht keine Kamera-Berechtigung)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}