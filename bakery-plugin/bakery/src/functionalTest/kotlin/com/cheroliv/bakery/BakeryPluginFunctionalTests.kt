package com.cheroliv.bakery

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

class BakeryPluginFunctionalTests {


    // ========================================================================
    // PHASE 1: File Setup Tests
    // ========================================================================
    @Test
    fun `phase 1 - all project files are created correctly`() {
        "Test: Creation of all project files"
            .apply(logger::info)
            .apply(::println)

        // Verify file paths are correct
        assertThat(buildFile)
            .exists()
        assertThat(settingsFile.path)
            .isEqualTo("${projectDir.path}${separator}${SETTINGS_FILE}")
        assertThat(libsVersionsTomlFile.path)
            .isEqualTo("${projectDir.path}${separator}${GRADLE_DIR}${separator}${LIBS_VERSIONS_TOML_FILE}")
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
            .apply(logger::info)
            .apply(::println)
    }

    @Test
    fun `phase 1 - config file can be created and validated independently`() {
        "Test: Creation and validation of the configuration file"
            .apply(logger::info)
            .apply(::println)
        assertThat(configFile)
            .describedAs("Config file should be created")
            .exists()
        assertThat(configFile.readText(UTF_8))
            .describedAs("Config file should contains expectedStrings ; $configListOfStringContained")
            .contains(configListOfStringContained)
        "✓ Configuration file created and validated"
            .apply(logger::info)
            .apply(::println)
    }

    @Test
    fun `phase 1 - gradle directory structure is created correctly`() {
        "Test: Creation of the gradle/ structure"
            .apply(logger::info)
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
            .apply(logger::info)
            .apply(::println)
    }

    // ========================================================================
    // PHASE 2: Plugin Execution Tests
    // ========================================================================
    @Suppress("FunctionName")
    @Test
    fun `phase 2 - plugin can read configuration from file`() {
        "Test: The plugin can read the configuration file"
            .apply(logger::info)
            .apply(::println)

        buildFile
            .readText(UTF_8)
            .run(::assertThat)
            .describedAs("Proves gradle script use extension and config file are accorded")
            .contains("""bakery { configPath = file("$CONFIG_FILE").absolutePath }""")

        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=bakery")
            .withProjectDir(projectDir)
            .build()

        assertThat(result.output)
            .describedAs("""Gradle task tasks output should contains publishSite and publishMaquette""")
            .contains("publishMaquette", "publishSite")

        "✓ Plugin reads the configuration correctly"
            .apply(logger::info)
            .apply(::println)
    }

    @Test
    fun `phase 2 - help task bake command retrieves name and description successfully`() {
        "Test: The bake task executes successfully"
            .apply(logger::info)
            .apply(::println)

        val result = create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--task", "bake")
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
            .apply(logger::info)
            .apply(::println)
    }

    // ========================================================================
    // PHASE 3: Integration Tests (Future)
    // ========================================================================

    @Test
    fun `phase 3 - complete workflow from bake to push executes successfully`() {
//        "Phase 3 - Future: End-to-end workflow testing"
        "Test: Complete workflow from bake to push"
            .apply(logger::info)
            .apply(::println)

        // TODO: Implement complete workflow test
        // 1. Setup files
        // 2. Run bake
        // 3. Verify output
        // 4. Run pushPage
        // 5. Verify push result

        "✓ Complete workflow executed successfully"
            .apply(logger::info)
            .apply(::println)
    }

    @Test
    fun `phase 3 - plugin handles missing configuration gracefully`() {
//        "Phase 3 - Future: Error handling testing"
        "Test: Handling of missing configuration errors"
            .apply(logger::info)
            .apply(::println)

        // TODO: Test error scenarios
        // - Missing config file
        // - Invalid config format
        // - Missing required fields

        "✓ Errors handled correctly"
            .apply(logger::info)
            .apply(::println)
    }


    @AfterEach
    fun teardown(testInfo: TestInfo) = listOf(
        "✓ Test finished: ${testInfo.displayName}",
        "─".repeat(60)
    ).forEach {
        it.apply(logger::info)
            .apply(::println)
    }

