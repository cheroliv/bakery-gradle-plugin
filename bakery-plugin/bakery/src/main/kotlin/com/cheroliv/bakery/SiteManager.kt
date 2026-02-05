package com.cheroliv.bakery

import com.cheroliv.bakery.ConfigPrompts.getOrPrompt
import com.cheroliv.bakery.ConfigPrompts.saveConfiguration
import com.cheroliv.bakery.FileSystemManager.copyResourceDirectory
import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.FileSystemManager.isYmlUri
import com.cheroliv.bakery.FileSystemManager.yamlMapper
import com.cheroliv.bakery.GitService.GIT_ATTRIBUTES_CONTENT
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.File
import java.io.File.separator
import kotlin.text.Charsets.UTF_8

object SiteManager {

    const val BAKERY_GROUP = "bakery"
    const val BAKE_TASK = "bake"
    const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
    const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"
    const val ASCIIDOC_ATTRIBUTES_PROP = "asciidoctor.attributes"
    const val ASCIIDOC_DIAGRAMS_DIRECTORY = "imagesDir=diagrams"
    const val ASCIIDOC_SOURCE_DIR = "sourceDir"
    const val BAKERY_CONFIG_PATH_KEY = "bakery.config.path"
    const val CNAME = "CNAME"


    fun Project.createJBakeRuntimeConfiguration()
            : Configuration = configurations.create("jbakeRuntime").apply {
        description = "Classpath for running Jbake core directly"
        listOf(
            "org.jbake:jbake-core:2.6.7",
            "commons-configuration:commons-configuration:1.10",
            "org.asciidoctor:asciidoctorj-diagram:3.0.1",
            "org.asciidoctor:asciidoctorj-diagram-plantuml:1.2025.3"
        ).forEach { this@createJBakeRuntimeConfiguration.dependencies.add(name, it) }
    }

    fun Project.configureConfigPath(bakeryExtension: BakeryExtension, isGradlePropertiesEnabled: Boolean) {
        if (isGradlePropertiesEnabled) return
        else {
            val gradlePropertiesFile = layout.projectDirectory.asFile.resolve("gradle.properties")
            if (gradlePropertiesFile.exists())
                properties.run {
                    val configPath = this[BAKERY_CONFIG_PATH_KEY]?.toString()
                    if (keys.contains(BAKERY_CONFIG_PATH_KEY) &&
                        !configPath.isNullOrBlank() &&
                        configPath.isYmlUri
                    ) bakeryExtension.configPath.set(configPath)
                } else logger.info(
                "Nor dsl configuration like 'bakery { configPath = file(\"site.yml\").absolutePath }\n' " +
                        "or gradle properties file found"
            )
        }
    }

    // ==================== Init Site Task ====================

    fun Project.registerInitSiteTask() {
        tasks.register("initSite") { task ->
            task.apply {
                group = BAKERY_GROUP
                description = "Initialise site and maquette folders."

                doLast {
                    val configFile = project.projectDir.resolve("site.yml").run {
                        if (exists()) this else createAndConfigureSiteYml()
                    }
                    setupGitIgnore()
                    setupGitAttributes()
                    copySiteResources(configFile)
                }
            }
        }
    }

    private fun Project.createAndConfigureSiteYml(): File {
        val configFile = projectDir.resolve("site.yml").apply {
            createNewFile()
        }.also {
            "create config file."
                .apply(::println)
                .let(logger::info)
        }

        val site = SiteConfiguration(BakeConfiguration("site", "bake"))
        site.run(yamlMapper::writeValueAsString)
            .run(configFile::writeText)
            .also {
                "write config file."
                    .apply(::println)
                    .let(project.logger::info)
            }

        return configFile
    }


    private fun Project.setupGitIgnore() {
        projectDir.resolve(".gitignore").run {
            if (!exists()) {
                createNewFile()
                writeText(
                    ".gradle\nbuild\n.kotlin\nsite.yml\n.idea\n" +
                            "*.iml\n*.ipr\n*.iws\nlocal.properties\n"
                )
            } else if (!readText(UTF_8).contains("site.yml")) {
                appendText("\nsite.yml\n", UTF_8)
            }
        }
    }

    private fun Project.setupGitAttributes() {
        projectDir.resolve(".gitattributes").run {
            if (!exists()) {
                createNewFile()
                writeText(GIT_ATTRIBUTES_CONTENT.trimIndent(), UTF_8)
            }
        }
    }

    private fun Project.copySiteResources(configFile: File) {
        val site = from(configFile.absolutePath)
        copyResourceDirectory(site.bake.srcPath, project.projectDir, project)
        copyResourceDirectory("maquette", project.projectDir, project)
    }

    // ==================== Bakery Tasks Configuration ====================

    internal fun Project.configureJBakePlugin(site: SiteConfiguration) {
        plugins.apply(JBakePlugin::class.java)

        extensions.configure(JBakeExtension::class.java) {
            it.srcDirName = site.bake.srcPath
            it.destDirName = site.bake.destDirPath
            it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
            it.configuration[ASCIIDOC_ATTRIBUTES_PROP] = arrayOf(
                "$ASCIIDOC_SOURCE_DIR=${project.projectDir.resolve(site.bake.srcPath)}",
                ASCIIDOC_DIAGRAMS_DIRECTORY,
            )
        }
    }

