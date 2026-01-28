package com.cheroliv.bakery.scenarios

import com.cheroliv.bakery.BakeConfiguration
import com.cheroliv.bakery.FileSystemManager.yamlMapper
import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE
import com.cheroliv.bakery.FuncTestsConstants.SETTINGS_FILE
import com.cheroliv.bakery.SiteConfiguration
import com.fasterxml.jackson.module.kotlin.readValue
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_8

class InitSiteSteps(private val world: TestWorld) {

    @And("I add a buildScript file with {string} as the config path in the dsl")
    fun checkBuildScript(configFileName: String) {
        BUILD_FILE
            .run(world.projectDir!!::resolve)
            .readText(UTF_8)
            .run(::assertThat)
            .describedAs("Gradle buildScript should contains plugins block and bakery dsl.")
            .contains(
                "plugins { id(\"com.cheroliv.bakery\") }",
                "bakery { configPath = file(\"$configFileName\").absolutePath }"
            )
    }

    @And("the gradle project does not have {string} as site configuration file")
    fun checkSiteConfigFileDoesNotExists(configFileName: String) {
        configFileName
            .run(world.projectDir!!::resolve)
            .apply { if (exists()) assertTrue(delete()) }
            .run(::assertThat)
            .describedAs("Project directory should not have a site configuration file.")
            .doesNotExist()
    }

    @And("I add the gradle settings file with gradle portal dependencies repository")
    fun checkRepositoryManagementInSettingsGradleFile() {
        SETTINGS_FILE
            .run(world.projectDir!!::resolve)
            .readText(UTF_8)
            .run(::assertThat)
            .describedAs("The gradle settings file should contains gradlePortal repository")
            .contains("pluginManagement.repositories.gradlePluginPortal()")
    }

    @And("the gradle project does not have {string} file for site")
    fun checkDontHaveSiteFolder(siteFolderName: String) {
        world.projectDir!!
            .resolve(siteFolderName)
            .run(::assertThat)
            .describedAs("project directory should not contain file named '$siteFolderName'")
            .doesNotExist()
    }


    @And("the gradle project does not have {string} file for maquette")
    fun checkDontHaveMaquetteFolders(maquetteFolderName: String) {
        world.projectDir!!
            .resolve(maquetteFolderName)
            .run(::assertThat)
            .describedAs("project directory should not contain file named '$maquetteFolderName'")
            .doesNotExist()

    }

    @Then("the gradle project folder should have a {string} file for site configuration")
    fun siteConfigurationFileShouldBeCreated(configFileName: String) {
        world.projectDir!!.resolve(configFileName).run {

            run(::assertThat)
                .describedAs("project directory should contains file named '$configFileName'")
                .exists()

            readText(UTF_8)
                .run(::assertThat)
                .contains("bake", "srcPath", "destDirPath", "site")

            assertThat(yamlMapper.readValue<SiteConfiguration>(this))
                .describedAs("YAML mapping should fit.")
                .isEqualTo(
                    SiteConfiguration(
                        BakeConfiguration(
                            "site",
                            "bake",
                            ""
                        )
                    )
                )
        }
    }

    @Then("the gradle project folder should have a directory named {string} who contains jbake.properties file")
    fun jbakePropertiesFileShouldBeCreated(siteDirName: String) {
        world.projectDir!!
            .resolve("site")
            .resolve("jbake.properties")
            .run(::assertThat)
            .describedAs("the $siteDirName directory should contains jbake.properties file")
            .exists()
            .isFile()
    }

    @Then("the gradle project folder should have a directory named {string} who contains index.html file")
    fun indexHtmlFileShouldBeCreated(siteDirName: String) {
        world.projectDir!!
            .resolve("maquette")
            .resolve("index.html")
            .run(::assertThat)
            .describedAs("the $siteDirName directory should contains jbake.properties file")
            .exists()
            .isFile()
    }
}
