package com.cheroliv.bakery

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.Console

object ConfigPrompts {

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
        logger: Logger
    ): String {
        val console: Console? = System.console()

        return if (console != null) {
            if (sensitive) promptSensitive(console, propertyName, logger)
            else promptNormal(console, propertyName, example, logger)
        } else promptFallback(propertyName, sensitive, example, logger)
    }

    private fun promptSensitive(
        console: Console,
        propertyName: String,
        logger: Logger
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
        logger: Logger
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
        logger: Logger
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

    fun saveConfiguration(
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