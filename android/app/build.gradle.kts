import java.security.KeyStore

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jovi.photoai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jovi.photoai"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStoreFile = System.getenv("PHOTOAI_RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("PHOTOAI_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("PHOTOAI_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("PHOTOAI_RELEASE_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (
                !releaseStoreFile.isNullOrBlank() &&
                !releaseStorePassword.isNullOrBlank() &&
                !releaseKeyAlias.isNullOrBlank() &&
                !releaseKeyPassword.isNullOrBlank()
            ) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core / Lifecycle / Activity
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    kapt("androidx.room:room-compiler:2.6.1")
    // Room 2.6.1 requests an older KSP API transitively. Pin to this Kotlin 2.0.21-compatible
    // API so Gradle resolves one compiler API version instead of reaching for a stale artifact.
    kapt("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // CameraX — Preview, ImageCapture, ImageAnalysis (AH0 baseline)
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Unit test
    testImplementation("junit:junit:4.13.2")

    // Android test-only contracts; no production runtime Pose dependencies.
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register("verifyReleaseSigning") {
    doLast {
        fun required(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: throw GradleException("P20_BLOCKED_RELEASE_SIGNING_INPUT: missing $name")

        val storeFile = File(required("PHOTOAI_RELEASE_STORE_FILE"))
        val storePassword = required("PHOTOAI_RELEASE_STORE_PASSWORD")
        val keyAlias = required("PHOTOAI_RELEASE_KEY_ALIAS")
        required("PHOTOAI_RELEASE_KEY_PASSWORD")
        if (!storeFile.isFile) {
            throw GradleException("P20_BLOCKED_RELEASE_SIGNING_INPUT: signing store is unavailable")
        }
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            storeFile.inputStream().use { keyStore.load(it, storePassword.toCharArray()) }
            if (!keyStore.isKeyEntry(keyAlias)) {
                throw GradleException("P20_BLOCKED_RELEASE_SIGNING_INPUT: signing alias is unavailable")
            }
        } catch (error: GradleException) {
            throw error
        } catch (_: Exception) {
            throw GradleException("P20_BLOCKED_RELEASE_SIGNING_INPUT: signing store cannot be opened")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn("verifyReleaseSigning")
    }
}
