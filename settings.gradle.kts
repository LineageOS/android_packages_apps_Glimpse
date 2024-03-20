/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../lineagex/gradle/libs.versions.toml"))
        }
        create("lineagex") {
            from(files("../lineagex/gradle/lineagex.versions.toml"))
        }
    }
}
rootProject.name = "Glimpse"

include(":core")
include(":settingslib")
include(":ui")

project(":core").projectDir = File("../lineagex", "../lineagex/core")
project(":settingslib").projectDir = File("../lineagex", "../lineagex/settingslib")
project(":ui").projectDir = File("../lineagex", "../lineagex/ui")

include(":app")
