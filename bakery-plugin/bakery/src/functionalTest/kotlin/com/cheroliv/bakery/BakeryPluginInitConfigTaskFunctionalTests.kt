@file:Suppress("FunctionName")

package com.cheroliv.bakery


import com.cheroliv.bakery.FuncTestsConstants.BAKERY_GROUP
import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE
import com.cheroliv.bakery.FuncTestsConstants.CONFIG_FILE
import com.cheroliv.bakery.FuncTestsConstants.LIBS_FILE
import com.cheroliv.bakery.FuncTestsConstants.SETTINGS_FILE
import com.cheroliv.bakery.FuncTestsConstants.buildScriptListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.configListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.settingsListOfStringContained
import com.cheroliv.bakery.FuncTestsConstants.tomlListOfStringContained
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner.create
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.text.Charsets.UTF_8


class BakeryPluginInitConfigTaskFunctionalTests {
    companion object {
        private val log: Logger by lazy { getLogger(BakeryPluginInitSiteTaskFunctionalTests::class.java) }

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

    @Test
    fun `test initConfig task exists without config file when running tasks`() {
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
            .describedAs("""Gradle task tasks output should contains 'initConfig' and 'Initialize configuration.'""")
            .contains("Initialize Bakery configuration.", "initConfig")
        info("✓ tasks displays the initConfig task's description correctly without config file.")
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
            .describedAs("""Gradle task tasks output should contains 'initConfig' and 'Initialize configuration.'""")
            .contains("Initialize Bakery configuration.", "initConfig")
        info("✓ tasks displays the initConfig task's description correctly.")
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
}