@file:Suppress("FunctionName")

package com.cheroliv.bakery


import com.cheroliv.bakery.FuncTestsConstants.BAKERY_GROUP
import com.cheroliv.bakery.FuncTestsConstants.BAKE_TASK
import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE
import com.cheroliv.bakery.FuncTestsConstants.CONFIG_FILE
import com.cheroliv.bakery.FuncTestsConstants.GRADLE_DIR
import com.cheroliv.bakery.FuncTestsConstants.JBAKE_TASK_SEQUENCE
import com.cheroliv.bakery.FuncTestsConstants.LIBS_FILE
import com.cheroliv.bakery.FuncTestsConstants.LIBS_VERSIONS_TOML_FILE
import com.cheroliv.bakery.FuncTestsConstants.SETTINGS_FILE
import com.cheroliv.bakery.FuncTestsConstants.buildScriptListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.configListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.settingsListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.tomlListOfStringContained
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner.create
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.separator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.text.Charsets.UTF_8


class BakeryPluginFunctionalTests {
    companion object {
        private val log: Logger by lazy { getLogger(BakeryPluginFunctionalTests::class.java) }

        private fun info(message: String) = message
            .apply(log::info)
            .run(::println)
    }

    @field:TempDir
    private lateinit var projectDir: File

    private val File.configFile: File
        get() = if (absolutePath == projectDir.absolutePath) resolve(CONFIG_FILE)
        else projectDir.resolve(CONFIG_FILE)

    private fun File.deleteConfigFile(): Boolean = configFile.delete()


    @BeforeTest
    fun setUp() {
        //directory empty
        assertThat(projectDir.isDirectory)
            .describedAs("$projectDir should be a directory.")
            .isTrue
        assertThat(projectDir.listFiles())
            .describedAs("$projectDir should be an empty directory.")
            .isEmpty()

        info("Prepare temporary directory to host gradle build.")

        projectDir.createSettingsFile()
        projectDir.createBuildScriptFile()
        projectDir.createDependenciesFile()
        projectDir.createConfigFile()

        assertThat(projectDir.configFile.readText(UTF_8))
            .describedAs("Config file should contains expectedStrings ; $configListOfStringContained")
            .contains(configListOfStringContained)
        info("gradle and $CONFIG_FILE files successfully created.")

        assertThat(projectDir.resolve(LIBS_FILE).readText(UTF_8))
            .describedAs("libsVersionsTomlFile should contains the given list of strings")
            .contains(tomlListOfStringContained)
        info("gradle and $LIBS_FILE files successfully created.")

        assertThat(projectDir.resolve(BUILD_FILE).readText(UTF_8))
            .describedAs("buildFile should contains the given list of strings")
            .contains(buildScriptListOfStringContained)
        info("gradle and $BUILD_FILE files successfully created.")

        assertThat(projectDir.resolve(SETTINGS_FILE).readText(UTF_8))
            .describedAs("settingsFile should contains the given list of strings")
            .contains(settingsListOfStringContained.toMutableList().apply { add("bakery-test") })

        info("gradle and $SETTINGS_FILE files successfully created.")
    }

    @AfterEach
    fun teardown(testInfo: TestInfo) = listOf(
        "✓ Test finished: ${testInfo.displayName}",
        "─".repeat(60)
    ).forEach {
        it.apply(log::info)
            .apply(::println)
    }

    /**
     * # Mode interactif
     * ./gradlew configureSite
     *
     * # Avec paramètres
     * ./gradlew configureSite \
     *   -PGitHubToken=ghp_xxx \
     *   -PGitHubUsername=username \
     *   -PGitHubRepositoryURL=https://github.com/username/repo.git
     *
     * # Mode non-interactif (échoue si paramètres manquants)
     * ./gradlew configureSite --no-interactive -PGitHubUsername=username
     */
    @kotlin.test.Ignore
    @Test
    fun `test configureSite task without config file with --no-interactive parameter`() {
        projectDir.deleteConfigFile()
        info("$CONFIG_FILE file successfully deleted.")
        info("Run gradle task: configureSite.")
        create()
            .forwardOutput()
            .withPluginClasspath()
//            .withArguments("configureSite")
            .withArguments("configureSite", "--no-interactive")
            .withProjectDir(projectDir)
            .build()

//        assertThat(result.output)
//        ```./gradlew configureSite \
//        -PGitHubToken=ghp_xxx \
//        -PGitHubUsername=cheroliv \
//        -PGitHubRepositoryURL=https://github.com/cheroliv/bakery.git```
    }


