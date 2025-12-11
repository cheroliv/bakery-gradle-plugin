package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.File
import java.io.File.separator


@Suppress("unused")
class BakeryPlugin : Plugin<Project> {
    companion object {
        const val BAKERY_GROUP = "bakery"
        private const val BAKE_TASK = "bake"
        private const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        private const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"
        private const val CNAME = "CNAME"
    }

    override fun apply(project: Project) {

        project.plugins.apply(JBakePlugin::class.java)

        val extension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        project.afterEvaluate {
            val site = from(project, extension.configPath.get())

            project.extensions.configure(JBakeExtension::class.java) {
                it.srcDirName = site.bake.srcPath
                it.destDirName = site.bake.destDirPath
                it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
                it.configuration["asciidoctor.attributes"] = arrayOf(
                    "sourceDir=${project.projectDir.resolve(site.bake.srcPath)}",
                    "imagesDir=diagrams",
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
            project.tasks.register("publishSite") { publishSiteTask ->
                publishSiteTask.run {
                    group = BAKERY_GROUP
                    description = "Publish site online."
                    dependsOn(BAKE_TASK)
                    doFirst { site.createCnameFile(project) }
                    doLast {
                        pushPages(
                            destPath = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.bake.destDirPath}" },
                            pathTo = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.pushPage.to}" },
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

            project.tasks.register("initConfig") { task ->
                task.run {
                    group = BAKERY_GROUP
                    description = "Initialize configuration."
                }
            }
        }
    }
}

/*
package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.Console
import java.io.File


@Suppress("unused")
class BakeryPlugin : Plugin<Project> {
    companion object {
        private const val BAKERY_GROUP = "bakery"
        private const val BAKE_TASK = "bake"
        private const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        private const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"
    }

    override fun apply(project: Project) {

        project.plugins.apply(JBakePlugin::class.java)
        val extension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        project.afterEvaluate {
            val site = from(project, extension.configPath.get())

            project.extensions.configure(JBakeExtension::class.java) {
                it.srcDirName = site.bake.srcPath
                it.destDirName = site.bake.destDirPath
                it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
                it.configuration["asciidoctor.attributes"] = arrayOf(
                    "sourceDir=${project.projectDir.resolve(site.bake.srcPath)}",
                    "imagesDir=diagrams",
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

            // Tâche de configuration GitHub
            project.tasks.register("configureGitHub") { task ->
                task.run {
                    group = BAKERY_GROUP
                    description = "Configure GitHub credentials and repository"

                    val githubToken = project.objects.property(String::class.java)
                    val githubUsername = project.objects.property(String::class.java)
                    val githubRepo = project.objects.property(String::class.java)
                    val noInteractive = project.objects.property(Boolean::class.java).apply {
                        convention(false)
                    }

                    doLast {
                        val token = getOrPrompt(
                            property = githubToken,
                            propertyName = "GitHub Token",
                            sensitive = true,
                            noInteractive = noInteractive.get(),
                            logger = project.logger
                        )

                        val username = getOrPrompt(
                            property = githubUsername,
                            propertyName = "GitHub Username",
                            sensitive = false,
                            noInteractive = noInteractive.get(),
                            logger = project.logger
                        )

                        val repo = getOrPrompt(
                            property = githubRepo,
                            propertyName = "GitHub Repository URL",
                            sensitive = false,
                            noInteractive = noInteractive.get(),
                            logger = project.logger,
                            example = "https://github.com/username/repo.git"
                        )

                        project.logger.lifecycle("✓ GitHub configuration completed:")
                        project.logger.lifecycle("  Username: $username")
                        project.logger.lifecycle("  Repository: $repo")
                        project.logger.lifecycle("  Token: ${if (token.isNotEmpty()) "***configured***" else "not set"}")

                        saveGitHubConfiguration(project, token, username, repo)
                    }
                }
            }

            project.tasks.register("publishSite") { publishSiteTask ->
                publishSiteTask.run {
                    group = BAKERY_GROUP
                    description = "Publish site online."
                    dependsOn(BAKE_TASK)
                    doFirst { site.createCnameFile(project) }
//                    doLast {
//                        pushPages(
//                            destPath = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.bake.destDirPath}" },
//                            pathTo = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.pushPage.to}" },
//                            site.pushPage,
//                            project.logger
//                        )
//                    }
                }
            }

            project.tasks.register("publishMaquette") {
                it.run {
                    group = BAKERY_GROUP
                    description = "Publish maquette online."
                    val uiDir: File = project
                        .layout.projectDirectory.asFile
                        .resolve(site.pushMaquette.from)
                    val uiBuildDir: File = project
                        .layout.buildDirectory.asFile.get()
                        .resolve(site.pushMaquette.from)
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
        }
    }

    private fun getOrPrompt(
        property: Property<String>,
        propertyName: String,
        sensitive: Boolean = false,
        noInteractive: Boolean = false,
        logger: org.gradle.api.logging.Logger,
        example: String? = null
    ): String {
        // Si la propriété est déjà fournie via argument CLI ou gradle.properties
        if (property.isPresent && property.get().isNotBlank()) {
            return property.get()
        }

        // Si mode non-interactif, échouer
        if (noInteractive) {
            throw IllegalArgumentException(
                "Missing required parameter: $propertyName. " +
                "Provide -P${propertyName.replace(" ", "")} or via command line, " +
                "or remove --no-interactive flag."
            )
        }

        // Sinon, demander interactivement
        return promptUser(propertyName, sensitive, example, logger)
    }

    private fun promptUser(
        propertyName: String,
        sensitive: Boolean,
        example: String?,
        logger: org.gradle.api.logging.Logger
    ): String {
        val console: Console? = System.console()

        return if (console != null) {
            if (sensitive) {
                promptSensitive(console, propertyName, logger)
            } else {
                promptNormal(console, propertyName, example, logger)
            }
        } else {
            promptFallback(propertyName, sensitive, example, logger)
        }
    }

    private fun promptSensitive(
        console: Console,
        propertyName: String,
        logger: org.gradle.api.logging.Logger
    ): String {
        var input: CharArray?
        do {
            print("Enter $propertyName (hidden): ")
            input = console.readPassword()
            if (input == null || input.isEmpty()) {
                logger.warn("$propertyName cannot be empty. Please try again.")
            }
        } while (input == null || input.isEmpty())

        return String(input).also {
            input.fill('0')
        }
    }

    private fun promptNormal(
        console: Console,
        propertyName: String,
        example: String?,
        logger: org.gradle.api.logging.Logger
    ): String {
        val exampleText = example?.let { " (e.g., $it)" } ?: ""
        var input: String?
        do {
            print("Enter $propertyName$exampleText: ")
            input = console.readLine()
            if (input.isNullOrBlank()) {
                logger.warn("$propertyName cannot be empty. Please try again.")
            }
        } while (input.isNullOrBlank())

        return input
    }

    private fun promptFallback(
        propertyName: String,
        sensitive: Boolean,
        example: String?,
        logger: org.gradle.api.logging.Logger
    ): String {
        val exampleText = example?.let { " (e.g., $it)" } ?: ""
        val sensitiveNote = if (sensitive) " (will be visible)" else ""

        logger.lifecycle("Console not available. Using standard input.")
        print("Enter $propertyName$exampleText$sensitiveNote: ")

        var input: String?
        do {
            input = readLine()
            if (input.isNullOrBlank()) {
                logger.warn("$propertyName cannot be empty. Please try again.")
                print("Enter $propertyName$exampleText: ")
            }
        } while (input.isNullOrBlank())

        return input
    }

    private fun saveGitHubConfiguration(
        project: Project,
        token: String,
        username: String,
        repo: String
    ) {
        val configFile = project.rootProject.file(".github-config")
        configFile.writeText("""
            |# GitHub Configuration
            |# DO NOT COMMIT THIS FILE - Add it to .gitignore
            |github.username=$username
            |github.repo=$repo
            |github.token=$token
        """.trimMargin())

        project.logger.lifecycle("Configuration saved to ${configFile.absolutePath}")
        project.logger.warn("⚠ Don't forget to add .github-config to your .gitignore!")

        val gitignore = project.rootProject.file(".gitignore")
        if (gitignore.exists()) {
            val content = gitignore.readText()
            if (!content.contains(".github-config")) {
                project.logger.warn("⚠ .github-config is not in your .gitignore file!")
            }
        }
    }
}
*/