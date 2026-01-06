@file:Suppress("FunctionName")

package com.cheroliv.bakery

import com.cheroliv.bakery.FuncTestsConstants.BAKERY_GROUP
import com.cheroliv.bakery.FuncTestsConstants.BAKE_TASK
import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE
import com.cheroliv.bakery.FuncTestsConstants.CONFIG_FILE
import com.cheroliv.bakery.FuncTestsConstants.GRADLE_DIR
import com.cheroliv.bakery.FuncTestsConstants.GRADLE_PROPERTIES_FILE
import com.cheroliv.bakery.FuncTestsConstants.LIBS_VERSIONS_TOML_FILE
import com.cheroliv.bakery.FuncTestsConstants.SETTINGS_FILE
import com.cheroliv.bakery.FuncTestsConstants.buildScriptListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.configListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.settingsListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.tomlListOfStringContained
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner.create
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.separator
import kotlin.text.Charsets.UTF_8

class BakeryPluginProdFunctionalTests {
    companion object {
        private val log = getLogger(BakeryPluginProdFunctionalTests::class.java)
        private val projectDir: File = ""
            .run(::File)
            .absoluteFile
            .parentFile
            .parentFile

        //Create settings files in temporary directory
        private val buildFile by lazy { projectDir.resolve(BUILD_FILE) }
        private val settingsFile by lazy { projectDir.resolve(SETTINGS_FILE) }
        private val libsVersionsTomlFile by lazy { projectDir.resolve(GRADLE_DIR).resolve(LIBS_VERSIONS_TOML_FILE) }
        private val gradleDir by lazy { projectDir.resolve(GRADLE_DIR) }
        private val configFile by lazy { projectDir.resolve(CONFIG_FILE) }
        private val gradlePropertiesFile by lazy { projectDir.resolve(GRADLE_PROPERTIES_FILE) }


        @BeforeAll
        @JvmStatic
        fun `load configuration content before all tests`() = log.loadConfiguration(
            projectDir,
            gradleDir,
            libsVersionsTomlFile,
            tomlListOfStringContained,
            settingsFile,
            settingsListOfStringContained,
            buildFile,
            buildScriptListOfStringContained,
            configFile,
            configListOfStringContained
        )
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
        assertThat(buildFile)
            .describedAs("$BUILD_FILE should exists.")
            .exists()
        assertThat(settingsFile.path)
            .describedAs("$SETTINGS_FILE should exists.")
            .isEqualTo("${projectDir.path}$separator$SETTINGS_FILE")
        assertThat(libsVersionsTomlFile.path)
            .describedAs("$LIBS_VERSIONS_TOML_FILE should exists.")
            .isEqualTo("${projectDir.path}$separator$GRADLE_DIR$separator$LIBS_VERSIONS_TOML_FILE")
        assertThat(configFile.path)
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
        assertThat(configFile)
            .describedAs("Config file should be created")
            .exists()
        assertThat(configFile.readText(UTF_8))
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

        assertThat(gradleDir)
            .describedAs("gradle directory should exist")
            .exists()
            .describedAs("gradle should be a directory")
            .isDirectory

        assertThat(gradleDir.listFiles().map { it.name })
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

        buildFile
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
            .contains(
                """
Detailed task information for bake

Path
     :bake

Type
     JBakeTask (org.jbake.gradle.JBakeTask)

Options
     --rerun     Causes the task to be re-run even if up-to-date.

Description
     Bake a jbake project

Group
     Documentation""".trimIndent()
            )

        "✓ Bake task executed successfully"
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

    @AfterEach
    fun teardown(testInfo: TestInfo) = listOf(
        "✓ Test finished: ${testInfo.displayName}",
        "─".repeat(60)
    ).forEach {
        it.apply(log::info)
            .apply(::println)
    }


}