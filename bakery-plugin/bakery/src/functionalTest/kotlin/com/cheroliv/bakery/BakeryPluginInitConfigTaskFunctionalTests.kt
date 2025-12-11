@file:Suppress("FunctionName")

package com.cheroliv.bakery

import com.cheroliv.bakery.BakeryPluginFunctionalTests.Companion.buildScriptListOfStringContained
import com.cheroliv.bakery.BakeryPluginFunctionalTests.Companion.configListOfStringContained
import com.cheroliv.bakery.BakeryPluginFunctionalTests.Companion.settingsListOfStringContained
import com.cheroliv.bakery.BakeryPluginFunctionalTests.Companion.tomlListOfStringContained
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner.create
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.assertThrows
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

        fun info(message: String) {
            message
                .apply(log::info)
                .run(::println)
        }
    }

    @field:TempDir
    lateinit var projectDir: File

    val File.configFile: File
        get() = if (absolutePath == projectDir.absolutePath)
            resolve("site.yml")
        else projectDir.resolve("site.yml")

    fun File.deleteConfigFile(): Boolean = configFile.delete()


    @BeforeTest
    fun prepare() {
        // Do site.yml exist in root project folder?
        assertThat(projectDir.resolve("site.yml"))
            .describedAs("site.yml should not exists yet")
            .doesNotExist()
        info("Prepare temporary directory to host gradle build.")
        projectDir.createSettingsFile()
        projectDir.createBuildScriptFile()
        projectDir.createDependenciesFile()
        projectDir.createConfigFile()
        assertThat(projectDir.configFile.readText(UTF_8))
            .describedAs("Config file should contains expectedStrings ; $configListOfStringContained")
            .contains(configListOfStringContained)

        assertThat(projectDir.resolve("gradle/libs.versions.toml").readText(UTF_8))
            .describedAs("libsVersionsTomlFile should contains the given list of strings")
            .contains(tomlListOfStringContained)
        assertThat(projectDir.resolve("build.gradle.kts").readText(UTF_8))
            .describedAs("buildFile should contains the given list of strings")
            .contains(buildScriptListOfStringContained)
        assertThat(projectDir.resolve("settings.gradle.kts").readText(UTF_8))
            .describedAs("settingsFile should contains the given list of strings")
            .contains(settingsListOfStringContained.toMutableList().apply {  add("bakery-test")})
        info("gradle and site.yml files successfully created.")
    }

    @Test
    fun `test initConfig task without config file`() {
//        projectDir.deleteConfigFile()
        info("site.yml file successfully deleted.")
        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("initConfig")
            .withProjectDir(projectDir)
            .build()
//        assertThat(result.output)
//            .describedAs("""Gradle task tasks output should contains 'initConfig' and 'Initialize configuration.'""")
//            .contains("Initialize configuration.", "initConfig")
//        info("✓ tasks displays the initConfig task's description correctly")
    }


    @Test
    fun `tasks displays with config file`() {
        val result = create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=bakery")
            .withProjectDir(projectDir)
            .build()
        assertThat(result.output)
            .describedAs("""Gradle task tasks output should contains 'initConfig' and 'Initialize configuration.'""")
            .contains("Initialize configuration.", "initConfig")
        info("✓ tasks displays the initConfig task's description correctly")
    }

    @Test
    fun `tasks displays without config file`() {
        projectDir.deleteConfigFile()
        info("site.yml file successfully deleted.")
        assertThrows<UnexpectedBuildFailure> {
            create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("tasks", "--group=bakery")
                .withProjectDir(projectDir)
                .build()
        }
        info("✓ without config file, the project fails to build.")
    }
}