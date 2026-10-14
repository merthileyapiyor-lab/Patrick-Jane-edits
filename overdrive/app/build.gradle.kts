plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: a private key can be supplied through environment variables
// (OVERDRIVE_KEYSTORE, OVERDRIVE_KEYSTORE_PASSWORD, OVERDRIVE_KEY_ALIAS, OVERDRIVE_KEY_PASSWORD).
// Without them the build falls back to the public development key in keystore/, which keeps
// signatures stable between CI builds so updates install over each other.
val envKeystore: String? = System.getenv("OVERDRIVE_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.overdrive.quest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.overdrive.quest"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("overdrive") {
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("OVERDRIVE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OVERDRIVE_KEY_ALIAS")
                keyPassword = System.getenv("OVERDRIVE_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/overdrive-dev.jks")
                storePassword = "overdrive-dev"
                keyAlias = "overdrive"
                keyPassword = "overdrive-dev"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("overdrive")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("overdrive")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

base {
    archivesName.set("Overdrive-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
}
