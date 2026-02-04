package com.cheroliv.bakery.scenarios


import com.cheroliv.bakery.createConfigFile
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner.create
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.createTempFile

class TestWorld {
    val log: Logger = getLogger(TestWorld::class.java)

    // Scope de coroutines pour le scénario
    val scope = CoroutineScope(Default + SupervisorJob())

    // État partagé entre les steps
    var projectDir: File? = null
    var buildResult: BuildResult? = null
    var exception: Throwable? = null

    // Jobs asynchrones en cours
    private val asyncJobs = mutableListOf<Deferred<*>>()

    /**
     * Exécute une tâche Gradle de manière asynchrone
     */
    fun executeGradleAsync(vararg tasks: String): Deferred<BuildResult> {
        require(projectDir != null) { "Project directory must be initialized" }

        log.info("Starting async Gradle execution: ${tasks.joinToString(" ")}")

        return scope.async {
            try {
                create()
                    .withProjectDir(projectDir!!)
                    .withArguments(tasks.toList() + "--stacktrace")
                    .withPluginClasspath()
                    .build()
            } catch (e: Exception) {
                log.error("Gradle build failed", e)
                exception = e
                throw e
            }
        }.also { asyncJobs.add(it) }
    }

    /**
     * Exécute une tâche Gradle de manière synchrone
     */
    suspend fun executeGradle(vararg tasks: String): BuildResult = executeGradleAsync(*tasks)
        .await()
        .also { buildResult = it }

    /**
     * Exécute une action avec un timeout
     */
    suspend fun <T> withTimeout(seconds: Long, block: suspend () -> T): T = withTimeout(seconds * 1000) {
        block()
    }

    /**
     * Attend la fin de toutes les opérations asynchrones
     */
    suspend fun awaitAll() {
        if (asyncJobs.isNotEmpty()) {
            log.info("Waiting for ${asyncJobs.size} async operations...")
            asyncJobs.awaitAll()
            log.info("All async operations completed")
        }
    }

    /**
     * Nettoyage des ressources
     */
    fun cleanup() {
        scope.cancel()
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
        exception = null
        asyncJobs.clear()
    }

    /**
     * Crée un projet Gradle de test
     */
    fun createGradleProject(
        pluginId: String = "com.cheroliv.bakery",
        buildScriptContent: String = "bakery { configPath = file(\"site.yml\").absolutePath }"
    ): File {
        val tempDir = createTempFile("gradle-test-", "").apply {
            delete()
            mkdirs()
        }

        tempDir
            .resolve("settings.gradle.kts")
            .apply { createNewFile() }
            .writeText(
                "pluginManagement.repositories.gradlePluginPortal()\n" +
                        "rootProject.name = \"${tempDir.name}\""
            )

        tempDir
            .resolve("build.gradle.kts")
            .apply { createNewFile() }
            .writeText("plugins { id(\"$pluginId\") }\n$buildScriptContent")

        tempDir.createConfigFile()
        projectDir = tempDir
        return tempDir
    }

}