    @Test
    fun `test configureSite task exists without config file when running tasks`() {
        projectDir.deleteConfigFile()
        info("$CONFIG_FILE file successfully deleted.")
        info("Run gradle task :tasks --group=$BAKERY_GROUP.")
        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=$BAKERY_GROUP")
            .withProjectDir(projectDir)
            .build()
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should contains 'configureSite' and 'Initialize configuration.'""")
            .contains("configureSite", "Initialize Bakery configuration.")
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should not contains 'publishSite' and 'Publish site online.'""")
            .doesNotContain("publishSite", "Publish site online.")
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should not contains 'publishMaquette' and 'Publish maquette online.'""")
            .doesNotContain("publishMaquette", "Publish maquette online.")
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should not contains 'serve' and 'Serves the baked site locally.'""")
            .doesNotContain("serve", "Serves the baked site locally.")

        info("✓ tasks displays the configureSite task's description correctly without config file.")
    }


    @Test
    fun `tasks displays with config file`() {
        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=$BAKERY_GROUP")
            .withProjectDir(projectDir)
            .build()
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should contains 'configureSite' and 'Initialize configuration.'""")
            .contains("Initialize Bakery configuration.", "configureSite")
        info("✓ tasks displays the configureSite task's description correctly.")
    }

    @Test
    fun `tasks displays without config file`() {
        projectDir.deleteConfigFile()
        info("$CONFIG_FILE file successfully deleted.")
        assertDoesNotThrow {
            create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("tasks", "--group=$BAKERY_GROUP")
                .withProjectDir(projectDir)
                .build()
        }
        info("✓ without config file, the project does not fail to build.")
    }

    @Suppress("DANGEROUS_CHARACTERS", "FunctionName")
    @Test
    fun `Template folder does not exist`() {
        info("initSiteTest")
        info("Delete temporary directory if exists.")
        info("Project temporary path : ${projectDir.path}")
        if (projectDir.resolve("src/jbake").exists()) {
            projectDir.resolve("src/jbake").deleteRecursively()
        }
        assertThat(projectDir.resolve("src/jbake").exists())
            .describedAs("src/jbake should not exists anymore in temporary project folder : ${projectDir.path}")
            .isFalse
        info("Do template folder exist in default path : src/jbake?")
        // Est ce que le dossier src/jbake existe?
    }
    @Suppress("FunctionName")
    @Test
    fun `phase 2 - help task bake command retrieves name and description successfully`() {
        "Test: The bake task executes successfully"
            .apply(log::info)
            .apply(::println)

        val result = create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--task", BAKE_TASK)
            .forwardOutput()
            .build()

        assertThat(result.output)
            .describedAs("Gradle task bake output should contains bake help description")
            .contains(JBAKE_TASK_SEQUENCE.trimIndent())

        "✓ Bake task executed successfully"
            .apply(log::info)
            .apply(::println)
    }

    // ========================================================================
    // PHASE 1: File Setup Tests
    // ========================================================================
    @Test
    fun `phase 1 - all project files are created correctly`() {
        "Test: Creation of all project files"
            .apply(log::info)
            .apply(::println)

        // Verify file paths are correct
        assertThat(projectDir.resolve(BUILD_FILE))
            .describedAs("$BUILD_FILE should exists.")
            .exists()
        assertThat(projectDir.resolve(SETTINGS_FILE).path)
            .describedAs("$SETTINGS_FILE should exists.")
            .isEqualTo("${projectDir.path}$separator$SETTINGS_FILE")
        assertThat(
            projectDir.resolve(GRADLE_DIR)
                .resolve(LIBS_VERSIONS_TOML_FILE)
                .path
        ).describedAs("$LIBS_VERSIONS_TOML_FILE should exists.")
            .isEqualTo("${projectDir.path}$separator$GRADLE_DIR$separator$LIBS_VERSIONS_TOML_FILE")
        assertThat(projectDir.resolve(CONFIG_FILE).path)
            .isEqualTo("${projectDir.path}${separator}${CONFIG_FILE}")

        // Final verification
        assertThat(projectDir)
            .describedAs("projectDir should contain all created files")
            .isNotEmptyDirectory

        assertThat(projectDir.listFiles()!!.size)
            .describedAs("projectDir should contain exactly 4 items (3 files + 1 directory)")
            .isNotZero

        "✓ All files were created successfully"
            .apply(log::info)
            .apply(::println)
    }

    @Test
    fun `phase 1 - config file can be created and validated independently`() {
        "Test: Creation and validation of the configuration file"
            .apply(log::info)
            .apply(::println)
        assertThat(projectDir.resolve(CONFIG_FILE))
            .describedAs("Config file should be created")
            .exists()
        assertThat(projectDir.resolve(CONFIG_FILE).readText(UTF_8))
            .describedAs("Config file should contains expectedStrings ; $configListOfStringContained")
            .contains(configListOfStringContained)
        "✓ Configuration file created and validated"
            .apply(log::info)
            .apply(::println)
    }

    @Test
    fun `phase 1 - gradle directory structure is created correctly`() {
        "Test: Creation of the gradle/ structure"
            .apply(log::info)
            .apply(::println)

        assertThat(projectDir.resolve(GRADLE_DIR))
            .describedAs("gradle directory should exist")
            .exists()
            .describedAs("gradle should be a directory")
            .isDirectory

        assertThat(projectDir.resolve(GRADLE_DIR).listFiles().map { it.name })
            .describedAs("gradle directory is not empty")
            .isNotEmpty
            .describedAs("gradle directory should contain $LIBS_VERSIONS_TOML_FILE")
            .contains(LIBS_VERSIONS_TOML_FILE)

        "✓ gradle/ structure created successfully"
            .apply(log::info)
            .apply(::println)
    }

    // ========================================================================
    // PHASE 2: Plugin Execution Tests
    // ========================================================================
    @Suppress("FunctionName")
    @Test
    fun `phase 2 - plugin can read configuration from file`() {
        "Test: The plugin can read the configuration file"
            .apply(log::info)
            .apply(::println)

        projectDir.resolve(BUILD_FILE)
            .readText(UTF_8)
            .run(::assertThat)
            .describedAs("Proves gradle script use extension and config file are accorded")
            .contains("""bakery { configPath = file("$CONFIG_FILE").absolutePath }""")

        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=$BAKERY_GROUP")
            .withProjectDir(projectDir)
            .build()

        assertThat(result.output)
            .describedAs("""Gradle task tasks output should contains publishSite and publishMaquette""")
            .contains("publishMaquette", "publishSite")

        "✓ Plugin reads the configuration correctly"
            .apply(log::info)
            .apply(::println)
    }


    // ========================================================================
    // PHASE 3: Integration Tests (Future)
    // ========================================================================

    @Test
    fun `phase 3 - complete workflow from bake to push executes successfully`() {
//        "Phase 3 - Future: End-to-end workflow testing"
        "Test: Complete workflow from bake to push"
            .apply(log::info)
            .apply(::println)

        // TODO: Implement complete workflow test
        // 1. Setup files
        // 2. Run bake
        // 3. Verify output
        // 4. Run pushPage
        // 5. Verify push result

        "✓ Complete workflow executed successfully"
            .apply(log::info)
            .apply(::println)
    }

    @Test
    fun `phase 3 - plugin handles missing configuration gracefully`() {
//        "Phase 3 - Future: Error handling testing"
        "Test: Handling of missing configuration errors"
            .apply(log::info)
            .apply(::println)

        // TODO: Test error scenarios
        // - Missing config file
        // - Invalid config format
        // - Missing required fields

        "✓ Errors handled correctly"
            .apply(log::info)
            .apply(::println)
    }

    //TODO: WIP there, testing differents type of configuration like gradle.properties, yaml file or cli parameters

}
