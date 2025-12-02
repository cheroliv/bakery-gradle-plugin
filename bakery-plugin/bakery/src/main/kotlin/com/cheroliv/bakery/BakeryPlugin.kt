package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.createCnameFile
import com.cheroliv.bakery.FileSystemManager.from
import com.cheroliv.bakery.GitService.pushPages
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jbake.gradle.JBakeExtension
import org.jbake.gradle.JBakePlugin
import org.jbake.gradle.JBakeTask
import java.io.File
import java.io.File.separator


@Suppress("unused")
class BakeryPlugin : Plugin<Project> {
    companion object {
        private const val BAKERY_GROUP = "bakery"
        private const val BAKE_TASK = "bake"
        private const val ASCIIDOCTOR_OPTION_REQUIRES = "asciidoctor.option.requires"
        private const val ASCIIDOCTOR_DIAGRAM = "asciidoctor-diagram"
    }

    override fun apply(project: Project) {

        project.plugins.apply(JBakePlugin::class.java)
        val extension = project.extensions.create(
            BAKERY_GROUP,
            BakeryExtension::class.java
        )

        project.afterEvaluate {
            val site = from(project, extension.configPath.get())

            project.extensions.configure(JBakeExtension::class.java) {
                it.srcDirName = site.bake.srcPath
                it.destDirName = site.bake.destDirPath
                it.configuration[ASCIIDOCTOR_OPTION_REQUIRES] = ASCIIDOCTOR_DIAGRAM
                it.configuration["asciidoctor.attributes"] = arrayOf(
                    "sourceDir=${project.projectDir.resolve(site.bake.srcPath)}",
                    "imagesDir=diagrams",
//                    "imagesoutdir=${project.tasks.withType(JBakeTask::class.java).findByName(BAKE_TASK)?.input}/assets/diagrams"
                )
            }

            project.tasks.withType(JBakeTask::class.java)
                .getByName(BAKE_TASK)
                .input = project.file(site.bake.srcPath)

            project.tasks.register("publishSite") {
                it.run {
                    group = BAKERY_GROUP
                    description = "Publish site online."
                    dependsOn(BAKE_TASK)
                    doFirst { site.createCnameFile(project) }
                    doLast {
                        pushPages(
                            destPath = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.bake.destDirPath}" },
                            pathTo = { "${project.layout.buildDirectory.get().asFile.absolutePath}${separator}${site.pushPage.to}" },
                            site.pushPage,
                            project.logger
                        )
                    }
                }
            }

            project.tasks.register("publishMaquette") {
                it.run {
                    group = BAKERY_GROUP
                    description = "Publish maquette online."
                    val uiDir: File = project
                        .layout.projectDirectory.asFile
                        .resolve(site.pushMaquette.from)
                    val uiBuildDir: File = project
                        .layout.buildDirectory.asFile.get()
                        .resolve(site.pushMaquette.from)
                    val destDir = project
                        .layout.buildDirectory.get()
                        .asFile.resolve(site.pushMaquette.to)
                    doFirst {
                        if (!uiDir.exists()) throw IllegalStateException("$uiDir does not exist")
                        if (!uiDir.isDirectory) throw IllegalStateException("$uiDir should be a directory")
                        if (uiBuildDir.exists()) uiBuildDir.deleteRecursively()
                        if (!uiBuildDir.exists()) uiBuildDir.mkdirs()
                        if (!uiBuildDir.isDirectory) throw IllegalStateException("$uiBuildDir should be directory")
//                        uiDir.absolutePath.run(::println)
//                        uiBuildDir.run(::println)
                        uiDir.copyRecursively(uiBuildDir, true)
                    }
                    doLast {
                        pushPages(
                            destPath = { "$uiBuildDir" },
                            pathTo = { "$destDir" },
                            site.pushMaquette,
                            project.logger
                        )
                    }
                }
            }
        }
    }
}