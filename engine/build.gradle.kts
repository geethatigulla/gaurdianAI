import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM. NO Android dependencies may be added here — the whole point of this
// module is that every detector and the risk engine run as plain JUnit tests in seconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Pure Java phone-number parsing with bundled metadata: works offline, no Android needed.
    implementation(libs.libphonenumber)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Offline evaluation on the labelled dataset; writes the paper's tables to docs/evaluation/.
tasks.register<JavaExec>("evaluate") {
    group = "verification"
    description = "Runs the engine on eval/dataset.csv and writes precision/recall/F1 tables."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ai.guardian.engine.eval.EvaluateKt")
    workingDir = projectDir
    args("eval/dataset.csv", "../docs/evaluation")
}

// Same evaluation on the public UCI SMS Spam Collection (prepare it with eval/prepare_uci.py first).
tasks.register<JavaExec>("evaluateUci") {
    group = "verification"
    description = "Runs the engine on the UCI SMS Spam Collection and writes docs/evaluation/uci/."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ai.guardian.engine.eval.EvaluateKt")
    workingDir = projectDir
    args("eval/external/uci_sms.csv", "../docs/evaluation/uci")
}
