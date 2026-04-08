/*
 * SPDX-FileCopyrightText: 2023-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.lineageos.generatebp)
}

android {
    namespace = "org.lineageos.glimpse"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.lineageos.glimpse"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(libs.adobe.xmpcore)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.recyclerview.selection)
    implementation(libs.androidx.viewpager2)
    implementation(libs.glide)
    implementation(libs.glide.okhttp3.integration)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.zoomimage.view.glide)
}

generateBp {
    targetSdk = android.defaultConfig.targetSdk!!
    minSdk = android.defaultConfig.minSdk!!
    versionCode = android.defaultConfig.versionCode!!
    versionName = android.defaultConfig.versionName!!
    availableInAOSP = { module ->
        when {
            module.group.startsWith("androidx") -> {
                // We provide our own androidx.media3 and androidx.navigation
                !module.group.startsWith("androidx.media3") &&
                        !module.group.startsWith("androidx.navigation")
            }

            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.guava" -> true
            else -> false
        }
    }
}
