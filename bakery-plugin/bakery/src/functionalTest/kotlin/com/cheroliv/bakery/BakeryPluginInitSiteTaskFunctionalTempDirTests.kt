package com.cheroliv.bakery

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test

class BakeryPluginInitSiteTaskFunctionalTempDirTests {

    companion object {
        private val log: Logger by lazy { getLogger(BakeryPluginInitSiteTaskFunctionalTempDirTests::class.java) }

        fun info(message: String) {
            message
                .apply(log::info)
                .run(::println)
        }
    }

    @field:TempDir
    lateinit var projectDir: File

    @BeforeTest
    fun prepare() {
        "${BakeryPluginInitSiteTaskFunctionalTempDirTests::class.java.simpleName}.projectDir exists, path: $projectDir"
            .run(::info)
        info("Prepare temporary directory to host gradle build.")
        projectDir.createSettingsFile()
        projectDir.createBuildScriptFile()
        projectDir.createDependenciesFile()
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

}



