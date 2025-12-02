@file:Suppress("FunctionName")

package com.cheroliv.bakery

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner.create
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.io.File.separator
import kotlin.test.Ignore
import kotlin.text.Charsets.UTF_8

@Ignore
class RefactoPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    // File references
    private val buildFile by lazy { projectDir.resolve(BUILD_FILE) }
    private val settingsFile by lazy { projectDir.resolve(SETTINGS_FILE) }
    private val libsVersionsTomlFile by lazy {
        projectDir.resolve(GRADLE_DIR).resolve(LIBS_VERSIONS_TOML_FILE)
    }
    private val gradleDir by lazy { projectDir.resolve(GRADLE_DIR) }
    private val configFile by lazy { projectDir.resolve(CONFIG_FILE) }

    companion object {
        private val logger = LoggerFactory.getLogger(TempDirBakeryPluginFunctionalTest::class.java)

        // File paths
        private const val CONFIG_FILE = "site.yml"
        private const val BUILD_FILE = "build.gradle.kts"
        private const val SETTINGS_FILE = "settings.gradle.kts"
        private const val GRADLE_DIR = "gradle"
        private const val LIBS_VERSIONS_TOML_FILE = "libs.versions.toml"
        private val PATH_GAP = "..$separator..$separator"

        // Content holders
        private lateinit var configContent: String
        private lateinit var buildScriptContent: String
        private lateinit var settingsContent: String
        private lateinit var libsVersionsTomlFileContent: String

        // External file paths (for loading content)
        private val SOURCE_CONFIG_PATH = "$PATH_GAP$CONFIG_FILE"
        private val SOURCE_BUILD_FILE_PATH = "$PATH_GAP$BUILD_FILE"
        private val SOURCE_SETTINGS_FILE_PATH = "$PATH_GAP$SETTINGS_FILE"
        private val SOURCE_LIBS_VERSIONS_TOML_FILE_PATH =
            "$PATH_GAP$GRADLE_DIR$separator$LIBS_VERSIONS_TOML_FILE"

        // Expected content validations
        private val buildScriptListOfStringContained = listOf(
            """alias(libs.plugins.bakery)"""".trimIndent(),
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
            "[versions]", "[libraries]", "[plugins]", "[bundles]",
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
            logger.info("=".repeat(60))
            logger.info("Initialisation des contenus de fichiers sources")
            logger.info("=".repeat(60))

            // Load config file content
            configContent = loadAndValidateSourceFile(
                SOURCE_CONFIG_PATH,
                "Config file",
                configListOfStringContained
            )

            // Load settings.gradle.kts content
            settingsContent = loadAndValidateSourceFile(
                SOURCE_SETTINGS_FILE_PATH,
                "Settings file",
                settingsListOfStringContained
            )

            // Load build.gradle.kts content
            buildScriptContent = loadAndValidateSourceFile(
                SOURCE_BUILD_FILE_PATH,
                "Build script file",
                buildScriptListOfStringContained
            )

            // Load libs.versions.toml content
            libsVersionsTomlFileContent = loadAndValidateSourceFile(
                SOURCE_LIBS_VERSIONS_TOML_FILE_PATH,
                "libs.versions.toml file",
                tomlListOfStringContained
            )

            logger.info("✓ Tous les contenus sources ont été chargés et validés")
        }

        /**
         * Loads a source file and validates its content
         */
        private fun loadAndValidateSourceFile(
            path: String,
            description: String,
            expectedStrings: List<String>
        ): String {
            logger.debug("Chargement de $description depuis '$path'")

            val sourceFile = File(path)
            assertThat(sourceFile)
                .describedAs("$description '$path' should exist")
                .exists()
                .isNotEmpty


            val content = sourceFile.readText(UTF_8)
            assertThat(content)
                .describedAs("$description content should contain expected strings")
                .contains(expectedStrings)

            logger.debug("✓ $description chargé et validé")
            return content
        }
    }

    @BeforeEach
    fun `prepare test environment`() {
        logger.info("-".repeat(60))
        logger.info("Préparation de l'environnement de test")
        logger.info("-".repeat(60))

        // Verify project directory is empty
        assertThat(projectDir)
            .describedAs("projectDir should be a clean empty directory")
            .exists()
            .isDirectory
            .isEmptyDirectory

        logger.debug("✓ Répertoire de test vide et prêt")
    }

    @AfterEach
    fun teardown(testInfo: TestInfo) {
        logger.info("✓ Test terminé: ${testInfo.displayName}")
        logger.info("=".repeat(60))
    }

    /**
     * Creates all required files in the test project directory
     */
    private fun setupAllProjectFiles() {
        logger.info("Création de tous les fichiers du projet")

        // Create config file
        createAndValidateFile(
            file = configFile,
            content = configContent,
            expectedStrings = configListOfStringContained,
            description = "Config file ($CONFIG_FILE)"
        )

        // Create gradle directory
        logger.debug("Création du répertoire gradle/")
        gradleDir.mkdirs()
        assertThat(gradleDir)
            .describedAs("gradle directory should exist")
            .exists()
            .isDirectory

        // Create libs.versions.toml
        createAndValidateFile(
            file = libsVersionsTomlFile,
            content = libsVersionsTomlFileContent,
            expectedStrings = tomlListOfStringContained,
            description = "libs.versions.toml file",
            searchDir = gradleDir
        )

        // Create settings.gradle.kts
        createAndValidateFile(
            file = settingsFile,
            content = settingsContent,
            expectedStrings = settingsListOfStringContained,
            description = "settings.gradle.kts file"
        )

        // Create build.gradle.kts
        createAndValidateFile(
            file = buildFile,
            content = buildScriptContent,
            expectedStrings = buildScriptListOfStringContained,
            description = "build.gradle.kts file"
        )

        logger.info("✓ Tous les fichiers du projet ont été créés et validés")
    }

    /**
     * Creates a file, writes content, and validates it thoroughly
     */
    private fun createAndValidateFile(
        file: File,
        content: String,
        expectedStrings: List<String>,
        description: String,
        searchDir: File = projectDir
    ) {
        logger.debug("Création de $description...")

        // Write content to file
        file.writeText(content)

        // Verify project directory is no longer empty
        assertThat(projectDir)
            .describedAs("projectDir should not be empty after creating $description")
            .isNotEmptyDirectory

        // Verify file exists and has content
        assertThat(file)
            .describedAs("$description should be created")
            .exists()
            .describedAs("$description should be a physical file")
            .isFile
            .describedAs("$description should not be empty")
            .isNotEmpty

        // Verify file content
//        assertThat(file.readText(UTF_8))
//            .describedAs("$description should contain expected strings")
//            .contains(expectedStrings)

        // Validate by searching in directory
        val searchedFile = searchDir.listFiles()
            ?.findLast { it.name == file.name }

        assertThat(searchedFile)
            .describedAs("$description should be found in ${searchDir.name}")
            .isNotNull
            .describedAs("$description should be a physical file")
            .isFile
            .describedAs("$description should not be empty")
            .isNotEmpty
            .describedAs("$description should exist")
            .exists()

//        assertThat(searchedFile!!.readText(UTF_8))
//            .describedAs("$description content (via search) should contain expected strings")
//            .contains(expectedStrings)

        logger.debug("✓ $description créé et validé")
    }

    // ========================================================================
    // PHASE 1: File Setup Tests
    // ========================================================================
    @Test
    fun `phase 1 - all project files are created correctly`() {
        logger.info("Test: Création de tous les fichiers du projet")

        // Initial state verification
        assertThat(buildFile).doesNotExist()
        assertThat(settingsFile).doesNotExist()
        assertThat(libsVersionsTomlFile).doesNotExist()
        assertThat(configFile).doesNotExist()

        // Verify file paths are correct
        assertThat(buildFile.path)
            .isEqualTo("${projectDir.path}${separator}$BUILD_FILE")
        assertThat(settingsFile.path)
            .isEqualTo("${projectDir.path}${separator}$SETTINGS_FILE")
        assertThat(libsVersionsTomlFile.path)
            .isEqualTo("${projectDir.path}${separator}$GRADLE_DIR${separator}$LIBS_VERSIONS_TOML_FILE")
        assertThat(configFile.path)
            .isEqualTo("${projectDir.path}${separator}$CONFIG_FILE")

        // Setup all files
        setupAllProjectFiles()

        // Final verification
        assertThat(projectDir)
            .describedAs("projectDir should contain all created files")
            .isNotEmptyDirectory

        assertThat(projectDir.listFiles()?.size)
            .describedAs("projectDir should contain exactly 4 items (3 files + 1 directory)")
            .isEqualTo(4)

        logger.info("✓ Tous les fichiers ont été créés avec succès")
    }

    @Test
    fun `phase 1 - config file can be created and validated independently`() {
        logger.info("Test: Création et validation du fichier de configuration")

        assertThat(configFile).doesNotExist()

        createAndValidateFile(
            file = configFile,
            content = configContent,
            expectedStrings = configListOfStringContained,
            description = "Config file"
        )

        assertThat(configFile)
            .describedAs("Config file should be created")
            .exists()
        logger.info("✓ Fichier de configuration créé et validé")
    }

    @Test
    fun `phase 1 - gradle directory structure is created correctly`() {
        logger.info("Test: Création de la structure gradle/")

        assertThat(gradleDir)
            .describedAs("gradle directory should not exist")
            .doesNotExist()

        assertThat(gradleDir.mkdirs())
            .describedAs("gradle directory should be created successfully")
            .isTrue

        assertThat(gradleDir)
            .describedAs("gradle directory should exist")
            .exists()
            .describedAs("gradle should be a directory")
            .isDirectory

        createAndValidateFile(
            file = libsVersionsTomlFile,
            content = libsVersionsTomlFileContent,
            expectedStrings = tomlListOfStringContained,
            description = "libs.versions.toml",
            searchDir = gradleDir
        )

        assertThat(gradleDir.listFiles()?.size)
            .describedAs("gradle directory should contain exactly 1 file")
            .isEqualTo(1)

        logger.info("✓ Structure gradle/ créée avec succès")
    }

    // ========================================================================
    // PHASE 2: Plugin Execution Tests (WIP)
    // ========================================================================
    @Test
    fun `phase 2 - plugin can read configuration from file`() {
        logger.info("Test: Le plugin peut lire le fichier de configuration")
        setupAllProjectFiles()


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

        logger.info("✓ Plugin lit correctement la configuration")
    }

    @Ignore
    @Test
    @Suppress("FunctionName")
    fun `phase 2 - plugin registers jbakeRuntime configuration with correct dependencies`() {
        create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("printJBakeClasspath")
            .withProjectDir(projectDir)
            .build().run {
                listOf(
                    "jbake-core-2.7.0-rc.7.jar",
                    "slf4j-simple-2.0.17.jar",
                    "asciidoctorj-diagram-3.0.1.jar",
                    "asciidoctorj-diagram-plantuml-1.2025.3.jar",
                    "commons-io-2.13.0.jar",
                    "commons-configuration-1.10.jar"
                ).forEach { dependency ->
                    assertThat(output.contains(dependency))
                        .describedAs("Missing dependency in classpath: $dependency")
                        .isTrue
                }
            }
    }


    @Test
    @Ignore("Phase 2 - WIP: Implementation of bake task testing")
    fun `phase 2 - bake task executes successfully`() {
        logger.info("Test: La tâche bake s'exécute avec succès")

        setupAllProjectFiles()

        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("bake")
            .withProjectDir(projectDir)
            .build()

        assertThat(result.output)
            .contains("BUILD SUCCESSFUL")

        logger.info("✓ Tâche bake exécutée avec succès")
    }

    // ========================================================================
    // PHASE 3: Integration Tests (Future)
    // ========================================================================

    @Test
    @Ignore("Phase 3 - Future: End-to-end workflow testing")
    fun `phase 3 - complete workflow from bake to push executes successfully`() {
        logger.info("Test: Workflow complet de bake à push")

        // TODO: Implement complete workflow test
        // 1. Setup files
        // 2. Run bake
        // 3. Verify output
        // 4. Run pushPage
        // 5. Verify push result

        logger.info("✓ Workflow complet exécuté avec succès")
    }

    @Test
    @Ignore("Phase 3 - Future: Error handling testing")
    fun `phase 3 - plugin handles missing configuration gracefully`() {
        logger.info("Test: Gestion des erreurs de configuration manquante")

        // TODO: Test error scenarios
        // - Missing config file
        // - Invalid config format
        // - Missing required fields

        logger.info("✓ Erreurs gérées correctement")
    }
}
