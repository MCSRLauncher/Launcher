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
    implementation("me.friwi:jcefmaven:122.1.10")
    implementation("com.formdev:flatlaf:3.6.1")
    implementation("com.formdev:flatlaf-fonts-roboto:2.137")
    implementation("com.miglayout:miglayout-core:5.3")
    implementation("com.miglayout:miglayout-swing:5.3")

    implementation("com.github.oshi:oshi-core:6.8.2")
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

val withFrontend = providers.gradleProperty("withFrontend").map { it.toBoolean() }.orElse(false)
val frontendDir = layout.projectDirectory.dir("frontend").asFile
val generatedWebAppDir = layout.buildDirectory.dir("generated-webapp")

val npmInstall = tasks.register<Exec>("npmInstall") {
    onlyIf { withFrontend.get() && frontendDir.resolve("package.json").exists() }
    workingDir = frontendDir
    commandLine = if (System.getProperty("os.name").lowercase().contains("win")) {
        listOf("cmd", "/c", "npm", "install")
    } else {
        listOf("npm", "install")
    }
    inputs.file(frontendDir.resolve("package.json"))
    inputs.file(frontendDir.resolve("package-lock.json"))
    outputs.dir(frontendDir.resolve("node_modules"))
}

val buildFrontend = tasks.register<Exec>("buildFrontend") {
    onlyIf { withFrontend.get() && frontendDir.resolve("package.json").exists() }
    dependsOn(npmInstall)
    workingDir = frontendDir
    commandLine = if (System.getProperty("os.name").lowercase().contains("win")) {
        listOf("cmd", "/c", "npm", "run", "build")
    } else {
        listOf("npm", "run", "build")
    }
    inputs.dir(frontendDir.resolve("src"))
    inputs.file(frontendDir.resolve("package.json"))
    inputs.file(frontendDir.resolve("vite.config.ts"))
    outputs.dir(frontendDir.resolve("dist"))
}

val copyFrontend = tasks.register<Copy>("copyFrontend") {
    onlyIf { withFrontend.get() }
    dependsOn(buildFrontend)
    from(frontendDir.resolve("dist"))
    into(generatedWebAppDir)
}

tasks.named<ProcessResources>("processResources") {
    if (withFrontend.get()) {
        dependsOn(copyFrontend)
        exclude("webapp/**")
        from(generatedWebAppDir) {
            into("webapp")
        }
    }
}
