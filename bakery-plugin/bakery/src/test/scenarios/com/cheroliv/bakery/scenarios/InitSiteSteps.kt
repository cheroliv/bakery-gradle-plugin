package com.cheroliv.bakery.scenarios

import com.cheroliv.bakery.FileSystemManager.yamlMapper
import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE
import com.cheroliv.bakery.SiteConfiguration
import com.cheroliv.bakery.SiteManager.BAKERY_CONFIG_PATH_KEY
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_8


class InitSiteSteps(private val world: TestWorld) {
    @And("the output of the task {string} contains {string} from the group {string} and {string}")
    fun checkInitSiteTaskIsAvailable(
        tasksTaskName: String,
        initSiteTaskName: String,
        taskGroup: String,
        taskDescription: String,
    ): Unit = runBlocking {
        world.executeGradle("-s", tasksTaskName).output
            .run(::assertThat)
            .contains(
                taskGroup,
                initSiteTaskName,
                taskDescription,
                "BUILD SUCCESSFUL"
            )
    }

    @Then("after running {string} the task is not available using {string} configuration")
    fun checkInitSiteTaskIsNotAvailableAfterRunning(
        taskName: String,
        configFileName: String): Unit = runBlocking {
        val site: SiteConfiguration = world.projectDir!!.resolve(configFileName)
            .run(yamlMapper::readValue)
        world.projectDir!!
            .resolve(site.bake.srcPath)
            .run(::assertThat)
            .describedAs("After $taskName task site directory should exist.")
            .exists()
        world.projectDir!!
            .resolve(site.bake.srcPath)
            .resolve("jbake.properties")
            .run(::assertThat)
            .describedAs("After $taskName task ${site.bake.srcPath}/jbake.properties file should exist.")
            .exists()
        world.projectDir!!
            .resolve(site.pushMaquette.from)
            .run(::assertThat)
            .describedAs("After $taskName task maquette directory should exist.")
            .exists()
        world.projectDir!!
            .resolve(site.pushMaquette.from)
            .resolve("index.html")
            .run(::assertThat)
            .describedAs("After $taskName task maquette/index.html file should exist.")
            .exists()
        world.executeGradle("-s", "tasks")
            .output!!
            .run(::assertThat)
            .describedAs("$taskName task should not be available.")
            .doesNotContain(
                taskName,
                "Initialise site and maquette folders.",
            )
    }

    @Given("an existing empty Bakery project using DSL with {string} file")
    fun setProjectWithSiteInitialized(configFileName: String): Unit = runBlocking {
        world.createGradleProject(configFileName)
        assertThat(world.projectDir).exists()
        val site = yamlMapper.readValue<SiteConfiguration>(world.projectDir!!.resolve(configFileName))
        site.bake.srcPath
            .run(::assertThat)
            .describedAs("siteConfiguration.bake.srcPath should be \"${site.bake.srcPath}\"")
            .isEqualTo("site")
        site.bake.destDirPath
            .run(::assertThat)
            .describedAs("siteConfiguration.bake.destDirPath should be \"${site.bake.destDirPath}\"")
            .isEqualTo("bake")
        world.projectDir!!
            .resolve(site.bake.srcPath)
            .run(::assertThat)
            .describedAs("project directory should not contain directory named \"${site.bake.srcPath}\"")
            .doesNotExist()
        world.projectDir!!
            .resolve(site.pushMaquette.from)
            .run(::assertThat)
            .describedAs("project directory should not contain directory named maquette")
            .doesNotExist()

        world.executeGradle("-s", "tasks")
            .output!!
            .run(::assertThat)
            .describedAs("initSite task should be available.")
            .contains(
                "Bakery tasks",
                "initSite",
                "Initialise site and maquette folders.",
                "BUILD SUCCESSFUL"
            )

    }

    @And("the project has {string} file in {string} directory")
    fun checkHaveSiteFolder(
        jbakePropertiesFileName: String,
        siteDirectoryName: String
    ) {
        world.projectDir!!
            .resolve(siteDirectoryName).run {
                run(::assertThat)
                    .describedAs("project directory should contain file named '$siteDirectoryName'")
                    .exists()
                    .isDirectory
                resolve(jbakePropertiesFileName)
                    .run(::assertThat)
                    .describedAs("project directory should contain file named '$jbakePropertiesFileName'")
                    .exists()
                    .isFile
            }
    }

    @And("the gradle project have {string} directory for maquette")
    fun checkHaveMaquetteFolder(maquetteFolderName: String) {
        world.projectDir!!
            .resolve(maquetteFolderName)
            .run(::assertThat)
            .describedAs("project directory should contain file named '$maquetteFolderName'")
            .isDirectory
            .doesNotExist()
    }


    @And("{string} file use {string} as the config path in the DSL")
    fun checkBuildScript(
        buildScriptFileName: String,
        configFileName: String
    ) {
        buildScriptFileName
            .run(world.projectDir!!::resolve)
            .readText(UTF_8)
            .run(::assertThat)
            .describedAs("Gradle buildScript should contains plugins block and bakery dsl.")
            .contains(
                "plugins { id(\"com.cheroliv.bakery\") }",
                "bakery { configPath = file(\"$configFileName\").absolutePath }"
            )
    }

    @And("does not have {string} for site configuration")
    fun checkSiteConfigFileDoesNotExists(configFileName: String) {
        configFileName
            .run(world.projectDir!!::resolve)
            .apply { if (exists()) assertTrue(delete()) }
            .run(::assertThat)
            .describedAs("Project directory should not have a site configuration file.")
            .doesNotExist()
    }

