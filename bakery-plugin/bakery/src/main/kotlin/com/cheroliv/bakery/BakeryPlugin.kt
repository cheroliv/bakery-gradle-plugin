package com.cheroliv.bakery

import com.cheroliv.bakery.ConfigPrompts.getOrPrompt
import com.cheroliv.bakery.ConfigPrompts.saveConfiguration
import com.cheroliv.bakery.FileSystemManager.copyResourceDirectory
import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.FileSystemManager.isYmlUri
import com.cheroliv.bakery.FileSystemManager.loadProperties
import com.cheroliv.bakery.FileSystemManager.yamlMapper
import com.cheroliv.bakery.GitService.GIT_ATTRIBUTES_CONTENT
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.File
import java.io.File.separator
import kotlin.text.Charsets.UTF_8


class BakeryPlugin : Plugin<Project> {
    companion object {
        const val BAKERY_GROUP = "bakery"
        const val BAKE_TASK = "bake"
        const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"
        const val ASCIIDOC_ATTRIBUTES_PROP = "asciidoctor.attributes"
        const val ASCIIDOC_DIAGRAMS_DIRECTORY = "imagesDir=diagrams"
        const val ASCIIDOC_SOURCE_DIR = "sourceDir"

        @Suppress("unused")
        const val CNAME = "CNAME"
    }

