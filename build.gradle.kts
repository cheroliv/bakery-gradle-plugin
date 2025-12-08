import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bakery)
}

bakery { configPath = file("site.yml").absolutePath }

val jbakeRuntime: Configuration by configurations.creating {
    description = "Classpath for running Jbake core directly"
}

dependencies {
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.ktor.client)
    testImplementation(libs.playwright)
    testImplementation(libs.supabase.postgrest)
    testImplementation(libs.supabase.storage)
    testImplementation(libs.supabase.jackson)
    testImplementation(libs.supabase.auth)
    testImplementation(libs.supabase.auth.jvm)

    listOf(
        kotlin("test"),
        gradleTestKit(),
        libs.junit.jupiter,
        libs.jackson.module.kotlin,
        libs.jackson.dataformat.yaml,
        libs.jackson.datatype.jsr310,
        libs.jackson.module.jsonSchema,
        libs.jackson.databind.yaml,
    ).forEach { testImplementation(it) }

    jbakeRuntime("org.jbake:jbake-core:2.6.7")

    arrayOf(
        "commons-configuration:commons-configuration:1.10",
        "org.asciidoctor:asciidoctorj-diagram:3.0.1",
        "org.asciidoctor:asciidoctorj-diagram-plantuml:1.2025.3",
    ).map { jbakeRuntime(it) }
}

//tasks.register<JavaExec>("serve") {
//    group = "bakery"
//    description = "Serves the baked site locally."
//    val site: SiteConfiguration = readSiteConfiguration(
//        project,
//        project.file(bakery.configPath)
//    )
//    mainClass.set("org.jbake.launcher.Main")
//    classpath = jbakeRuntime
//    environment(
//        "GEM_PATH",
//        configurations.getByName("jbakeRuntime").asPath
//    )
//    jvmArgs(
//        "--add-opens=java.base/java.lang=ALL-UNNAMED",
//        "--add-opens=java.base/java.util=ALL-UNNAMED",
//        "--add-opens=java.base/java.io=ALL-UNNAMED",
//        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
//    )
//    args = listOf(
//        "-b",
//        site.bake.srcPath
//            .run(project::file)
//            .absolutePath,
//        "-s",
//        site.bake.destDirPath
//            .run(project.layout.buildDirectory.get().asFile::resolve)
//            .absolutePath,
//    )
//    doFirst {
//        "Serving $group at: https://localhost:8820/"
//            .run(::println)
//    }
//}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = libs.gradle.tooling.api.get().version
    distributionType = BIN
}

tasks.withType<Test> {
    useJUnitPlatform()
//    dependsOn("serve")
//    finalizedBy("stopServe")
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED"
    )
    systemProperty("project.root.dir", project.rootDir.absolutePath)
    systemProperty("playwright.headless", "false")
    testLogging { showStandardStreams = true }
}

val functionalTest by sourceSets.creating

val serveScript = """
import org.apache.commons.lang.SystemUtils.USER_HOME
import java.io.ByteArrayOutputStream

buildscript {
    repositories.mavenCentral()
    arrayOf(
        "commons-configuration:commons-configuration:1.10",
        "org.asciidoctor:asciidoctorj-diagram:3.0.1",
        "org.asciidoctor:asciidoctorj-diagram-plantuml:1.2025.3",
    ).map { dependencies.classpath(it) }
}

plugins { this.id("org.jbake.site").version("5.5.0") }

project.tasks.wrapper {
    gradleVersion = "9.0.0"
    distributionType = Wrapper.DistributionType.BIN
}

val jbakeRuntime: Configuration by configurations.creating {
    description = "Classpath for running Jbake core directly"
}

dependencies { jbakeRuntime("org.jbake:jbake-core:2.7.0-rc.7") }

tasks.bakeInit {
    templateUrl = project.file("src/resources/example_project_freemarker.zip").path
}

jbake {
    srcDirName = "src/jbake".run(project::file).path
    destDirName = "jbake".run(project.layout.buildDirectory.get()::file).asFile.path
    configuration["asciidoctor.option.requires"] = "asciidoctor-diagram"
    configuration["asciidoctor.attributes"] = arrayOf(
        "sourceDir=${projectDir}",
        "imagesDir=diagrams",
        "imagesoutdir=${tasks.bake.get().input}/assets/diagrams"
    )
}


//TODO: Add in task initialize the generation of the site yml configuration file.
// si il trouve pas de fichier de configuration, il faut le créer
// sinon laisser le fichier de configuration existant
// et chercher si il trouve un fichier de configuration jbake.properties dans la valeur srcDirPath du fichier de configuration
// ajouter l'installation de jbake si il n'est pas installé dans l'action(github action) initialize
tasks.register<Exec>("initialize") {
    group = project.projectDir.name
    val srcDirPath = "src/jbake".apply {
        run(project::file)
            .run { if (!exists()) mkdirs() }
    }
    val baker = "${'$'}USER_HOME/.sdkman/candidates/jbake/2.6.7/bin/jbake".apply {
        if (!run(::File).exists()) {
            "Jbake executable not found at: ${'$'}this".run(::println)
            throw "Jbake executable not found".run(::IllegalStateException)
        }
    }
    doFirst {
        srcDirPath
            .run { "Initializing Jbake source directory at: ${'$'}this" }
            .run(::println)
    }
    commandLine = listOf(baker, "-i", srcDirPath)
    workingDir = projectDir
    standardOutput = ByteArrayOutputStream()
}    
""".trimIndent()