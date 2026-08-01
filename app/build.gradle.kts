plugins {
    id("com.android.application")
}

android {
    namespace = "uk.cpjsmith.ponypaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.cpjsmith.ponypaper"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "1.7.0-modern"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Phase 1: no AndroidX migration yet; framework Preference APIs still compile.
}
