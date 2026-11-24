plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.taro902.isolationpolicy"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.taro902.isolationpolicy"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "2.2.0"
    }

    buildFeatures {
        buildConfig = true
        aidl = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            val ksPass = System.getenv("KEYSTORE_PASS")
            val kAlias = System.getenv("KEY_ALIAS")
            val kPass = System.getenv("KEY_PASS") ?: System.getenv("KEYPASS")
            if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !kAlias.isNullOrBlank() && !kPass.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val relSign = signingConfigs.findByName("release")
            if (relSign != null && relSign.storeFile != null) {
                signingConfig = relSign
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

tasks.configureEach {
    if (name.contains("AarMetadata")) {
        enabled = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
