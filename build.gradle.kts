plugins {
    kotlin("jvm") version "2.1.0-RC2"
    kotlin("plugin.serialization") version "2.1.0-RC2"
}

group = "com.redlimerl.mcsrlauncher"
version = "1.0"

repositories {
    mavenCentral()
    maven { setUrl("https://www.jetbrains.com/intellij-repository/releases") }
}

dependencies {
    implementation("com.jetbrains.intellij.java:java-gui-forms-rt:243.26574.98")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")

    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-text:1.13.1")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    implementation("commons-io:commons-io:2.19.0")

    implementation("com.google.guava:guava:33.4.8-jre")

    implementation("com.github.zafarkhaja:java-semver:0.10.2")

    implementation("com.formdev:flatlaf:3.6")
    implementation("com.formdev:flatlaf-fonts-roboto:2.137")
    implementation("com.miglayout:miglayout-core:5.3")
    implementation("com.miglayout:miglayout-swing:5.3")

    implementation("com.github.oshi:oshi-core:6.8.2")
}

kotlin {
    jvmToolchain(8)
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "com.redlimerl.mcsrlauncher.MCSRLauncher",
            "Implementation-Version" to version
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })
}