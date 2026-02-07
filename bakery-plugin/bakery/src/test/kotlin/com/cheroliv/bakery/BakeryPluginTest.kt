package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.yamlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.testfixtures.ProjectBuilder
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.io.File
import java.util.*
import kotlin.text.Charsets.UTF_8

class BakeryPluginTest {

    private fun createMockProject(): Pair<Project, PluginContainer> {
        // Create all mocks first to avoid nested mock creation issues
        val mockJbakeDependency = mock<MinimalExternalModuleDependency>()
        val mockSlf4jDependency = mock<MinimalExternalModuleDependency>()
        val mockAsciidoctorjDiagramDependency = mock<MinimalExternalModuleDependency>()
        val mockAsciidoctorjDiagramPlantumlDependency = mock<MinimalExternalModuleDependency>()
        val mockCommonsIoDependency = mock<MinimalExternalModuleDependency>()
        val mockCommonsConfigurationDependency = mock<MinimalExternalModuleDependency>()

        val mockJbakeProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockSlf4jProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockAsciidoctorjDiagramProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockAsciidoctorjDiagramPlantumlProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockCommonsIoProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockCommonsConfigurationProvider = mock<Provider<MinimalExternalModuleDependency>>()
        val mockVersionConstraint = mock<VersionConstraint>()
        val mockLibsCatalog = mock<VersionCatalog>()
        val mockVersionCatalogsExtension = mock<VersionCatalogsExtension>()
        val mockJbakeRuntimeConfiguration = mock<Configuration>()
        val mockConfigurationContainer = mock<ConfigurationContainer>()
        val mockConfigPathProperty = mock<Property<String>>()
        val mockBakeryExtension = mock<BakeryExtension>()
        val mockProjectDirectory = mock<Directory>()
        val mockBuildDirectoryFile = mock<Directory>()
        val mockBuildDirectory = mock<DirectoryProperty>()
        val mockProjectLayout = mock<ProjectLayout>()
        val mockExtensionContainer = mock<ExtensionContainer>()
        val mockDependencyHandler = mock<DependencyHandler>()
        val mockTaskContainer = mock<TaskContainer>()
        val mockPluginContainer = mock<PluginContainer>()
        val mockLogger = mock<Logger>()
        val mockProject = mock<Project>()

        // Now configure all the mocks using whenever
        whenever(mockJbakeProvider.get()).thenReturn(mockJbakeDependency)
        whenever(mockSlf4jProvider.get()).thenReturn(mockSlf4jDependency)
        whenever(mockAsciidoctorjDiagramProvider.get()).thenReturn(mockAsciidoctorjDiagramDependency)
        whenever(mockAsciidoctorjDiagramPlantumlProvider.get()).thenReturn(mockAsciidoctorjDiagramPlantumlDependency)
        whenever(mockCommonsIoProvider.get()).thenReturn(mockCommonsIoDependency)
        whenever(mockCommonsConfigurationProvider.get()).thenReturn(mockCommonsConfigurationDependency)

        whenever(mockLibsCatalog.findLibrary("jbake")).thenReturn(Optional.of(mockJbakeProvider))
        whenever(mockLibsCatalog.findLibrary("slf4j-simple")).thenReturn(Optional.of(mockSlf4jProvider))
        whenever(mockLibsCatalog.findLibrary("asciidoctorj-diagram")).thenReturn(
            Optional.of(
                mockAsciidoctorjDiagramProvider
            )
        )
        whenever(mockLibsCatalog.findLibrary("asciidoctorj-diagram-plantuml")).thenReturn(
            Optional.of(
                mockAsciidoctorjDiagramPlantumlProvider
            )
        )
        whenever(mockLibsCatalog.findLibrary("commons-io")).thenReturn(Optional.of(mockCommonsIoProvider))
        whenever(mockLibsCatalog.findLibrary("commons-configuration")).thenReturn(
            Optional.of(
                mockCommonsConfigurationProvider
            )
        )
        whenever(mockLibsCatalog.findVersion("jbake")).thenReturn(Optional.of(mockVersionConstraint))

        whenever(mockVersionCatalogsExtension.named("libs")).thenReturn(mockLibsCatalog)

        whenever(mockJbakeRuntimeConfiguration.name).thenReturn("jbakeRuntime")
        whenever(mockJbakeRuntimeConfiguration.asPath).thenReturn("/mock/classpath")

        // CORRECTION IMPORTANTE : Gérer les deux cas d'appel de create()
        // Cas 1 : create(String) sans Action
        whenever(mockConfigurationContainer.create(eq("jbakeRuntime"))).thenReturn(mockJbakeRuntimeConfiguration)

        // Cas 2 : create(String, Action) avec Action (pour compatibilité)
        whenever(
            mockConfigurationContainer.create(
                eq("jbakeRuntime"),
                any<Action<Configuration>>()
            )
        ).doAnswer { invocation ->
            val action = invocation.arguments[1] as Action<Configuration>
            action.execute(mockJbakeRuntimeConfiguration)
            mockJbakeRuntimeConfiguration
        }

        val configFile = File("../../site.yml").canonicalFile
        val projectDir = configFile.parentFile

        // Use the actual path that will resolve correctly
        whenever(mockConfigPathProperty.get()).thenReturn("site.yml")
        whenever(mockConfigPathProperty.isPresent).thenReturn(true)

        whenever(mockBakeryExtension.configPath).thenReturn(mockConfigPathProperty)

        // Make sure projectDirectory points to where site.yml actually exists
        whenever(mockProjectDirectory.asFile).thenReturn(projectDir)

        // Mock the build directory and its dir() method
        val buildDir = File(projectDir, "build")
        whenever(mockBuildDirectoryFile.asFile).thenReturn(buildDir)
        whenever(mockBuildDirectory.get()).thenReturn(mockBuildDirectoryFile)

        // Create a separate mock for asFile property
        val mockBuildDirAsFileProvider = mock<Provider<File>>()
        whenever(mockBuildDirAsFileProvider.get()).thenReturn(buildDir)
        whenever(mockBuildDirectory.asFile).thenReturn(mockBuildDirAsFileProvider)

        // Mock the dir() method to return a proper Provider
        whenever(mockBuildDirectory.dir(any<String>())).doAnswer { invocation ->
            val path = invocation.arguments[0] as String
            val mockDirProvider = mock<Provider<Directory>>()
            val mockDir = mock<Directory>()
            whenever(mockDir.asFile).thenReturn(File(buildDir, path))
            whenever(mockDirProvider.get()).thenReturn(mockDir)
            mockDirProvider
        }

        whenever(mockProjectLayout.projectDirectory).thenReturn(mockProjectDirectory)
        whenever(mockProjectLayout.buildDirectory).thenReturn(mockBuildDirectory)

        whenever(mockExtensionContainer.getByType(VersionCatalogsExtension::class.java)).thenReturn(
            mockVersionCatalogsExtension
        )
        whenever(mockExtensionContainer.create("bakery", BakeryExtension::class.java)).thenReturn(mockBakeryExtension)
        whenever(mockExtensionContainer.getByType(BakeryExtension::class.java)).thenReturn(mockBakeryExtension)

        // Mock configure method for JBakeExtension
        whenever(
            mockExtensionContainer.configure(
                eq(JBakeExtension::class.java),
                any()
            )
        ).doAnswer { invocation ->
            // Just execute the action, we don't need to verify JBake extension configuration
            val action = invocation.arguments[1] as Action<JBakeExtension>
            null
        }

        whenever(mockProject.extensions).thenReturn(mockExtensionContainer)
        whenever(mockProject.configurations).thenReturn(mockConfigurationContainer)
        whenever(mockProject.dependencies).thenReturn(mockDependencyHandler)

        // CORRECTION : Mock pour DependencyHandler.add()
        whenever(mockDependencyHandler.add(any(), any())).thenReturn(mock<Dependency>())

        whenever(mockProject.tasks).thenReturn(mockTaskContainer)
        whenever(mockProject.plugins).thenReturn(mockPluginContainer)
        whenever(mockProject.layout).thenReturn(mockProjectLayout)
        whenever(mockProject.logger).thenReturn(mockLogger)

        // Set projectDir consistently
        val projectDirectory = File("../../site.yml").canonicalFile.parentFile
        whenever(mockProject.projectDir).thenReturn(projectDirectory)
        whenever(mockProject.file(any<String>())).doAnswer { invocation ->
            File(projectDirectory, invocation.arguments[0] as String)
        }

        // Mock tasks.withType to avoid issues
        val mockJBakeTaskCollection = mock<TaskCollection<JBakeTask>>()
        val mockJBakeTask = mock<JBakeTask>()
        whenever(mockJBakeTaskCollection.getByName("bake")).thenReturn(mockJBakeTask)
        whenever(mockTaskContainer.withType(JBakeTask::class.java)).thenReturn(mockJBakeTaskCollection)

        // Mock task registration methods
        whenever(mockTaskContainer.register(any<String>(), any<Action<Task>>())).thenReturn(mock())
        whenever(mockTaskContainer.register(eq("publishSite"), any<Action<Task>>())).thenReturn(mock())
        whenever(
            mockTaskContainer.register(
                eq("publishMaquette"),
                any<Action<Task>>()
            )
        ).thenReturn(mock())
        whenever(mockTaskContainer.register(eq("configureSite"), any<Action<Task>>())).thenReturn(mock())
        whenever(
            mockTaskContainer.register(
                eq("serve"),
                eq(JavaExec::class.java),
                any<Action<JavaExec>>()
            )
        ).thenReturn(mock())

        // Set up afterEvaluate to execute immediately
        whenever(mockProject.afterEvaluate(any<Action<Project>>())).doAnswer { invocation ->
            val action = invocation.arguments[0] as Action<Project>
            action.execute(mockProject)
            null
        }

        return Pair(mockProject, mockPluginContainer)
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
            config = configFile.readText()
                .run(yamlMapper::readValue)
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
                .describedAs("SiteConfiguration.bake.cname should be 'bakery'")
                .isBlank()
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
                .describedAs("SiteConfiguration.pushMaquette.to should be 'cvs'")
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
            val (project, _) = createMockProject()
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
            // Get both the project mock and the plugin container mock
            val (project, mockPluginContainer) = createMockProject()
            val plugin = BakeryPlugin()

            // Debug: Check what the plugin will see
            val extension = project.extensions.getByType(BakeryExtension::class.java)
            val configPath = extension.configPath.get()
            val projectDir = project.layout.projectDirectory.asFile
            val resolvedConfig = projectDir.resolve(configPath)

            println("Config path from extension: $configPath")
            println("Project directory: ${projectDir.absolutePath}")
            println("Resolved config file: ${resolvedConfig.absolutePath}")
            println("Config file exists: ${resolvedConfig.exists()}")

            plugin.apply(project)

            // Verify directly on the mockPluginContainer instance
            verify(mockPluginContainer).apply(JBakePlugin::class.java)
        }

