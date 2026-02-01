package com.cheroliv.bakery

import com.cheroliv.bakery.GitService.FileOperationResult
import com.cheroliv.bakery.GitService.FileOperationResult.Failure
import com.cheroliv.bakery.GitService.FileOperationResult.Success
import com.cheroliv.bakery.RepositoryConfiguration.Companion.CNAME
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.jar.JarFile
import kotlin.text.Charsets.UTF_8


object FileSystemManager {
    val String.isYmlUri: Boolean
        get() = runCatching {
            val uri = URI(this)
            // On vérifie que le chemin de l'URI (le "path") finit par .yml
            // On utilise ignoreCase = true pour gérer .YML
            uri.path?.endsWith(".yml", ignoreCase = true) ?: false
        }.getOrDefault(false)

    /**
     * Copie un répertoire de ressources depuis le plugin (JAR ou filesystem) vers un dossier cible
     *
     * @param resourcePath Le chemin de la ressource dans le plugin (ex: "site")
     * @param targetDir Le dossier de destination
     * @param project Le projet Gradle (pour le logging)
     */
    fun copyResourceDirectory(resourcePath: String, targetDir: File, project: Project) {
        val classLoader = BakeryPlugin::class.java.classLoader
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
            val jarUrl = BakeryPlugin::class.java.protectionDomain.codeSource.location
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

                        @Suppress("LoggingSimilarMessage")
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
            val classLoader = BakeryPlugin::class.java.classLoader
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

    // Publishing logic
    fun createRepoDir(path: String, logger: Logger): File = path.let(::File).apply {
        if (exists() && !isDirectory) if (delete()) logger.info("$name exists as file and successfully deleted.")
        else throw "$name exists and must be a directory".run(::IOException)

        if (exists()) if (deleteRecursively()) logger.info("$name exists as directory and successfully deleted.")
        else throw "$name exists as a directory and cannot be deleted".run(::IOException)

        if (!exists()) logger.info("$name does not exist.")
        else throw IOException("$name must not exist anymore.")

        if (!exists()) {
            if (mkdir()) logger.info("$name as directory successfully created.")
            else throw IOException("$name as directory cannot be created.")
        }
    }

    fun copyBakedFilesToRepo(
        bakeDirPath: String, repoDir: File, logger: Logger
    ): FileOperationResult = try {
        bakeDirPath.also { "bakeDirPath : $it".let(logger::info) }.let(::File).apply {
            copyRecursively(repoDir, true)
            deleteRecursively()
        }.run {
            if (!exists()) logger.info("$name directory successfully deleted.")
            else throw IOException("$name must not exist.")
        }
        Success
    } catch (e: Exception) {
        Failure(e.message ?: "An error occurred during file copy.")
    }

    val yamlMapper: ObjectMapper
        get() = YAMLFactory()
            .let(::ObjectMapper)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .registerKotlinModule()


    fun SiteConfiguration.createCnameFile(project: Project) {
        project.layout.buildDirectory.get()
            .asFile
            .resolve(bake.destDirPath)
            .resolve(CNAME).run {
                if (exists()) delete()
                if (bake.cname.isNotBlank()) {
                    apply(File::createNewFile)
                        .writeText(bake.cname, UTF_8)
                }
            }
    }


    fun Project.from(configPath: String): SiteConfiguration {
        val configFile = file(configPath)
        return read(configFile)
    }

    fun Project.read(configFile: File): SiteConfiguration = try {
        yamlMapper.readValue(configFile)
    } catch (e: Exception) {
        logger.error("Failed to read site configuration from ${configFile.absolutePath}", e)
        // Return a default/empty configuration to avoid build failure
        SiteConfiguration()
    }
}