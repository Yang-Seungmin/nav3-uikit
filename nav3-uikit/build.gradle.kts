plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

group = "dev.seungmin.nav3"
version = providers.gradleProperty("nav3.version").getOrElse("0.1.0-SNAPSHOT")

kotlin {
    explicitApi()

    android {
        namespace = "dev.seungmin.nav3.uikit"
        compileSdk = 37
        minSdk = 23
    }
    jvm()

    // Every platform without UIKit delegates to NavDisplay through the nonIos source set.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonIos") {
                withJvm()
                withCompilations { it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.ui)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
