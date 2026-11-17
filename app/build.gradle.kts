import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use(localProperties::load)
val apiBaseUrl = localProperties.getProperty("fortRushApiBaseUrl", "http://10.0.2.2:3000").trimEnd('/')
val matchmakingBaseUrl = localProperties.getProperty("fortRushMatchmakingBaseUrl", apiBaseUrl).trimEnd('/')
val escapedApiBaseUrl = apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val escapedMatchmakingBaseUrl = matchmakingBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "org.TheIza.fortrush"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.TheIza.fortrush"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "FORT_RUSH_API_BASE_URL", "\"$escapedApiBaseUrl\"")
        buildConfigField("String", "FORT_RUSH_MATCHMAKING_BASE_URL", "\"$escapedMatchmakingBaseUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("test").resources.srcDir(rootProject.file("shared"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.googleid)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
