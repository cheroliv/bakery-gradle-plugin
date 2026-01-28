package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.FileSystemManager.yamlMapper
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
import java.util.jar.JarFile


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
                "config file does not exists."
                    .apply(::println)
                    .let(project.logger::info)
                project.tasks.register("initSite") { publishSiteTask ->
                    publishSiteTask.run {
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
                        val site = SiteConfiguration(BakeConfiguration("site", "bake", ""))
                        site.run(yamlMapper::writeValueAsString)
                            .run(configFile::writeText)
                            .also {
                                "write config file."
                                    .apply(::println)
                                    .let(project.logger::info)
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


//        project.tasks.register("configureSite") { task ->
//            task.run {
//                group = BAKERY_GROUP
//                description = "Initialize Bakery configuration."
//
//                doLast {
//                    val token = getOrPrompt(
//                        project = project,
//                        propertyName = "GitHub Token",
//                        cliProperty = "githubToken",
//                        sensitive = true
//                    )
//
//                    val username = getOrPrompt(
//                        project = project,
//                        propertyName = "GitHub Username",
//                        cliProperty = "githubUsername",
//                        sensitive = false
//                    )
//
//                    val repo = getOrPrompt(
//                        project = project,
//                        propertyName = "GitHub Repository URL",
//                        cliProperty = "githubRepo",
//                        sensitive = false,
//                        example = "https://github.com/username/repo.git"
//                    )
//
//                    val configPath = getOrPrompt(
//                        project = project,
//                        propertyName = "Config Path",
//                        cliProperty = "configPath",
//                        sensitive = false,
//                        example = "config/bakery.yml",
//                        default = "config/bakery.yml"
//                    )
//
//                    project.logger.lifecycle("✓ Bakery configuration completed:")
//                    project.logger.lifecycle("  Username: $username")
//                    project.logger.lifecycle("  Repository: $repo")
//                    project.logger.lifecycle("  Config Path: $configPath")
//                    project.logger.lifecycle("  Token: ${if (token.isNotEmpty()) "***configured***" else "not set"}")
//
//                    saveConfiguration(project, token, username, repo, configPath)
//
//                    project.logger.lifecycle("")
//                    project.logger.lifecycle("✓ Configuration saved successfully!")
//                    project.logger.lifecycle("  You can now run: ./gradlew bake")
//                }
//            }
//        }

    }

    /**
     * Copie un répertoire de ressources depuis le plugin (JAR ou filesystem) vers un dossier cible
     *
     * @param resourcePath Le chemin de la ressource dans le plugin (ex: "site")
     * @param targetDir Le dossier de destination
     * @param project Le projet Gradle (pour le logging)
     */
    private fun copyResourceDirectory(resourcePath: String, targetDir: File, project: Project) {
        val classLoader = this::class.java.classLoader
        val resource = classLoader.getResource(resourcePath)

        project.logger.info("Attempting to copy resource: $resourcePath")
        project.logger.info("Resource URL: $resource")

        when {
            resource == null -> {
                val errorMsg = "Resource directory not found: $resourcePath"
                project.logger.error(errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            resource.protocol == "jar" -> {
                project.logger.info("Copying from JAR...")
                copyFromJar(resourcePath, targetDir, project)
            }

            resource.protocol == "file" -> {
                project.logger.info("Copying from file system...")
                copyFromFileSystem(resourcePath, targetDir, project)
            }

            else -> {
                val errorMsg = "Unsupported resource protocol: ${resource.protocol}"
                project.logger.error(errorMsg)
                throw IllegalArgumentException(errorMsg)
            }
        }
    }

    /**
     * Copie depuis un JAR
     */
    private fun copyFromJar(resourcePath: String, targetDir: File, project: Project) {
        try {
            // Obtenir le chemin du JAR du plugin
            val jarUrl = this::class.java.protectionDomain.codeSource.location
            project.logger.info("JAR URL: $jarUrl")

            JarFile(File(jarUrl.toURI())).use { jar ->
                val normalizedPath = resourcePath.removeSuffix("/") + "/"
                var copiedCount = 0

                jar.entries().asSequence()
                    .filter { entry ->
                        entry.name.startsWith(normalizedPath) &&
                                !entry.isDirectory &&
                                entry.name != normalizedPath
                    }
                    .forEach { entry ->
                        val relativePath = entry.name.removePrefix(normalizedPath)
                        val targetFile = targetDir.resolve(relativePath)

                        project.logger.info("Copying: ${entry.name} -> ${targetFile.absolutePath}")

                        targetFile.parentFile.mkdirs()

                        jar.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        copiedCount++
                    }

                project.logger.lifecycle("✓ Copied $copiedCount files from $resourcePath to ${targetDir.absolutePath}")
            }
        } catch (e: Exception) {
            project.logger.error("Error copying from JAR: ${e.message}", e)
            throw e
        }
    }

    /**
     * Copie depuis le système de fichiers (mode développement)
     */
    private fun copyFromFileSystem(resourcePath: String, targetDir: File, project: Project) {
        try {
            val classLoader = this::class.java.classLoader
            val resource = classLoader.getResource(resourcePath)
                ?: throw IllegalArgumentException("Resource not found: $resourcePath")

            val sourceDir = File(resource.toURI())
            val destDir = targetDir.resolve(resourcePath)

            project.logger.info("Source: ${sourceDir.absolutePath}")
            project.logger.info("Destination: ${destDir.absolutePath}")

            if (!sourceDir.exists()) {
                throw IllegalArgumentException("Source directory does not exist: ${sourceDir.absolutePath}")
            }

            if (!sourceDir.isDirectory) {
                throw IllegalArgumentException("Source is not a directory: ${sourceDir.absolutePath}")
            }

            destDir.parentFile.mkdirs()

            val copiedCount = sourceDir.walkTopDown()
                .filter { it.isFile }
                .count { sourceFile ->
                    val relativePath = sourceFile.relativeTo(sourceDir).path
                    val targetFile = destDir.resolve(relativePath)

                    project.logger.info("Copying: ${sourceFile.absolutePath} -> ${targetFile.absolutePath}")

                    targetFile.parentFile.mkdirs()
                    sourceFile.copyTo(targetFile, overwrite = true)
                    true
                }

            project.logger.lifecycle("✓ Copied $copiedCount files from $resourcePath to ${destDir.absolutePath}")
        } catch (e: Exception) {
            project.logger.error("Error copying from file system: ${e.message}", e)
            throw e
        }
    }
}