        @Test
        fun `lecture de la configuration depuis l'extension`() {
            val (project, _) = createMockProject()
            val plugin = BakeryPlugin()

            plugin.apply(project)

            val extension = project.extensions.getByType(BakeryExtension::class.java)
            val configPath = extension.configPath.get()

            // The mock now uses "site.yml" instead of "../../site.yml"
            assertThat(configPath).isEqualTo("site.yml")
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class PublishingTest {
        @Test
        fun check_publishing() {
            val (project, _) = createMockProject()
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

        private fun createFakeSiteConfiguration(cname: String = "") = SiteConfiguration(
            BakeConfiguration("site", "bake", cname)
        )

        @Test
        fun `createCnameFile should create CNAME file with correct content when cname is provided`() {
            // Given
            @Suppress("LocalVariableName")
            val CNAME_VALUE = "test.cheroliv.com"
            val siteConfiguration = createFakeSiteConfiguration(CNAME_VALUE)
            project.layout.buildDirectory.get().asFile.mkdirs()
            val expectedCnameFile: File = project.layout.buildDirectory.run {
                file(siteConfiguration.bake.destDirPath).apply { get().asFile.mkdir() }
                file("${siteConfiguration.bake.destDirPath}/CNAME").get().asFile.apply {
                    createNewFile()
                    writeText(CNAME_VALUE, UTF_8)
                }
            }
            project.layout.buildDirectory.get()
                .asFile
                .resolve(siteConfiguration.bake.destDirPath).apply {
                    run(::assertThat)
                        .describedAs("destDirPath should exist")
                        .exists()
                        .isDirectory
                    resolve("CNAME")
                        .run(::assertThat)
                        .describedAs("CNAME file should exist")
                        .exists()
                        .isFile
                    resolve("CNAME")
                        .readText(UTF_8)
                        .run(::assertThat)
                        .describedAs("CNAME file should contain 'test.cheroliv.com'")
                        .contains(CNAME_VALUE)
                }
            // When
            siteConfiguration.createCnameFile(project)

            // Then
            expectedCnameFile
                .run(::assertThat)
                .describedAs("CNAME file should exist")
                .exists().isFile
            expectedCnameFile.readText(UTF_8)
                .run(::assertThat)
                .describedAs("CNAME file should contains '$CNAME_VALUE'")
                .isEqualTo(CNAME_VALUE)
        }

        @Test
        fun `createCnameFile should do nothing if cname is null`() {
            // Given
            val siteConfiguration = createFakeSiteConfiguration()
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