    internal fun Project.configureBakeTask(site: SiteConfiguration) {
        tasks.withType(JBakeTask::class.java)
            .getByName(BAKE_TASK).apply {
                input = file(site.bake.srcPath)
                output = layout.buildDirectory
                    .dir(site.bake.destDirPath)
                    .get()
                    .asFile
            }
    }

    // ==================== Publish Site Task ====================

    internal fun Project.registerPublishSiteTask(site: SiteConfiguration) {
        tasks.register("publishSite") { task ->
            task.apply {
                group = BAKERY_GROUP
                description = "Publish site online."
                dependsOn(BAKE_TASK)

                doFirst { site.createCnameFile(project) }
                doLast {
                    val buildDir = layout.buildDirectory.get().asFile.absolutePath
                    pushPages(
                        { "$buildDir$separator${site.bake.destDirPath}" },
                        { "$buildDir$separator${site.pushPage.to}" },
                        site.pushPage,
                        logger
                    )
                }
            }
        }
    }

    // ==================== Publish Maquette Task ====================

    internal fun Project.registerPublishMaquetteTask(site: SiteConfiguration) {
        tasks.register("publishMaquette") { task ->
            task.apply {
                group = BAKERY_GROUP
                description = "Publish maquette online."
                doFirst { prepareAndCopyMaquette(site) }
                doLast { publishMaquetteToPages(site) }
            }
        }
    }

    private fun Project.prepareAndCopyMaquette(site: SiteConfiguration) {
        val uiDir = layout.projectDirectory.asFile
            .resolve(site.pushMaquette.from)
        val uiBuildDir = layout.buildDirectory.asFile.get()
            .resolve(site.pushMaquette.from)

        // Validation
        if (!uiDir.exists()) throw IllegalStateException("$uiDir does not exist")
        if (!uiDir.isDirectory) throw IllegalStateException("$uiDir should be a directory")

        // Préparation du répertoire de build
        if (uiBuildDir.exists()) uiBuildDir.deleteRecursively()
        uiBuildDir.mkdirs()

        if (!uiBuildDir.isDirectory) throw IllegalStateException("$uiBuildDir should be directory")

        // Logging et copie
        uiDir.absolutePath
            .apply(logger::info)
            .run(::println)

        uiBuildDir.path
            .apply(logger::info)
            .run(::println)

        uiDir.copyRecursively(uiBuildDir, overwrite = true)
    }

    private fun Project.publishMaquetteToPages(site: SiteConfiguration) {
        val uiBuildDir = layout.buildDirectory.asFile.get()
            .resolve(site.pushMaquette.from)
        val destDir = layout.buildDirectory.get().asFile
            .resolve(site.pushMaquette.to)

        pushPages(
            { "$uiBuildDir" },
            { "$destDir" },
            site.pushMaquette,
            logger
        )
    }

    // ==================== Serve Task ====================

    internal fun Project.registerServeTask(
        site: SiteConfiguration,
        jbakeRuntime: Configuration
    ) {
        tasks.register("serve", JavaExec::class.java) { task ->
            task.apply {
                group = BAKERY_GROUP
                description = "Serves the baked site locally."
                mainClass.set("org.jbake.launcher.Main")
                classpath = jbakeRuntime
                environment("GEM_PATH", jbakeRuntime.asPath)
                jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                )
                args = listOf(
                    "-b", file(site.bake.srcPath).absolutePath,
                    "-s", layout.buildDirectory.get()
                        .asFile.resolve(site.bake.destDirPath)
                        .absolutePath
                )

                doFirst {
                    "Serving $group at: https://localhost:8820/"
                        .apply(logger::info)
                        .run(::println)
                }
            }
        }
    }

    // ==================== Configure Site Task ====================

    internal fun Project.registerConfigureSiteTask(
        site: SiteConfiguration,
        isGradlePropertiesEnabled: Boolean
    ) {
        tasks.register("configureSite") { task ->
            task.apply {
                group = BAKERY_GROUP
                description = "Initialize Bakery configuration."

                doLast {
                    val token = getOrPrompt(
                        propertyName = "GitHub Token",
                        cliProperty = "githubToken",
                        sensitive = true
                    )
                    val username = getOrPrompt(
                        propertyName = "GitHub Username",
                        cliProperty = "githubUsername",
                        sensitive = false
                    )
                    val repo = getOrPrompt(
                        propertyName = "GitHub Repository URL",
                        cliProperty = "githubRepo",
                        sensitive = false,
                        example = "https://github.com/username/repo.git"
                    )
                    val configPath = getOrPrompt(
                        propertyName = "Config Path",
                        cliProperty = "configPath",
                        sensitive = false,
                        example = "site.yml",
                        default = "site.yml"
                    )
                    logger.lifecycle("✓ Bakery configuration completed:")
                    logger.lifecycle("  Username: $username")
                    logger.lifecycle("  Repository: $repo")
                    logger.lifecycle("  Config Path: $configPath")
                    logger.lifecycle("  Token: ${if (token.isNotEmpty()) "***configured***" else "not set"}")

                    project.saveConfiguration(site, isGradlePropertiesEnabled)

                    logger.lifecycle("")
                    logger.lifecycle("✓ Configuration saved successfully!")
                    logger.lifecycle("  You can now run: ./gradlew bake")
                }
            }
        }
    }

    // ==================== Utility Tasks ====================

    internal fun Project.registerUtilityTasks() {
        tasks.register("createPagesRepository") { }
        tasks.register("updatePagesSecret") { }
    }


}