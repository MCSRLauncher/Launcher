plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.2"
}

group = "com.redlimerl.mcsrlauncher"
version = project.properties["projectVersion"]!!

repositories {
    mavenCentral()
    maven { setUrl("https://www.jetbrains.com/intellij-repository/releases") }
}

dependencies {
    implementation("com.jetbrains.intellij.java:java-gui-forms-rt:243.26574.98")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")

    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")

    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("org.apache.commons:commons-text:1.13.1")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    implementation("commons-io:commons-io:2.19.0")

    implementation("com.google.guava:guava:33.4.8-jre")

    implementation("io.github.z4kn4fein:semver:1.4.2")

    implementation("com.formdev:flatlaf:3.6.1")
    implementation("com.formdev:flatlaf-fonts-roboto:2.137")
    implementation("com.miglayout:miglayout-core:5.3")
    implementation("com.miglayout:miglayout-swing:5.3")

    implementation("com.github.oshi:oshi-core:6.8.2")

    implementation("org.jabref:mslinks:1.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.named<ProcessResources>("processResources") {
    doFirst {
        val versionFile = file("${layout.buildDirectory.get()}/generated-resources/version")
        versionFile.parentFile.mkdirs()
        versionFile.writeText(version.toString())
    }

    from("${layout.buildDirectory.get()}/generated-resources") {
        into("")
    }
}

tasks.register<Copy>("buildUpdater") {
    dependsOn(":LauncherUpdater:jar")

    from(project(":LauncherUpdater").layout.buildDirectory.file("libs/LauncherUpdater.jar"))
    into(layout.buildDirectory.dir("resources/main"))
}

tasks.named("processResources") {
    dependsOn("buildUpdater")
}

tasks.withType<Jar> {
    archiveVersion = ""
    manifest {
        attributes(
            "Main-Class" to "com.redlimerl.mcsrlauncher.MCSRLauncher",
            "Implementation-Version" to version
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("MCSRLauncher")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
}