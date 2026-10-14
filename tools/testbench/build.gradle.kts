// Command-line test bench (HANDOFF.md §12, M1): runs the real prompts against Groq's free plan
// with sample lessons and retellings, and writes Markdown reports.
//
//   ./gradlew :testbench:run --args="all"
//   ./gradlew :testbench:run --args="enrich lessons/istorie/SAMPLE-aparitia-omului.md"
//   ./gradlew :testbench:run --args="assess <lesson.md> <retellings.md>"
//   ./gradlew :testbench:run --args="transcribe <audio.m4a> [--lesson <lesson.md>]"
//
// The key comes from GROQ_API_KEY or secrets.properties (git-ignored). It is never printed.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("com.capytutor.testbench.MainKt")
}

tasks.named<JavaExec>("run") {
    // Paths on the command line are relative to the repository root.
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ai"))
    testImplementation(libs.junit)
}