    @And("{string} set gradle portal dependencies repository with {string}")
    fun checkRepositoryManagementInSettingsGradleFile(
        settingsBuildFileName: String,
        gradlePluginPortal: String
    ) {
        settingsBuildFileName
            .run(world.projectDir!!::resolve)
            .apply {
                run(::assertThat)
                    .describedAs("project directory should contains file named '$settingsBuildFileName'")
                    .exists()
                    .isFile
            }.readText(UTF_8)
            .run(::assertThat)
            .describedAs("The gradle settings file should contains gradlePortal repository")
            .contains("pluginManagement.repositories.$gradlePluginPortal()")
    }

    @And("the gradle project does not have {string} directory for site")
    fun checkDontHaveSiteFolder(siteDirectoryName: String) {
        world.projectDir!!
            .resolve(siteDirectoryName)
            .run(::assertThat)
            .describedAs("project directory should not contain file named '$siteDirectoryName'")
            .doesNotExist()
    }


    @And("the gradle project does not have {string} file for maquette")
    fun checkDontHaveMaquetteFolder(maquetteFolderName: String) {
        world.projectDir!!
            .resolve(maquetteFolderName)
            .run(::assertThat)
            .describedAs("project directory should not contain file named '$maquetteFolderName'")
            .doesNotExist()
    }

    @And("I add gradle.properties file with the entry bakery.config.path={string}")
    fun checkBakeryConfigPathKeyInGradlePropertiesFile(configFileName: String) {
        world.projectDir!!
            .resolve("gradle.properties").apply {
                createNewFile()
                writeText("$BAKERY_CONFIG_PATH_KEY=$configFileName", UTF_8)
                readText(UTF_8)
                    .run(::assertThat)
                    .contains("$BAKERY_CONFIG_PATH_KEY=$configFileName")
            }
    }

    @Then("the project should have a {string} file for site configuration")
    fun siteConfigurationFileShouldBeCreated(configFileName: String) {
        world.projectDir!!.resolve(configFileName).run {

            run(::assertThat)
                .describedAs("project directory should contains file named '$configFileName'")
                .exists()

            readText(UTF_8)
                .run(::assertThat)
                .contains("bake", "srcPath", "destDirPath", "site")

            yamlMapper.readValue<SiteConfiguration>(this).run {
                bake.srcPath
                    .run(::assertThat)
                    .describedAs("YAML mapping should fit for siteConfiguration.bake.srcPath.")
                    .isEqualTo("site")
                bake.destDirPath
                    .run(::assertThat)
                    .describedAs("YAML mapping should fit for siteConfiguration.bake.destDirPath.")
                    .isEqualTo("bake")
            }
        }
//        world.projectDir!!.run {
//            path.run { "project path: $this" }.run(::println)
//            "contains:".run(::println)
//            listFiles().forEach {
//                (it.name to it.isDirectory).run {
//                    "$first is a ${if (second) "directory" else "file"}"
//                        .run(::println)
//                }
//            }
//        }
    }

    @Then("the project should have a directory named {string} who contains {string} file")
    fun jbakePropertiesFileShouldBeCreated(
        siteDirName: String,
        jbakePropertiesFileName: String
    ) {
        world.projectDir!!
            .resolve(siteDirName)
            .resolve(jbakePropertiesFileName)
            .run(::assertThat)
            .describedAs("the $siteDirName directory should contains $jbakePropertiesFileName file")
            .exists()
            .isFile()
    }

    @Then("the project should have a directory named {string} to mock site who contains {string} file")
    fun indexHtmlFileShouldBeCreated(
        maquetteDirName: String,
        htmlFileName: String
    ) {
        world.projectDir!!
            .resolve(maquetteDirName)
            .resolve(htmlFileName)
            .run(::assertThat)
            .describedAs("the $maquetteDirName directory should contains jbake.properties file")
            .exists()
            .isFile()
    }

    @Then("the project should have a file named {string} who contains {string}, {string}, {string} and {string}")
    fun checkGitIgnoreFileExistsWithIgnoredFiles(
        gitIgnoreFileName: String,
        configFileName: String,
        dotGradleDirName: String,
        buildDirName: String,
        dotKotlinDirName: String
    ) {
        world.projectDir!!
            .resolve(gitIgnoreFileName)
            .apply {
                run(::assertThat)
                    .describedAs("project directory should contains file named '$gitIgnoreFileName")
                    .exists()
                    .isFile
            }.readText(UTF_8)
            .run(::assertThat)
            .contains(configFileName, dotGradleDirName, buildDirName, dotKotlinDirName)
    }

    @Then("the project should have a file named {string} who contains {string} and {string}")
    fun checkGitAttributesFileExistsWithEolConfig(
        gitAttributesFileName: String,
        gitAttributesFileContentEOL: String,
        gitAttributesFileContentCRLF: String,
    ) {
        world.projectDir!!
            .resolve(gitAttributesFileName)
            .apply {
                run(::assertThat)
                    .describedAs("project directory should contains file named '$gitAttributesFileName")
                    .exists()
                    .isFile
            }.readText(UTF_8)
            .run(::assertThat)
            .contains(gitAttributesFileContentEOL, gitAttributesFileContentCRLF)
    }

    @Then("with buildScript file without bakery dsl")
    fun checkBuildScriptWithoutDsl() {
        BUILD_FILE.run(world.projectDir!!::resolve).apply {
            delete()
            createNewFile()
            writeText("plugins { id(\"com.cheroliv.bakery\") }", UTF_8)
            readText(UTF_8).run(::assertThat)
                .describedAs("Gradle buildScript should contains plugins block without bakery dsl.")
                .doesNotContain("bakery { configPath = file(\"site.yml\").absolutePath }")
        }
    }
}
