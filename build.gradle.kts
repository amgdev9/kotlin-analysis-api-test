val analysisApiKotlinVersion = "2.0.20-dev-3728"
val intellijVersion = "213.7172.25"

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
    implementation("org.jetbrains.kotlin:high-level-api-fir-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:high-level-api-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:low-level-api-fir-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:analysis-api-providers-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:analysis-project-structure-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:symbol-light-classes-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:analysis-api-standalone-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains.kotlin:high-level-api-impl-base-for-ide:$analysisApiKotlinVersion") {
        isTransitive = false
    }

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("org.example.Main")
}