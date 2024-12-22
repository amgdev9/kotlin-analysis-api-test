val analysisApiKotlinVersion = "2.1.20-dev-3305"
val intellijVersion = "233.13135.128"
val jdkVersion = 21

plugins {
    kotlin("jvm") version "2.0.20"

    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    maven(url = "https://cache-redirector.jetbrains.com/intellij-third-party-dependencies")
}

dependencies {
    implementation("com.jetbrains.intellij.platform:core:$intellijVersion")
    implementation("com.jetbrains.intellij.platform:core-impl:$intellijVersion")
    implementation("com.jetbrains.intellij.platform:util:$intellijVersion")

    implementation("org.jetbrains.kotlin:kotlin-compiler:$analysisApiKotlinVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")  // Needed by kotlin analysis api
    listOf(
        "org.jetbrains.kotlin:high-level-api-fir-for-ide",
        "org.jetbrains.kotlin:analysis-api-platform-interface-for-ide",
        "org.jetbrains.kotlin:high-level-api-for-ide",
        "org.jetbrains.kotlin:low-level-api-fir-for-ide",
        "org.jetbrains.kotlin:symbol-light-classes-for-ide",
        "org.jetbrains.kotlin:analysis-api-standalone-for-ide",
        "org.jetbrains.kotlin:high-level-api-impl-base-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-common-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fir-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-ir-for-ide",
    ).forEach {
        implementation("$it:$analysisApiKotlinVersion") { isTransitive = false }
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(jdkVersion)
}

application {
    mainClass.set("org.example.MainKt")
}
