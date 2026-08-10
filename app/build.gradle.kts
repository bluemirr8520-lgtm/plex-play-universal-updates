plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val plexReleaseStorePath = System.getenv("PLEX_RELEASE_KEYSTORE")
val plexReleaseStorePassword = System.getenv("PLEX_RELEASE_STORE_PASSWORD")
val plexReleaseKeyAlias = System.getenv("PLEX_RELEASE_KEY_ALIAS")
val plexReleaseKeyPassword = System.getenv("PLEX_RELEASE_KEY_PASSWORD")
val plexReleaseSigningReady = listOf(
    plexReleaseStorePath,
    plexReleaseStorePassword,
    plexReleaseKeyAlias,
    plexReleaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.mirr.plexplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.mirr.plexplay.universal"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (plexReleaseSigningReady) {
            create("plexRelease") {
                storeFile = rootProject.file(plexReleaseStorePath!!)
                storePassword = plexReleaseStorePassword
                keyAlias = plexReleaseKeyAlias
                keyPassword = plexReleaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (plexReleaseSigningReady) {
                signingConfigs.getByName("plexRelease")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.name.contains("release", ignoreCase = true)
    }
    if (releaseRequested && !plexReleaseSigningReady) {
        throw GradleException(
            "Release signing is not configured. Set PLEX_RELEASE_KEYSTORE, " +
                "PLEX_RELEASE_STORE_PASSWORD, PLEX_RELEASE_KEY_ALIAS and " +
                "PLEX_RELEASE_KEY_PASSWORD.",
        )
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-effect:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation(files("libs/libvlc-all-3.7.5.aar"))

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
