package com.cheroliv.bakery

import com.cheroliv.bakery.ConfigPrompts.getOrPrompt
import com.cheroliv.bakery.ConfigPrompts.saveConfiguration
import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
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


class BakeryPlugin : Plugin<Project> {
    companion object {
        const val BAKERY_GROUP = "bakery"
        const val BAKE_TASK = "bake"
        const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"

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
            // Only configureSite is available
            if (!project.layout.projectDirectory.asFile.resolve(bakeryExtension.configPath.get()).exists()) {
                "config file does not exists"
                    .apply(::println)
                    .let(project.logger::info)
            } else {
                val site = from(project, bakeryExtension.configPath.get())
                project.plugins.apply(JBakePlugin::class.java)
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
                                destPath = { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.bake.destDirPath}" },
                                pathTo = { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.pushPage.to}" },
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
            }
        }



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
                        example = "config/bakery.yml",
                        default = "config/bakery.yml"
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

/*
package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.Console
import java.io.File
import java.io.File.separator


class BakeryPlugin : Plugin<Project> {
    companion object {
        const val BAKERY_GROUP = "bakery"
        const val BAKE_TASK = "bake"
        const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"

        @Suppress("unused")
        const val CNAME = "CNAME"
    }

    override fun apply(project: Project) {

        val extension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        project.afterEvaluate {
            // If site.yml does not exist then jbakeExtension is not configured,
            // publishSite and publishMaquette not registered.
            // Only configureSite is available
            if (!project.layout.projectDirectory.asFile.resolve(extension.configPath.get()).exists()) {
                "config file does not exists"
                    .apply(::println)
                    .let(project.logger::info)
            } else {
                project.plugins.apply(JBakePlugin::class.java)

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
                                destPath = { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.bake.destDirPath}" },
                                pathTo = { "${project.layout.buildDirectory.get().asFile.absolutePath}$separator${site.pushPage.to}" },
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

                // Enregistrement de la tâche serve
                project.tasks.register("serve", JavaExec::class.java) { task ->
                    task.run {
                        group = BAKERY_GROUP
                        description = "Serves the baked site locally."
                        mainClass.set("org.jbake.launcher.Main")
                        classpath = jbakeRuntime
                        environment(
                            "GEM_PATH",
                            jbakeRuntime.asPath
                        )
                        jvmArgs(
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            "--add-opens=java.base/java.util=ALL-UNNAMED",
                            "--add-opens=java.base/java.io=ALL-UNNAMED",
                            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                        )

                        args = listOf(
                            "-b",
                            project.file(site.bake.srcPath).absolutePath,
                            "-s",
                            project.layout.buildDirectory.get()
                                .asFile.resolve(site.bake.destDirPath)
                                .absolutePath,
                        )

                        doFirst {
                            println("Serving $group at: https://localhost:8820/")
                        }
                    }
                }
            }
        }

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
                        example = "config/bakery.yml",
                        default = "config/bakery.yml"
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

    fun getOrPrompt(
        project: Project,
        propertyName: String,
        cliProperty: String,
        sensitive: Boolean = false,
        example: String? = null,
        default: String? = null
    ): String {
        // 1. Vérifier les propriétés du projet (-P)
        if (project.hasProperty(cliProperty)) {
            val value = project.property(cliProperty) as String
            if (value.isNotBlank()) return value
        }

        // 2. Vérifier les variables d'environnement
        val envVar = cliProperty.uppercase().replace(Regex("([a-z])([A-Z])"), "$1_$2")
        System.getenv(envVar)?.takeIf { it.isNotBlank() }?.let { return it }

        // 3. Utiliser la valeur par défaut si fournie
        default?.let { return it }

        // 4. Demander interactivement
        return promptUser(propertyName, sensitive, example, project.logger)
    }

    fun promptUser(
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
            input = readlnOrNull()
            if (input.isNullOrBlank()) {
                logger.warn("$propertyName cannot be empty. Please try again.")
                print("Enter $propertyName$exampleText: ")
            }
        } while (input.isNullOrBlank())

        return input
    }

    private fun saveConfiguration(
        project: Project,
        token: String,
        username: String,
        repo: String,
        configPath: String
    ) {
        //TODO: changer ca en sauvegarder ou update site.yml
        // Sauvegarder les credentials GitHub
        val githubConfigFile = project.rootProject.file(".github-config")
        githubConfigFile.writeText(
            """
            |# GitHub Configuration
            |# DO NOT COMMIT THIS FILE - Add it to .gitignore
            |github.username=$username
            |github.repo=$repo
            |github.token=$token
        """.trimMargin()
        )

        // Sauvegarder le chemin de configuration dans gradle.properties
        val gradlePropertiesFile = project.rootProject.file("gradle.properties")
        val properties = if (gradlePropertiesFile.exists()) {
            gradlePropertiesFile.readLines().toMutableList()
        } else {
            mutableListOf()
        }

        // Retirer l'ancienne ligne configPath si elle existe
        properties.removeIf { it.startsWith("bakery.configPath=") }
        properties.add("bakery.configPath=$configPath")

        gradlePropertiesFile.writeText(properties.joinToString("\n"))

        project.logger.lifecycle("Configuration saved to:")
        project.logger.lifecycle("  - ${githubConfigFile.absolutePath}")
        project.logger.lifecycle("  - ${gradlePropertiesFile.absolutePath}")
        project.logger.warn("")
        project.logger.warn("⚠️ IMPORTANT SECURITY NOTES:")
        project.logger.warn("  • Add .github-config to your .gitignore")
        project.logger.warn("  • Never commit your GitHub token")

        // Vérifier .gitignore
        val gitignore = project.rootProject.file(".gitignore")
        if (gitignore.exists()) {
            val content = gitignore.readText()
            if (!content.contains(".github-config")) {
                project.logger.warn("  • .github-config is NOT in your .gitignore!")
            }
        } else {
            project.logger.warn("  • No .gitignore file found!")
        }
    }
}
* */