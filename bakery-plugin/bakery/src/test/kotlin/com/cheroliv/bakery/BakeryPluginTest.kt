package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.parseSiteConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.io.File
import java.util.*
import kotlin.text.Charsets.UTF_8

class BakeryPluginTest {

    private fun createMockProject(): Project {
        // Mock dependencies
        val mockJbakeDependency = mock<MinimalExternalModuleDependency>()
        val mockSlf4jDependency = mock<MinimalExternalModuleDependency>()
        val mockAsciidoctorjDiagramDependency = mock<MinimalExternalModuleDependency>()
        val mockAsciidoctorjDiagramPlantumlDependency = mock<MinimalExternalModuleDependency>()
        val mockCommonsIoDependency = mock<MinimalExternalModuleDependency>()
        val mockCommonsConfigurationDependency = mock<MinimalExternalModuleDependency>()

        // Mock Providers
        val mockJbakeProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockJbakeDependency
        }
        val mockSlf4jProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockSlf4jDependency
        }
        val mockAsciidoctorjDiagramProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockAsciidoctorjDiagramDependency
        }
        val mockAsciidoctorjDiagramPlantumlProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockAsciidoctorjDiagramPlantumlDependency
        }
        val mockCommonsIoProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockCommonsIoDependency
        }
        val mockCommonsConfigurationProvider = mock<Provider<MinimalExternalModuleDependency>> {
            on { get() } doReturn mockCommonsConfigurationDependency
        }

        // Mock VersionConstraint for version
        val mockVersionConstraint = mock<VersionConstraint>()

        // Mock VersionCatalog
        val mockLibsCatalog = mock<VersionCatalog> {
            on { findLibrary("jbake") } doReturn Optional.of(mockJbakeProvider)
            on { findLibrary("slf4j-simple") } doReturn Optional.of(mockSlf4jProvider)
            on { findLibrary("asciidoctorj-diagram") } doReturn Optional.of(mockAsciidoctorjDiagramProvider)
            on { findLibrary("asciidoctorj-diagram-plantuml") } doReturn Optional.of(
                mockAsciidoctorjDiagramPlantumlProvider
            )
            on { findLibrary("commons-io") } doReturn Optional.of(mockCommonsIoProvider)
            on { findLibrary("commons-configuration") } doReturn Optional.of(mockCommonsConfigurationProvider)
            on { findVersion("jbake") } doReturn Optional.of(mockVersionConstraint)
        }

        // Mock VersionCatalogsExtension
        val mockVersionCatalogsExtension = mock<VersionCatalogsExtension> {
            on { named("libs") } doReturn mockLibsCatalog
        }

        // Mock Configuration
        val mockJbakeRuntimeConfiguration = mock<Configuration> {
            on { name } doReturn "jbakeRuntime"
        }

        // Mock ConfigurationContainer
        val mockConfigurationContainer = mock<ConfigurationContainer> {
            on { create(eq("jbakeRuntime"), any<Action<Configuration>>()) } doAnswer { invocation ->
                val action = invocation.arguments[1] as Action<Configuration>
                action.execute(mockJbakeRuntimeConfiguration)
                mockJbakeRuntimeConfiguration
            }
        }

        // Mock BakeryExtension
        val mockConfigPathProperty = mock<Property<String>> {
            on { get() } doReturn File("../../site.yml").path
        }
        val mockBakeryExtension = mock<BakeryExtension> {
            on { configPath } doReturn mockConfigPathProperty
        }

        // Mock ExtensionContainer
        val mockExtensionContainer = mock<ExtensionContainer> {
            on { getByType(VersionCatalogsExtension::class.java) } doReturn mockVersionCatalogsExtension
            on { create("bakery", BakeryExtension::class.java) } doReturn mockBakeryExtension
            on { getByType(BakeryExtension::class.java) } doReturn mockBakeryExtension
        }

        // Mock DependencyHandler
        val mockDependencyHandler = mock<DependencyHandler>()

        // Mock TaskContainer (for registering tasks)
        val mockTaskContainer = mock<org.gradle.api.tasks.TaskContainer>()

        // Mock PluginContainer
        val mockPluginContainer = mock<org.gradle.api.plugins.PluginContainer>()

        // Mock Project
        val mockProject = mock<Project> {
            on { extensions } doReturn mockExtensionContainer
            on { configurations } doReturn mockConfigurationContainer
            on { dependencies } doReturn mockDependencyHandler
            on { tasks } doReturn mockTaskContainer
            on { plugins } doReturn mockPluginContainer
        }
        return mockProject
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class SiteConfigurationParsingTest {

        private lateinit var config: SiteConfiguration

        @BeforeAll
        fun `load and validate configuration before all tests`() {
            val configPath = "../../site.yml"
            val configFile = File(configPath)
            assertThat(configFile)
                .describedAs("Configuration file '%s' not found.", configPath)
                .exists()
            config = parseSiteConfiguration(configFile.readText())
        }

        @Test
        fun `check SiteConfiguration#bake properties`() {
            assertThat(config.bake).isNotNull()
            assertThat(config.bake.srcPath)
                .describedAs("SiteConfiguration.bake.srcPath should be 'site'")
                .isEqualTo("site")
            assertThat(config.bake.destDirPath)
                .describedAs("SiteConfiguration.bake.destDirPath should be 'bake'")
                .isEqualTo("bake")
            assertThat(config.bake.cname)
                .describedAs("SiteConfiguration.bake.cname should be 'cheroliv.com'")
                .isEqualTo("cheroliv.com")
        }

        @Test
        fun `check SiteConfiguration#pushPages properties`() {
            assertThat(config.pushPage.from)
                .describedAs("SiteConfiguration.pushPage.from should be 'bake'")
                .isEqualTo("bake")
            assertThat(config.pushPage.to)
                .describedAs("SiteConfiguration.pushPage.to should be 'cvs'")
                .isEqualTo("cvs")
            assertThat(config.pushPage.repo.name)
                .describedAs("SiteConfiguration.pushPage.repo.name should be 'thymeleaf.cheroliv.com'")
                .isEqualTo("thymeleaf.cheroliv.com")
            assertThat(config.pushPage.repo.repository)
                .describedAs("SiteConfiguration.pushPage.repo.repository should be 'https://github.com/pages-content/bakery.git'")
                .isEqualTo("https://github.com/pages-content/bakery.git")
            assertThat(config.pushPage.repo.credentials.username)
                .describedAs("SiteConfiguration.pushPage.repo.credentials.username should be 8 characters long")
                .hasSize(8)
            assertThat(config.pushPage.repo.credentials.password)
                .describedAs("SiteConfiguration.pushPage.repo.credentials.password should be 40 characters long")
                .hasSize(40)
            assertThat(config.pushPage.branch)
                .describedAs("SiteConfiguration.pushPage.branch should be 'main'")
                .isEqualTo("main")
            assertThat(config.pushPage.message)
                .describedAs("SiteConfiguration.pushPage.message should be 'thymeleaf.cheroliv.com'")
                .isEqualTo("thymeleaf.cheroliv.com")
        }

        @Test
        fun `check SiteConfiguration#pushMaquette properties`() {
            assertThat(config.pushMaquette.from)
                .describedAs("SiteConfiguration.pushMaquette.from should be 'maquette'")
                .isEqualTo("maquette")
            assertThat(config.pushMaquette.to)
                .describedAs("SiteConfiguration.pushMaquette.to should be 'maquette'")
                .isEqualTo("cvs")
            assertThat(config.pushMaquette.repo.name)
                .describedAs("SiteConfiguration.pushMaquette.repo.name should be 'cheroliv-maquette'")
                .isEqualTo("cheroliv-maquette")
            assertThat(config.pushMaquette.repo.repository)
                .describedAs("SiteConfiguration.pushMaquette.repo.repository should be 'https://github.com/pages-content/cheroliv-maquette.git'")
                .isEqualTo("https://github.com/pages-content/cheroliv-maquette.git")
            assertThat(config.pushMaquette.repo.credentials.username)
                .describedAs("SiteConfiguration.pushMaquette.repo.credentials.username should be 8 characters long")
                .hasSize(8)
            assertThat(config.pushMaquette.repo.credentials.password)
                .describedAs("SiteConfiguration.pushMaquette.repo.credentials.password should be 40 characters long")
                .hasSize(40)
            assertThat(config.pushMaquette.branch)
                .describedAs("SiteConfiguration.pushMaquette.branch should be 'main'")
                .isEqualTo("main")
            assertThat(config.pushMaquette.message)
                .describedAs("SiteConfiguration.pushMaquette.message should be 'cheroliv-maquette'")
                .isEqualTo("cheroliv-maquette")
        }

        @Test
        fun `check SiteConfiguration#supabase#project properties`() {
            assertThat(config.supabase!!.project.url)
                .describedAs("SiteConfiguration.supabase.project.url should be 40 characters long")
                .hasSize(40)
                .describedAs("SiteConfiguration.supabase.project.url should contains '.supabase.co'")
                .contains(".supabase.co")

            assertThat(config.supabase!!.project.publicKey)
                .describedAs("SiteConfiguration.supabase.project.url should be 208 characters long")
                .hasSize(208)
        }

        @Test
        fun `check SiteConfiguration#supabase#schema#contacts properties`() {
            assertThat(config.supabase!!.schema.contacts.name)
                .describedAs("SiteConfiguration.supabase.schema.contacts.name should be 'public.contacts")
                .isEqualTo("public.contacts")

            assertThat(config.supabase!!.schema.contacts.columns.map { it.name })
                .describedAs("SiteConfiguration.supabase.schema.contacts.columns should contains 'id', 'created_at', 'name', 'email', 'telephone'")
                .contains("id", "created_at", "name", "email", "telephone")

            assertThat(config.supabase!!.schema.contacts.rlsEnabled).isTrue
        }

        @Test
        fun `check SiteConfiguration#supabase#schema#messages properties`() {
            assertThat(config.supabase!!.schema.messages.name)
                .describedAs("SiteConfiguration.supabase.schema.messages.name should be 'public.messages")
                .isEqualTo("public.messages")

            assertThat(config.supabase!!.schema.messages.columns.map { it.name })
                .describedAs("SiteConfiguration.supabase.schema.messages.columns should contains 'id', 'created_at', 'contact_id', 'subject', 'message'")
                .contains("id", "created_at", "contact_id", "subject", "message")
        }

        @Test
        fun `check SiteConfiguration#supabase#rpc properties`() {
            assertThat(config.supabase!!.rpc.name)
                .describedAs("SiteConfiguration.supabase.rpc.name should be 'public.handle_contact_form'")
                .isEqualTo("public.handle_contact_form")

            assertThat(config.supabase!!.rpc.params.map { it.name })
                .describedAs("SiteConfiguration.supabase.rpc.params should contain 'p_name', 'p_email', 'p_subject', 'p_message'")
                .contains("p_name", "p_email", "p_subject", "p_message")
        }

        @Test
        fun `check SiteConfiguration#supabase#schema#contacts#column properties types are correctly mapped`() {
            val columns = config.supabase!!.schema.contacts.columns
            val expectedTypes = mapOf(
                "id" to "uuid",
                "created_at" to "timestamptz",
                "name" to "text",
                "email" to "text",
                "telephone" to "text"
            )

            assertThat(columns).hasSize(expectedTypes.size)

            expectedTypes.forEach { (name, type) ->
                val column = columns.find { it.name == name }
                assertThat(column)
                    .withFailMessage("Column with name '$name' not found.")
                    .isNotNull
                assertThat(column!!.type)
                    .withFailMessage("Expected column '$name' to have type '$type' but was '${column.type}'.")
                    .isEqualTo(type)
            }
        }

        @Test
        fun `check SiteConfiguration#supabase#schema#messages#column properties types are correctly mapped`() {
            val columns = config.supabase!!.schema.messages.columns
            val expectedTypes = mapOf(
                "id" to "uuid",
                "created_at" to "timestamptz",
                "contact_id" to "uuid",
                "subject" to "text",
                "message" to "text"
            )

            assertThat(columns).hasSize(expectedTypes.size)

            expectedTypes.forEach { (name, type) ->
                val column = columns.find { it.name == name }
                assertThat(column)
                    .withFailMessage("Column with name '$name' not found.")
                    .isNotNull
                assertThat(column!!.type)
                    .withFailMessage("Expected column '$name' to have type '$type' but was '${column.type}'.")
                    .isEqualTo(type)
            }
        }

        @Test
        fun `check SiteConfiguration#supabase#rpc#param properties types are correctly mapped`() {
            val params = config.supabase!!.rpc.params
            val expectedTypes = mapOf(
                "p_name" to "text",
                "p_email" to "text",
                "p_subject" to "text",
                "p_message" to "text",
            )

            assertThat(params).hasSize(expectedTypes.size)

            expectedTypes.forEach { (name, type) ->
                val param = params.find { it.name == name }
                assertThat(param)
                    .withFailMessage("Parameter with name '$name' not found.")
                    .isNotNull
                assertThat(param!!.type)
                    .withFailMessage("Expected parameter '$name' to have type '$type' but was '${param.type}'.")
                    .isEqualTo(type)
            }
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class ExtensionTest {

        @Test
        fun `plugin creates bakery extension`() {
            val project = createMockProject()
            val plugin = BakeryPlugin()

            plugin.apply(project)

            verify(project.extensions).create("bakery", BakeryExtension::class.java)
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class JbakeTest {

        @Test
        fun `plugin applies jbake gradle plugin`() {
            val project = createMockProject()
            val plugin = BakeryPlugin()
            plugin.apply(project)
            verify(project.plugins).apply(org.jbake.gradle.JBakePlugin::class.java)
        }


        @Test
        fun `lecture de la configuration depuis l'extension`() {
            val project = createMockProject()
            val plugin = BakeryPlugin()

            plugin.apply(project)

            val extension = project.extensions.getByType(BakeryExtension::class.java)
            val configPath = extension.configPath.get()

            assertThat(configPath).isEqualTo(File("../../site.yml").path)
        }


    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class PublishingTest {
        @Test
        fun check_publishing() {
            val project = createMockProject()
            val plugin = BakeryPlugin()
            plugin.apply(project)


        }
    }

    @Nested
    inner class FileSystemManagerTest {

        @TempDir
        lateinit var tempDir: File

        private lateinit var project: Project

        @BeforeEach
        fun `setup project`() {
            project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        }

        private fun createFakeSiteConfiguration(cname: String?) = SiteConfiguration(
            bake = BakeConfiguration(srcPath = "site", destDirPath = "bake", cname = cname),
            pushPage = GitPushConfiguration(
                from = "", to = "", repo = RepositoryConfiguration(
                    name = "", repository = "", credentials = RepositoryCredentials(username = "", password = "")
                ), branch = "", message = ""
            ),
            pushMaquette = GitPushConfiguration(
                from = "", to = "", repo = RepositoryConfiguration(
                    name = "", repository = "", credentials = RepositoryCredentials("", "")
                ), branch = "", message = ""
            )
        )

        @Test
        fun `createCnameFile should create CNAME file with correct content when cname is provided`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration("test.cheroliv.com")
            project.layout.buildDirectory.get().asFile.mkdirs()
            val expectedCnameFile = project.layout.buildDirectory.file(
                "${siteConfiguration.bake.destDirPath}/CNAME"
            ).get().asFile

            // When
            siteConfiguration.createCnameFile(project)

            // Then
            assertThat(expectedCnameFile).exists().isFile
            assertThat(expectedCnameFile.readText(UTF_8)).isEqualTo("test.cheroliv.com")
        }

        @Test
        fun `createCnameFile should do nothing if cname is null`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration(null)
            project.layout.buildDirectory.get().asFile.mkdirs()
            val cnameFile = project.layout.buildDirectory.file(
                "${siteConfiguration.bake.destDirPath}/CNAME"
            ).get().asFile

            // When
            siteConfiguration.createCnameFile(project)

            // Then
            assertThat(cnameFile).doesNotExist()
        }

        @Test
        fun `createCnameFile should do nothing if cname is blank`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration("   ")
            project.layout.buildDirectory.get().asFile.mkdirs()
            val cnameFile = project.layout.buildDirectory.file(
                "${siteConfiguration.bake.destDirPath}/CNAME"
            ).get().asFile

            // When
            siteConfiguration.createCnameFile(project)

            // Then
            assertThat(cnameFile).doesNotExist()
        }

        @Test
        fun `createCnameFile should overwrite existing CNAME file`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration("new.cheroliv.com")
            project.layout.buildDirectory.get().asFile.mkdirs()
            val cnameFile = project.layout.buildDirectory.file(
                "${siteConfiguration.bake.destDirPath}/CNAME"
            ).get().asFile
            cnameFile.parentFile.mkdirs()
            cnameFile.writeText("old.cheroliv.com", UTF_8)

            // When
            siteConfiguration.createCnameFile(project)

            // Then
            assertThat(cnameFile).exists().isFile
            assertThat(cnameFile.readText(UTF_8)).isEqualTo("new.cheroliv.com")
        }

        @Test
        fun `createCnameFile should replace existing CNAME directory`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration("another.cheroliv.com")
            project.layout.buildDirectory.get().asFile.mkdirs()
            val cnameFile = project.layout.buildDirectory.file(
                "${siteConfiguration.bake.destDirPath}/CNAME"
            ).get().asFile

            cnameFile.mkdirs()
            assertThat(cnameFile).exists().isDirectory

            // When
            siteConfiguration.createCnameFile(project)

            // Then
            assertThat(cnameFile).exists().isFile
            assertThat(cnameFile.readText(UTF_8)).isEqualTo("another.cheroliv.com")
        }
    }
}
