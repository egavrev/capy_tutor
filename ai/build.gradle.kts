// :ai is plain Kotlin/JVM: the LlmProvider / SpeechToText / TextToSpeech interfaces,
// the JSON caller (retry once on an invalid reply) and the Groq HTTP adapters.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
