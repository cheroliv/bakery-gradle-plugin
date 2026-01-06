plugins {
    `java-library`
    signing
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
    alias(libs.plugins.kotlin.jvm)
}

group = "com.cheroliv"
version = libs.plugins.bakery.get().version

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.20")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.junit.jupiter)
    api(libs.asciidoctorj.diagram)
    api(libs.asciidoctorj.diagram.plantuml)
    api(libs.jbake.gradle.plugin)
    api(libs.commons.io)
    api(libs.jgit.core)
    api(libs.jgit.ssh)
    api(libs.jgit.archive)
    api(libs.xz)

    // Cucumber dependencies
    testImplementation("io.cucumber:cucumber-java:7.33.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.33.0")
    testImplementation("io.cucumber:cucumber-picocontainer:7.33.0")
    testImplementation("org.junit.platform:junit-platform-suite:6.0.1")

}

kotlin.jvmToolchain(21)

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

val functionalTest: SourceSet by sourceSets.creating

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
}


configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
// Configuration des source sets pour Cucumber
sourceSets {
    test {
        resources {
            srcDir("src/test/features")
        }
        java {
            srcDir("src/test/scenarios")
        }
    }
}
// Tâche dédiée aux tests Cucumber
val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests"
    group = "verification"

    useJUnitPlatform {
        includeTags("cucumber")
    }

    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.plugin", "pretty, html:build/reports/cucumber.html, json:build/reports/cucumber.json")

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
gradlePlugin {
    plugins {
        create("bakery") {
            id = libs.plugins.bakery.get().pluginId
            implementationClass = "${libs.plugins.bakery.get().pluginId}.BakeryPlugin"
            displayName = "Bakery Plugin"
            description = "Gradle plugin for static site generation."
            tags.set(listOf("jbake", "static-site-generator", "blog", "jgit", "asciidoc", "markdown", "thymeleaf"))
        }
    }
    website = "https://cheroliv.com"
    vcsUrl = "https://github.com/cheroliv/bakery-gradle-plugin.git"
    testSourceSets(functionalTest)
}


java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("bakery").displayName)
                    description.set(gradlePlugin.plugins.getByName("bakery").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cheroliv")
                            name.set("cheroliv")
                            email.set("cheroliv.developer@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
    if (isReleaseVersion) {
        sign(publishing.publications)
    }
    useGpgCmd()
}

tasks.check {
    dependsOn(functionalTestTask)
    dependsOn(cucumberTest)
}
