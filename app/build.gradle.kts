import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

// Optional release signing. Prefer (in order):
// 1) keystore.properties at the repo root (local builds; never commit)
// 2) SIGNING_* environment variables (CI / GitHub Actions secrets)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
val envStoreFile = System.getenv("SIGNING_STORE_FILE")
val hasReleaseSigning =
    keystorePropertiesFile.exists() || (envStoreFile != null && envStoreFile.isNotBlank())

android {
    namespace = "uk.cpjsmith.ponypaper"
    compileSdk = 35

    defaultConfig {
        // Install identity for this fork (separate from upstream uk.cpjsmith.ponypaper).
        // Java package / namespace stay upstream-shaped for less source churn.
        applicationId = "io.github.derram.ponypaper"
        minSdk = 21
        targetSdk = 35
        versionCode = 180
        versionName = "1.8.0-modern"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                if (keystorePropertiesFile.exists()) {
                    storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                    storePassword = keystoreProperties["storePassword"] as String
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                } else {
                    storeFile = file(envStoreFile!!)
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// Align transitive Kotlin artifacts. Without this, kotlin-stdlib resolves to
// 1.8.22 (from AndroidX) while kotlinx-coroutines still pulls
// kotlin-stdlib-jdk7/jdk8:1.6.21, and check*DuplicateClasses fails because
// those JDK7/8 APIs were folded into kotlin-stdlib in 1.8+.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("1.8.22")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference:1.2.1")
}
