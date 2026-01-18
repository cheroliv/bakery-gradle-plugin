package com.cheroliv.bakery

import com.cheroliv.bakery.FuncTestsConstants.BUILD_FILE_PATH
import com.cheroliv.bakery.FuncTestsConstants.CONFIG_FILE
import com.cheroliv.bakery.FuncTestsConstants.CONFIG_PATH
import com.cheroliv.bakery.FuncTestsConstants.GRADLE_DIR
import com.cheroliv.bakery.FuncTestsConstants.LIBS_VERSIONS_TOML_FILE
import com.cheroliv.bakery.FuncTestsConstants.LIBS_VERSIONS_TOML_FILE_PATH
import com.cheroliv.bakery.FuncTestsConstants.SETTINGS_FILE_PATH
import com.cheroliv.bakery.FuncTestsConstants.DEPS
import com.cheroliv.bakery.FuncTestsConstants.GRADLE_BUILD_CONTENT
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.Logger
import java.io.File
import java.io.File.separator
import java.io.IOException
import kotlin.text.Charsets.UTF_8


fun File.createBuildScriptFile() {
    resolve("build.gradle.kts").run {
        assertThat(exists())
            .describedAs("build.gradle.kts should not exists yet.")
            .isFalse
        assertThat(createNewFile())
            .describedAs("build.gradle.kts should be created.")
            .isTrue
        writeText(GRADLE_BUILD_CONTENT.trimIndent(), UTF_8)

    }
}

fun File.createSettingsFile() {
    resolve("settings.gradle.kts").run {
        assertThat(exists())
            .describedAs("settings.gradle.kts should not exists yet.")
            .isFalse
        assertThat(createNewFile())
            .describedAs("setting.gradle.kts should be created.")
            .isTrue
        writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal() }
            }

            rootProject.name = "bakery-test"
        """, UTF_8
        )
        assertThat(exists())
            .describedAs("settings.gradle.kts should now exists.")
            .isTrue

        assertThat(readText(UTF_8))
            .describedAs("settings.gradle.kts should contains at least 'bakery-test'")
            .contains("bakery-test")
    }
}

fun File.createDependenciesFile() {
    val gradleFolder = resolve(GRADLE_DIR)
    val tomlFile = gradleFolder.resolve(LIBS_VERSIONS_TOML_FILE)
    if (!gradleFolder.exists()) gradleFolder.mkdirs()
    if (tomlFile.exists()) if (!tomlFile.delete())
        throw IOException("Could not delete existing $LIBS_VERSIONS_TOML_FILE file.")
    if (!tomlFile.exists()) tomlFile.createNewFile()
    tomlFile.writeText(DEPS.trimIndent(), UTF_8)
}

fun File.createConfigFile() {
    val configFile = File("").absoluteFile.parentFile?.parentFile?.resolve(CONFIG_FILE)
    configFile?.copyTo(resolve(CONFIG_FILE), true)
}
fun Logger.loadConfiguration(
    projectDir: File,
    gradleDir: File,
    libsVersionsTomlFile: File,
    tomlListOfStringContained: List<String>,
    settingsFile: File,
    settingsListOfStringContained: List<String>,
    buildFile: File,
    buildScriptListOfStringContained: List<String>,
    configFile: File,
    configListOfStringContained: List<String>
){
    "Setup test"
        .apply(::debug)
        .apply(::println)

    // Check every files are prepared but do not exist yet!
    "prepare test environment"
        .apply(::debug)
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
        .apply(::info)

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

object FuncTestsConstants {
    const val BAKERY_GROUP = "bakery"
    const val BAKE_TASK = "bake"
    const val CNAME = "CNAME"
    const val CONFIG_FILE = "site.yml"
    const val GRADLE_PROPERTIES_FILE = "site.yml"
    const val BUILD_FILE = "build.gradle.kts"
    const val SETTINGS_FILE = "settings.gradle.kts"
    const val GRADLE_DIR = "gradle"
    const val LIBS_VERSIONS_TOML_FILE = "libs.versions.toml"
    val LIBS_FILE = "$GRADLE_DIR$separator$LIBS_VERSIONS_TOML_FILE"
    val PATH_GAP = "..$separator..$separator"
    val CONFIG_PATH = "${PATH_GAP}$CONFIG_FILE"
    val BUILD_FILE_PATH = "${PATH_GAP}$BUILD_FILE"
    val SETTINGS_FILE_PATH = "${PATH_GAP}$SETTINGS_FILE"
    val LIBS_VERSIONS_TOML_FILE_PATH = "${PATH_GAP}$GRADLE_DIR${separator}$LIBS_VERSIONS_TOML_FILE"
    val buildScriptListOfStringContained = listOf(
        """alias(libs.plugins.bakery)""".trimIndent(),
        """bakery { configPath = file("$CONFIG_FILE").absolutePath }""".trimIndent(),
    )
    val settingsListOfStringContained = listOf(
        "pluginManagement", "repositories",
         "gradlePluginPortal()",
        "rootProject.name", "bakery-test"
    )
    val tomlListOfStringContained = listOf(
        "[versions]",
        "[libraries]",
        "[plugins]",
        "[bundles]",
    )
    val configListOfStringContained = listOf(
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
    const val GRADLE_BUILD_CONTENT = """
plugins { alias(libs.plugins.bakery) }

bakery { configPath = file("site.yml").absolutePath }
            """

    const val DEPS = """
            [versions]
            bakery = "0.0.8"
     
            [libraries]

            [plugins]
            bakery = { id = "com.cheroliv.bakery", version.ref = "bakery" }

            [bundles]
        """
    const val JBAKE_TASK_SEQUENCE = """
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
     Documentation"""


}
