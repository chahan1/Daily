plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chahan.daily"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chahan.daily"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // 固定簽署金鑰：每次編譯的 APK 簽章相同，才能直接覆蓋安裝更新
    signingConfigs {
        create("fixed") {
            storeFile = file("daily.jks")
            storePassword = "dailyapp"
            keyAlias = "daily"
            keyPassword = "dailyapp"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
