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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import java.io.File
import java.io.File.separator
import java.io.IOException
import kotlin.text.Charsets.UTF_8

object FileSystemManager {

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
                when {
                    !exists() -> logger.info("$name directory successfully deleted.")
                    else -> throw IOException("$name must not exist.")
                }
            }
        Success
    } catch (e: Exception) {
        Failure(e.message ?: "An error occurred during file copy.")
    }


    fun parseSiteConfiguration(yaml: String): SiteConfiguration =
        ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(yaml)


    val Project.yamlMapper: ObjectMapper
        get() = YAMLFactory().let(::ObjectMapper).disable(WRITE_DATES_AS_TIMESTAMPS).registerKotlinModule()


    fun readSiteConfiguration(project: Project, configFile: File): SiteConfiguration = try {
        project.yamlMapper.readValue(configFile)
    } catch (e: Exception) {
        throw GradleException("Failed to read site configuration from ${configFile.absolutePath}", e)
    }


    fun SiteConfiguration.createCnameFile(project: Project) {
        if (bake.cname != null && bake.cname.isNotBlank()) project.file(
            "${project.layout.buildDirectory.get().asFile.absolutePath}${
                separator
            }${
                bake.destDirPath
            }${separator}${CNAME}"
        ).apply {
            parentFile.mkdirs()
        }.run {
            if (exists() && isDirectory) {
                project.logger.info("$name exists as directory.")
                deleteRecursively()
            } else project.logger.info("$name does not exist as directory.")
            if (exists()) {
                project.logger.info("$name successfully deleted.")
                delete()
            } else project.logger.info("$name does not exist as file.")
            if (exists()) throw "$name file should not exist.".run(::IOException)
            else project.logger.info("$name does not exist as file or directory.")
            if (!createNewFile()) throw "Cannot create $name file.".run(::IOException)
            else project.logger.info("$name successfully created.")
            if (!exists()) throw "$name file should exist.".run(::IOException)
            else project.logger.info("$name file exists.")
            appendText(bake.cname ?: "", UTF_8)
            if (!(exists() && !isDirectory)) throw "$name file should exist as file not as directory.".run(::IOException) else project.logger.info(
                "$name file exists as file and not directory."
            )
        }
    }


    fun from(project: Project, configPath: String): SiteConfiguration {
        val configFile = project.file(configPath)
        return read(project, configFile)
    }

    fun read(
        project: Project, configFile: File
    ): SiteConfiguration = try {
        project.yamlMapper.readValue(configFile)
    } catch (e: Exception) {
        project.logger.error("Failed to read site configuration from ${configFile.absolutePath}", e)
        // Return a default/empty configuration to avoid build failure
        SiteConfiguration(
            BakeConfiguration(srcPath = "", destDirPath = "", cname = null),
            pushPage = GitPushConfiguration(
                from = "", to = "", repo = RepositoryConfiguration(
                    name = "", repository = "", credentials = RepositoryCredentials(username = "", password = "")
                ), branch = "", message = ""
            ),
            pushMaquette = GitPushConfiguration(
                from = "", to = "", repo = RepositoryConfiguration(
                    name = "", repository = "", credentials = RepositoryCredentials("", "")
                ), branch = "", message = ""
            ),
        )
    }
}