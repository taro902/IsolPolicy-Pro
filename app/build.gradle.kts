plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.cache.fix)
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val keystorePass: String? = System.getenv("KEYSTORE_PASS")
val keyPass: String? = System.getenv("KEYPASS")
val haveReleaseSigning = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()

android {
    namespace = "io.github.mhmrdd.isolationpolicy"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mhmrdd.isolationpolicy"
        minSdk = 29
        targetSdk = 34
        versionCode = 200
        versionName = "2.0-API102"
    }

    signingConfigs {
        if (haveReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "isolationpolicy"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "META-INF/*.version"
            excludes += "META-INF/proguard/*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "kotlin/**"
            excludes += "DebugProbesKt.bin"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.androidx.recyclerview)
}
tasks.matching { it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}
