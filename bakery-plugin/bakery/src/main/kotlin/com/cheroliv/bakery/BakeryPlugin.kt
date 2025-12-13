package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
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
            // Only initConfig is available
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

            }
        }
        project.tasks.register("initConfig") { task ->
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
        project.logger.warn("⚠️  IMPORTANT SECURITY NOTES:")
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

/*
package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
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
        val extension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        // Tâche initConfig disponible TOUJOURS, même sans configuration
        project.tasks.register("initConfig") { task ->
            task.run {
                group = BAKERY_GROUP
                description = "Initialize Bakery configuration (GitHub credentials and site config)"

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

        project.afterEvaluate {
            // Vérifier si la configuration existe
            val configFile = if (extension.configPath.isPresent) {
                project.file(extension.configPath.get())
            } else {
                project.file("config/bakery.yml")
            }

            if (!configFile.exists()) {
                // Configuration manquante : désactiver les tâches qui nécessitent la config
                disableTasksRequiringConfig(project)
                return@afterEvaluate
            }

            // Configuration présente : initialiser normalement
            try {
                val site = from(project, extension.configPath.get())
                initializeJBakePlugin(project, site)
                registerPublishTasks(project, site)
            } catch (e: Exception) {
                project.logger.error("Failed to load configuration: ${e.message}")
                disableTasksRequiringConfig(project)
            }
        }
    }

    private fun disableTasksRequiringConfig(project: Project) {
        val message = """
            |
            |═══════════════════════════════════════════════════════════════
            |  ⚠️  BAKERY CONFIGURATION MISSING
            |═══════════════════════════════════════════════════════════════
            |
            |  The Bakery plugin requires configuration to run.
            |
            |  To initialize your configuration, run:
            |    ./gradlew initConfig
            |
            |  This will guide you through setting up:
            |    • GitHub credentials
            |    • Repository URL
            |    • Site configuration path
            |
            |═══════════════════════════════════════════════════════════════
            |
        """.trimMargin()

        // Désactiver les tâches qui nécessitent la configuration
        listOf("bake", "publishSite", "publishMaquette").forEach { taskName ->
            project.tasks.register(taskName) { task ->
                task.run {
                    group = BAKERY_GROUP
                    doFirst {
                        project.logger.error(message)
                        throw IllegalStateException(
                            "Configuration file not found. Run './gradlew initConfig' first."
                        )
                    }
                }
            }
        }
    }

    private fun initializeJBakePlugin(project: Project, site: Any) {
        project.plugins.apply(JBakePlugin::class.java)

        project.extensions.configure(JBakeExtension::class.java) {
            val srcPath = site::class.java.getDeclaredField("bake").apply { isAccessible = true }
                .get(site)::class.java.getDeclaredField("srcPath").apply { isAccessible = true }
                .get(site::class.java.getDeclaredField("bake").apply { isAccessible = true }.get(site)) as String

            val destDirPath = site::class.java.getDeclaredField("bake").apply { isAccessible = true }
                .get(site)::class.java.getDeclaredField("destDirPath").apply { isAccessible = true }
                .get(site::class.java.getDeclaredField("bake").apply { isAccessible = true }.get(site)) as String

            it.srcDirName = srcPath
            it.destDirName = destDirPath
            it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
            it.configuration["asciidoctor.attributes"] = arrayOf(
                "sourceDir=${project.projectDir.resolve(srcPath)}",
                "imagesDir=diagrams",
            )
        }

        // Note: Simplification - vous devrez adapter selon votre structure Site
        val srcPath = getSitePath(site, "srcPath")
        val destDirPath = getSitePath(site, "destDirPath")

        project.tasks.withType(JBakeTask::class.java)
            .getByName(BAKE_TASK).apply {
                input = project.file(srcPath)
                output = project.layout.buildDirectory
                    .dir(destDirPath)
                    .get()
                    .asFile
            }
    }

    private fun registerPublishTasks(project: Project, site: Any) {
        project.tasks.register("publishSite") { publishSiteTask ->
                // Votre implémentation existante
        }

        project.tasks.register("publishMaquette") { task ->
                // Votre implémentation existante
        }
    }

    private fun getSitePath(site: Any, fieldName: String): String {
        return try {
            val bakeField = site::class.java.getDeclaredField("bake").apply { isAccessible = true }
            val bake = bakeField.get(site)
            val pathField = bake::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
            pathField.get(bake) as String
        } catch (e: Exception) {
            throw IllegalStateException("Failed to get $fieldName from site configuration", e)
        }
    }

    private fun getOrPrompt(
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

    private fun saveConfiguration(
        project: Project,
        token: String,
        username: String,
        repo: String,
        configPath: String
    ) {
        // Sauvegarder les credentials GitHub
        val githubConfigFile = project.rootProject.file(".github-config")
        githubConfigFile.writeText("""
            |# GitHub Configuration
            |# DO NOT COMMIT THIS FILE - Add it to .gitignore
            |github.username=$username
            |github.repo=$repo
            |github.token=$token
        """.trimMargin())

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
        project.logger.warn("⚠️  IMPORTANT SECURITY NOTES:")
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
*/

///////////////////////////////////

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