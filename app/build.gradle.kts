plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hamza.prozessor"
    // Android Studio Ladybug Feature Drop (2024.2.2) veya üstü gerekli
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hamza.prozessor"
        minSdk = 26       // Android 8.0 minimum
        targetSdk = 35    // Android 15+
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Shizuku binder sınıf adları korunmalı
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
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
        aidl = true  // UserService AIDL için
    }

    // Shizuku meta-inf çakışması
    packaging {
        resources.excludes += "META-INF/AL2.0"
        resources.excludes += "META-INF/LGPL2.1"
    }
}

dependencies {
    // ─── Shizuku ───────────────────────────────────────────────────────────────
    // API: uygulama tarafı (binder çağrıları için)
    implementation("dev.rikka.shizuku:api:13.1.5")
    // Provider: Shizuku permission sağlayıcı (AndroidManifest'te kayıt gerekli)
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // ─── Jetpack Compose ──────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ─── Lifecycle + ViewModel ────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ─── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ─── Activity ─────────────────────────────────────────────────────────────
    implementation("androidx.activity:activity-compose:1.9.3")

    // ─── DataStore (ayarlar + offender listesi kalıcı depolama) ───────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ─── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ─── JSON Serialization (log export için) ─────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
