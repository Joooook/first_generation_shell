plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jok.unshell"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jok.unshell"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
}