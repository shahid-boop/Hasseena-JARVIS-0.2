plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.hasseena.jarvis"; compileSdk = 35
    defaultConfig { applicationId = "com.hasseena.jarvis"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
}

kotlin { jvmToolchain(21) }

// Hasseena intentionally uses Android platform APIs for networking, speech recognition,
// TTS, notifications and storage. This keeps the first build small and avoids hidden dependencies.
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