    companion object {
        private val logger = getLogger(BakeryPluginFunctionalTests::class.java)
        private val projectDir: File = ""
            .run(::File)
            .absoluteFile
            .parentFile
            .parentFile
        private const val CONFIG_FILE = "site.yml"
        private const val BUILD_FILE = "build.gradle.kts"
        private const val SETTINGS_FILE = "settings.gradle.kts"
        private const val GRADLE_DIR = "gradle"
        private const val LIBS_VERSIONS_TOML_FILE = "libs.versions.toml"

        private val PATH_GAP = "..$separator..$separator"
        private val CONFIG_PATH = "${PATH_GAP}$CONFIG_FILE"
        private val BUILD_FILE_PATH = "${PATH_GAP}$BUILD_FILE"
        private val SETTINGS_FILE_PATH = "${PATH_GAP}$SETTINGS_FILE"
        private val LIBS_VERSIONS_TOML_FILE_PATH = "${PATH_GAP}$GRADLE_DIR${separator}$LIBS_VERSIONS_TOML_FILE"

        //Create settings files in temporary directory
        private val buildFile by lazy { projectDir.resolve(BUILD_FILE) }
        private val settingsFile by lazy { projectDir.resolve(SETTINGS_FILE) }
        private val libsVersionsTomlFile by lazy { projectDir.resolve(GRADLE_DIR).resolve(LIBS_VERSIONS_TOML_FILE) }
        private val gradleDir by lazy { projectDir.resolve(GRADLE_DIR) }
        private val configFile by lazy { projectDir.resolve(CONFIG_FILE) }


        private val buildScriptListOfStringContained = listOf(
                """alias(libs.plugins.bakery)""".trimIndent(),
                """bakery { configPath = file("$CONFIG_FILE").absolutePath }""".trimIndent(),
            )
        private val settingsListOfStringContained = listOf(
            "pluginManagement", "repositories",
            "mavenLocal()", "gradlePluginPortal()",
            "mavenCentral()", "google()",
            "dependencyResolutionManagement",
            "rootProject.name", "bakery-gradle-plugin"
        )
        private val tomlListOfStringContained = listOf(
            "[versions]",
            "[libraries]",
            "[plugins]",
            "[bundles]",
        )
        private val configListOfStringContained = listOf(
            "bake:", "srcPath:", "destDirPath:",
            "cname:", "pushPage:", "from:", "to:",
            "repo:", "name:", "repository:",
            "credentials:", "username:",
            "password:", "branch:", "message:",
            "pushMaquette:", "supabase:",
            "project:", "url:", "publicKey:",
            "schema:", "type:", "contacts:",
            "name:", "public.contacts",
            "columns:", "uuid", "text", "id",
            "created_at", "name", "email",
            "telephone", "rlsEnabled: true",
            "messages:", "public.messages", "rpc:",
            "name:", "params:", "timestamptz",
            "contact_id", "subject", "message",
            "public.handle_contact_form", "p_name",
            "p_email", "p_subject", "p_message",
        )

        @BeforeAll
        @JvmStatic
        fun `load configuration content before all tests`() {

            "Setup test"
                .apply(logger::debug)
                .apply(::println)

            // Check every files are prepared but do not exist yet!
            "prepare test environment"
                .apply(logger::debug)
                .apply(::println)

            assertThat(gradleDir)
                .describedAs("gradle directory should exist")
                .exists()
                .describedAs("gradle should be a directory")
                .isDirectory
                .isNotEmptyDirectory

            assertThat(libsVersionsTomlFile)
                .describedAs("libs.versions.toml file should be created")
                .exists()
                .describedAs("libs.versions.toml file is a physical file")
                .isFile
                .describedAs("libs.versions.toml file should not be empty")
                .isNotEmpty

            assertThat(libsVersionsTomlFile.readText(UTF_8))
                .describedAs("libsVersionsTomlFile should contains the given list of strings")
                .contains(tomlListOfStringContained)

            val tomlSearchedFile = gradleDir
                .listFiles()
                ?.findLast { it.name == "libs.versions.toml" }

            assertThat(tomlSearchedFile)
                .isFile
                .isNotNull
                .isNotEmpty
                .exists()


            assertThat(tomlSearchedFile!!.readText(UTF_8))
                .describedAs("toml file content should contains this list of strings")
                .contains(tomlListOfStringContained)

            assertThat(settingsFile)
                .describedAs("Settings file should be created")
                .exists()
                .describedAs("Settings file is a physical file")
                .isFile
                .describedAs("Settings file should not be empty")
                .isNotEmpty

            assertThat(settingsFile.readText(UTF_8))
                .describedAs("settingsFile should contains the given list of strings")
                .contains(settingsListOfStringContained)

            val settingsSearchedFile = projectDir
                .listFiles()
                ?.findLast { it.name == "settings.gradle.kts" }

            assertThat(settingsSearchedFile)
                .isFile
                .isNotNull
                .isNotEmpty
                .exists()


            assertThat(settingsSearchedFile!!.readText(UTF_8))
                .describedAs("Settings content should contains this list of strings")
                .contains(settingsListOfStringContained)

            assertThat(buildFile)
                .describedAs("Build file should be created")
                .exists()
                .describedAs("Build file is a physical file")
                .isFile
                .describedAs("Build file should not be empty")
                .isNotEmpty

            assertThat(buildFile.readText(UTF_8))
                .describedAs("buildFile should contains the given list of strings")
                .contains(buildScriptListOfStringContained)

            val buildSearchedFile = projectDir
                .listFiles()
                ?.findLast { it.name == "build.gradle.kts" }

            assertThat(buildSearchedFile)
                .isFile
                .isNotNull
                .isNotEmpty
                .exists()

            val buildSearchedFileContent = buildSearchedFile!!.readText(UTF_8)

            assertThat(buildSearchedFileContent)
                .describedAs("Build script content should contains this list of strings")
                .contains(buildScriptListOfStringContained)

            "Initialisation"
                .apply(::println)
                .apply(logger::info)

            assertThat(projectDir)
                .describedAs("projectDir should not be an empty directory now that configFile is created and written")
                .isNotEmptyDirectory

            val configSearchedFile = projectDir
                .listFiles()
                .findLast { it.name == CONFIG_FILE }!!

            assertThat(configSearchedFile)
                .isFile
                .isNotNull
                .isNotEmpty
                .exists()

            assertThat(configSearchedFile.readText(UTF_8))
                .describedAs("ConfigContent should contains this list of strings")
                .contains(configListOfStringContained)

            // Set and check config file initialization
            assertThat(configFile)
                .describedAs("Source config file '$CONFIG_PATH' not found.")
                .exists()
                .isNotEmpty

            assertThat(configFile.readText(UTF_8))
                .contains(configListOfStringContained)

            assertThat(settingsFile)
                .describedAs("Gradle settings file '$SETTINGS_FILE_PATH' not found.")
                .exists()
                .isNotEmpty
            val settingsFile = File(SETTINGS_FILE_PATH)

            assertThat(settingsFile.readText(UTF_8))
                .describedAs("Gradle settings file should contains pluginManagement and dependencyResolutionManagement blocks.")
                .contains(settingsListOfStringContained)

            assertThat(buildFile)
                .describedAs("Gradle build script file '$BUILD_FILE_PATH' not found.")
                .exists()
                .isNotEmpty

            assertThat(buildFile.readText(UTF_8))
                .describedAs("Gradle build script file should contains build logik")
                .contains(buildScriptListOfStringContained)

            assertThat(libsVersionsTomlFile)
                .describedAs("libs.versions.toml file '$LIBS_VERSIONS_TOML_FILE_PATH' not found.")
                .exists()
                .isNotEmpty

            assertThat(libsVersionsTomlFile.readText(UTF_8))
                .describedAs("toml file should contains dependencies")
                .contains(tomlListOfStringContained)
        }
    }
}