    override fun apply(project: Project) {
        // Création de la configuration jbakeRuntime
        val jbakeRuntime: Configuration = project.configurations.create("jbakeRuntime").apply {
            description = "Classpath for running Jbake core directly"
        }

        // Ajout des dépendances à la configuration
        project.dependencies.apply {
            add(jbakeRuntime.name, "org.jbake:jbake-core:2.6.7")
            add(jbakeRuntime.name, "commons-configuration:commons-configuration:1.10")
            add(jbakeRuntime.name, "org.asciidoctor:asciidoctorj-diagram:3.0.1")
            add(jbakeRuntime.name, "org.asciidoctor:asciidoctorj-diagram-plantuml:1.2025.3")
        }

        val bakeryExtension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        project.afterEvaluate {
            // If site.yml does not exist then jbakeExtension is not configured,
            // publishSite and publishMaquette not registered.
            // Only initSite is available
            if (!bakeryExtension.configPath.isPresent) {
                val gradlePropertiesFile = project.layout.projectDirectory.asFile.resolve("gradle.properties")
                if (gradlePropertiesFile.exists()) {
                    gradlePropertiesFile.loadProperties().run {
                        if (keys.contains("bakery.configPath") &&
                            this["bakery.configPath"].toString().isNotBlank() &&
                            this["bakery.configPath"].toString().isYmlUri
                        ) bakeryExtension.configPath.set(this["bakery.configPath"].toString())
                    }
                } else project.logger.info("Nor dsl configuration like 'bakery { configPath = file(\"site.yml\").absolutePath }\n' or gradle properties file found")
            }

            if (!project.layout.projectDirectory.asFile.resolve(bakeryExtension.configPath.get()).exists()) {

                "config file does not exists."
                    .apply(::println)
                    .let(project.logger::info)

                project.tasks.register("initSite") { task ->
                    task.run {
                        group = BAKERY_GROUP
                        description = "Initialise site and maquette folders."
                        val configFile = project
                            .projectDir
                            .resolve("site.yml")
                            .apply { createNewFile() }
                            .also {
                                "create config file."
                                    .apply(::println)
                                    .let(project.logger::info)
                            }
                        val site = SiteConfiguration(BakeConfiguration("site", "bake"))
                        site.run(yamlMapper::writeValueAsString)
                            .run(configFile::writeText)
                            .also {
                                "write config file."
                                    .apply(::println)
                                    .let(project.logger::info)
                            }
                        project.projectDir.resolve(".gitignore").run {
                            if (!exists()) {
                                createNewFile()
                                writeText(".gradle\nbuild\n.kotlin\nsite.yml\n.idea\n*.iml\n*.ipr\n*.iws\nlocal.properties\n")
                            } else if (!readText(UTF_8).contains("site.yml"))
                                appendText("\nsite.yml\n", UTF_8)
                        }
                        project.projectDir.resolve(".gitattributes").run {
                            if (!exists()) {
                                createNewFile()
                                writeText(GIT_ATTRIBUTES_CONTENT.trimIndent(), UTF_8)
                            }

                        }
                        copyResourceDirectory(site.bake.srcPath, project.projectDir, project)
                        copyResourceDirectory("maquette", project.projectDir, project)
                    }
                }
            } else {
                val site = from(project, bakeryExtension.configPath.get())

                project.plugins.apply(JBakePlugin::class.java)

                project.extensions.configure(JBakeExtension::class.java) {
                    it.srcDirName = site.bake.srcPath
                    it.destDirName = site.bake.destDirPath
                    it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
                    it.configuration[ASCIIDOC_ATTRIBUTES_PROP] = arrayOf(
                        "$ASCIIDOC_SOURCE_DIR=${project.projectDir.resolve(site.bake.srcPath)}",
                        ASCIIDOC_DIAGRAMS_DIRECTORY,
                    )
                }

                project.tasks.withType(JBakeTask::class.java)
                    .getByName(BAKE_TASK).apply {
                        input = project.file(site.bake.srcPath)
                        output = project.layout.buildDirectory
                            .dir(site.bake.destDirPath)
                            .get()
                            .asFile
                    }

                project.tasks.register("publishSite") { task ->
                    task.run {
                        group = BAKERY_GROUP
                        description = "Publish site online."
                        dependsOn(BAKE_TASK)
                        doFirst { site.createCnameFile(project) }
                        doLast {
                            pushPages(
                                { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.bake.destDirPath}" },
                                { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.pushPage.to}" },
                                site.pushPage,
                                project.logger
                            )
                        }
                    }
                }

                project.tasks.register("publishMaquette") { task ->
                    task.run {
                        group = BAKERY_GROUP
                        description = "Publish maquette online."
                        val uiDir: File = project
                            .layout.projectDirectory.asFile
                            .resolve(site.pushMaquette.from)
                        val uiBuildDir: File = project
                            .layout.buildDirectory.asFile.get()
                            .resolve(site.pushMaquette.from)

                        @Suppress("SpellCheckingInspection")
                        val destDir = project
                            .layout.buildDirectory.get()
                            .asFile.resolve(site.pushMaquette.to)
                        doFirst {
                            if (!uiDir.exists()) throw IllegalStateException("$uiDir does not exist")
                            if (!uiDir.isDirectory) throw IllegalStateException("$uiDir should be a directory")
                            if (uiBuildDir.exists()) uiBuildDir.deleteRecursively()
                            if (!uiBuildDir.exists()) uiBuildDir.mkdirs()
                            if (!uiBuildDir.isDirectory) throw IllegalStateException("$uiBuildDir should be directory")
                            uiDir.absolutePath
                                .apply(project.logger::info)
                                .run(::println)
                            uiBuildDir
                                .path
                                .apply(project.logger::info)
                                .run(::println)
                            uiDir.copyRecursively(uiBuildDir, true)
                        }
                        doLast {
                            pushPages(
                                destPath = { "$uiBuildDir" },
                                pathTo = { "$destDir" },
                                site.pushMaquette,
                                project.logger
                            )
                        }
                    }
                }

                project.tasks.register("serve", JavaExec::class.java) { task ->
                    task.run {
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
                            "-b", project.file(site.bake.srcPath).absolutePath,
                            "-s",
                            project
                                .layout
                                .buildDirectory.get()
                                .asFile.resolve(site.bake.destDirPath)
                                .absolutePath,
                        )
                        doFirst {
                            "Serving $group at: https://localhost:8820/"
                                .apply(project.logger::info)
                                .run(::println)
                        }
                    }
                }

                project.tasks.register("updatePagesSecret") { task -> }

                project.tasks.register("createPagesRepository") { task -> }

                project.tasks.register("configureSite") { task ->
                    task.run {
                        group = BAKERY_GROUP
                        description = "Initialize Bakery configuration."

                        doLast {
                            val token = getOrPrompt(
                                project = project,
                                propertyName = "GitHub Token",
                                cliProperty = "githubToken",
                                sensitive = true
                            )

                            val username = getOrPrompt(
                                project = project,
                                propertyName = "GitHub Username",
                                cliProperty = "githubUsername",
                                sensitive = false
                            )

                            val repo = getOrPrompt(
                                project = project,
                                propertyName = "GitHub Repository URL",
                                cliProperty = "githubRepo",
                                sensitive = false,
                                example = "https://github.com/username/repo.git"
                            )

                            val configPath = getOrPrompt(
                                project = project,
                                propertyName = "Config Path",
                                cliProperty = "configPath",
                                sensitive = false,
                                example = "site.yml",
                                default = "site.yml"
                            )

                            project.logger.lifecycle("✓ Bakery configuration completed:")
                            project.logger.lifecycle("  Username: $username")
                            project.logger.lifecycle("  Repository: $repo")
                            project.logger.lifecycle("  Config Path: $configPath")
                            project.logger.lifecycle("  Token: ${if (token.isNotEmpty()) "***configured***" else "not set"}")

                            saveConfiguration(project, token, username, repo, configPath)

                            project.logger.lifecycle("")
                            project.logger.lifecycle("✓ Configuration saved successfully!")
                            project.logger.lifecycle("  You can now run: ./gradlew bake")
                        }
                    }
                }

            }
        }
    }
}
