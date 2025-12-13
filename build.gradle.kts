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
    jbakeRuntime("org.jbake:jbake-core:2.6.7")

    arrayOf(
        "commons-configuration:commons-configuration:1.10",
        "org.asciidoctor:asciidoctorj-diagram:3.0.1",
        "org.asciidoctor:asciidoctorj-diagram-plantuml:1.2025.3",
    ).map { jbakeRuntime(it) }
}

/**
 * TODO: acces to SiteConfiguration programmatically, and add to plugin
 */
tasks.register<JavaExec>("serve") {
    group = "bakery"
    description = "Serves the baked site locally."
//    val site: SiteConfiguration = readSiteConfiguration(
//        project,
//        project.file(bakery.configPath)
//    )
    mainClass.set("org.jbake.launcher.Main")
    classpath = jbakeRuntime
    environment(
        "GEM_PATH",
        configurations.getByName("jbakeRuntime").asPath
    )
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
    )

    args = listOf(
        "-b",
//        site.bake.srcPath
        "site"
            .run(project::file)
            .absolutePath,
        "-s",
//        site.bake.destDirPath
        "bake"
            .run(project.layout.buildDirectory.get().asFile::resolve)
            .absolutePath,
    )
    doFirst {
        "Serving $group at: https://localhost:8820/"
            .run(::println)
    }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = libs.gradle.tooling.api.get().version
    distributionType = BIN
}