import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val siteUrl = providers.gradleProperty("voiceOsSiteUrl")
    .orElse(providers.environmentVariable("VOICE_OS_SITE_URL")).orElse("").get().trim()
if (siteUrl.isNotEmpty()) {
    val site = URI(siteUrl)
    require(site.scheme.equals("https", ignoreCase = true) && site.host != null && site.rawUserInfo == null &&
        site.rawQuery == null && site.rawFragment == null && site.rawPath in listOf("", "/") &&
        (site.port == -1 || site.port in 1..65535) && siteUrl.length <= 300) {
        "voiceOsSiteUrl must be an HTTPS site origin, e.g. https://example.org"
    }
}
val appCode = providers.gradleProperty("voiceOsVersionCode").orElse("2").get().toInt()
val appVersion = providers.gradleProperty("voiceOsVersionName").orElse("0.2.0").get()
require(appCode in 1..2_100_000_000) { "Invalid Android versionCode" }
require(appVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.+-]{0,31}"))) { "Invalid Android versionName" }

android {
    namespace = "ai.voiceos"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.voiceos"
        minSdk = 26
        targetSdk = 36
        versionCode = appCode
        versionName = appVersion
        buildConfigField("String", "DEFAULT_SITE_URL", "\"$siteUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
