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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import kotlin.text.Charsets.UTF_8


object FileSystemManager {
    /**
     * Copie le répertoire [resourcePath] trouvé dans le JAR (ou répertoire) contenant [pluginClass]
     * vers [targetDir]. Retourne la liste des fichiers copiés.
     *
     * @param resourcePath ex: "site" (sans slash initial)
     * @param targetDir dossier cible (il sera créé si nécessaire)
     * @param pluginClass une classe qui se trouve dans le JAR du plugin (ex: com.cheroliv.bakery.BakeryPlugin::class.java)
     */
    fun copyResourceDirectoryFromPluginJar(
        resourcePath: String,
        targetDir: File,
        pluginClass: Class<*>
    ): List<Path> {
        require(resourcePath.isNotBlank()) { "resourcePath ne peut pas être vide" }
        val normalized = resourcePath.removePrefix("/").trimEnd('/')
        val prefix = "$normalized/"

        val codeSourceUrl = pluginClass.protectionDomain.codeSource?.location
            ?: throw IllegalStateException("Impossible de déterminer le codeSource pour ${pluginClass.name}")

        val uri = codeSourceUrl.toURI()

        // Si le plugin est en mode développement (répertoire), on copie depuis le FS
        if (Files.isDirectory(Paths.get(uri))) {
            val sourceDir = Paths.get(uri).resolve(normalized)
            if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
                throw IllegalArgumentException("Ressource '$normalized' introuvable sous $sourceDir")
            }
            return copyDirectoryOnFs(sourceDir, targetDir.toPath())
        }

        // Sinon on suppose que codeSource pointe vers un JAR
        val jarPath = Paths.get(uri).toFile()
        JarFile(jarPath).use { jf ->
            val copied = mutableListOf<Path>()
            val entries = jf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!name.startsWith(prefix)) continue
                val rel = name.removePrefix(prefix)
                if (rel.isEmpty()) continue
                val dest = targetDir.toPath().resolve(rel)
                if (entry.isDirectory) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    jf.getInputStream(entry).use { input ->
                        Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
                    }
                    copied.add(dest)
                }
            }
            return copied
        }
    }

    private fun copyDirectoryOnFs(source: Path, target: Path): List<Path> {
        val copied = mutableListOf<Path>()
        Files.walk(source).use { stream ->
            stream.forEach { p ->
                val rel = source.relativize(p)
                val dest = target.resolve(rel.toString())
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING)
                    copied.add(dest)
                }
            }
        }
        return copied
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
        yamlMapper.readValue(yaml)


    val yamlMapper: ObjectMapper
        get() = YAMLFactory()
            .let(::ObjectMapper)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .registerKotlinModule()


    fun SiteConfiguration.createCnameFile(project: Project) {
        val cnameFile: File = project.layout.buildDirectory.get()
            .asFile
            .resolve(bake.destDirPath)
            .resolve(CNAME)
        if (cnameFile.exists()) cnameFile.delete()
        if (!bake.cname.isNullOrBlank()) {
            cnameFile.createNewFile()
            cnameFile.writeText(bake.cname, UTF_8)
        }
    }


    fun from(
        project: Project,
        configPath: String
    ): SiteConfiguration {
        val configFile = project.file(configPath)
        return read(project, configFile)
    }

    fun read(
        project: Project, configFile: File
    ): SiteConfiguration = try {
        yamlMapper.readValue(configFile)
    } catch (e: Exception) {
        project.logger.error("Failed to read site configuration from ${configFile.absolutePath}", e)
        // Return a default/empty configuration to avoid build failure
        SiteConfiguration(
            BakeConfiguration(srcPath = "", destDirPath = "", cname = null),
            pushPage = GitPushConfiguration(
                from = "",
                to = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "",
                    credentials = RepositoryCredentials(username = "", password = "")
                ),
                branch = "",
                message = ""
            ),
            pushMaquette = GitPushConfiguration(
                from = "",
                to = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "",
                    credentials = RepositoryCredentials("", "")
                ),
                branch = "",
                message = ""
            ),
        )
    }
}