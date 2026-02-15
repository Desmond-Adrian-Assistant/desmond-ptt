import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.desmond.ptt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.desmond.ptt"
        minSdk = 26
        targetSdk = 34
        versionCode = 37
        versionName = "3.7"
        
        // Only include ARM64 to reduce APK size (Z Fold 7 is arm64-v8a)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Telegram config from local.properties
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"${localProperties.getProperty("telegram.botToken", "")}\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"${localProperties.getProperty("telegram.chatId", "")}\"")
        buildConfigField("int", "TELEGRAM_API_ID", localProperties.getProperty("telegram.apiId", "0"))
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("telegram.apiHash", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Encrypted SharedPreferences for secure credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // HTTP client for Telegram API (backup)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // TDLib - Telegram user client (send messages as YOU, not a bot)
    // Using locally built native libs in jniLibs/ + Java classes in java/org/drinkless/
}
