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

    // Coroutines - IMPORTANT pour les tests asynchrones
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")

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
    testImplementation("org.junit.platform:junit-platform-suite:1.14.1")
}

kotlin.jvmToolchain(21)

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// 1. Créer le SourceSet functionalTest
val functionalTest: SourceSet by sourceSets.creating {
    java {
        srcDirs("src/functionalTest/kotlin")
    }
    resources {
        srcDirs("src/functionalTest/resources")
    }
}

// 2. Ajouter GradleTestKit à functionalTest (SANS hériter de testImplementation)
dependencies {
    add(functionalTest.implementationConfigurationName, gradleTestKit())
    add(functionalTest.implementationConfigurationName, kotlin("stdlib-jdk8"))
    add(functionalTest.implementationConfigurationName, kotlin("test-junit5"))

    // Ajouter les dépendances nécessaires explicitement
    add(functionalTest.implementationConfigurationName, "org.slf4j:slf4j-api:2.0.17")
    add(functionalTest.runtimeOnlyConfigurationName, "ch.qos.logback:logback-classic:1.5.20")
    add(functionalTest.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")

    // CORRECTION: Ajouter AssertJ pour les assertions
    add(functionalTest.implementationConfigurationName, libs.assertj.core)

    // Ajouter Mockito si nécessaire
    add(functionalTest.implementationConfigurationName, libs.mockito.kotlin)
    add(functionalTest.implementationConfigurationName, libs.mockito.junit.jupiter)
}

// 3. Tâche pour les tests fonctionnels
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output

    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// CORRECTION: Gérer les duplications de ressources pour functionalTest
tasks.named<ProcessResources>(functionalTest.processResourcesTaskName) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 4. Configurer les sources sets pour Cucumber (test standard)
sourceSets {
    test {
        resources {
            srcDir("src/test/features")
        }
        java {
            srcDir("src/test/scenarios")  // Steps dans scenarios/
        }
    }
}

// 5. Faire hériter testImplementation de functionalTest (pas l'inverse !)
configurations.named("testImplementation").configure {
    extendsFrom(configurations.named(functionalTest.implementationConfigurationName).get())
}

configurations.named("testRuntimeOnly").configure {
    extendsFrom(configurations.named(functionalTest.runtimeOnlyConfigurationName).get())
}

// 6. Ajouter les classes compilées de functionalTest au classpath de test
dependencies {
    testImplementation(functionalTest.output)
}

// 7. Tâche dédiée aux tests Cucumber
val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests"
    group = "verification"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            functionalTest.output

    useJUnitPlatform {
        // CORRECTION: Ne pas filtrer par tag ici, ça filtre les engines JUnit
        // Le filtrage des scénarios Cucumber se fait dans le runner via FILTER_TAGS_PROPERTY_NAME
        excludeEngines("junit-jupiter")
    }

    systemProperty("cucumber.junit-platform.naming-strategy", "long")

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    outputs.upToDateWhen { false }

    // S'assurer que functionalTest et main sont compilés avant
    dependsOn(functionalTest.classesTaskName)
    dependsOn(tasks.classes)
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