plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

// Read .env file
val envFile = rootProject.file(".env")
val envVars = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val parts = trimmed.split("=", limit = 2)
            envVars[parts[0].trim()] = parts[1].trim()
        }
    }
}

android {
    namespace = "com.geoagent"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.geoagent"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TAVILY_API_KEY", "\"${envVars["TAVILY_API_KEY"] ?: ""}\"")
        buildConfigField("String", "LLM_API_KEY", "\"${envVars["LLM_API_KEY"] ?: ""}\"")
        buildConfigField("String", "LLM_BASE_URL", "\"${envVars["LLM_BASE_URL"] ?: "https://api.siliconflow.cn/v1"}\"")
        buildConfigField("String", "LLM_MODEL", "\"${envVars["LLM_MODEL"] ?: "Qwen/Qwen3.5-9B"}\"")
        buildConfigField("int", "LLM_MAX_TOKENS", "${envVars["LLM_MAX_TOKENS"] ?: "16384"}")
        buildConfigField("String", "SILICONFLOW_API_KEY", "\"${envVars["SILICONFLOW_API_KEY"] ?: ""}\"")
        buildConfigField("String", "SILICONFLOW_EMBED_MODEL", "\"${envVars["SILICONFLOW_EMBED_MODEL"] ?: "BAAI/bge-m3"}\"")
        buildConfigField("String", "SMTP_HOST", "\"${envVars["SMTP_HOST"] ?: ""}\"")
        buildConfigField("int", "SMTP_PORT", "${envVars["SMTP_PORT"] ?: "465"}")
        buildConfigField("String", "SMTP_USER", "\"${envVars["SMTP_USER"] ?: ""}\"")
        buildConfigField("String", "SMTP_PASSWORD", "\"${envVars["SMTP_PASSWORD"] ?: ""}\"")
        buildConfigField("String", "EMAIL_FROM", "\"${envVars["EMAIL_FROM"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.recyclerview)
    implementation(libs.drawerlayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.pdfbox.android)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)

    implementation(libs.mlkit.text.recognition)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.android.mail)
    implementation(libs.android.activation)

    implementation(libs.coil)
    implementation(libs.markwon.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
