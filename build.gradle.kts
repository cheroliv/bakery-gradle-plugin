import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.jvm)
    id("com.cheroliv.bakery") version libs.plugins.bakery.get().version
}

group = "com.cheroliv"
version = libs.plugins.bakery.get().version

bakery { configPath = file("site.yml").absolutePath }

dependencies {
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.ktor.client)
    testImplementation(libs.playwright)
    testImplementation(libs.supabase.postgrest)
    testImplementation(libs.supabase.storage)
    testImplementation(libs.supabase.jackson)
    testImplementation(libs.supabase.auth)
    testImplementation(libs.supabase.auth.jvm)

    listOf(
        kotlin("test"),
        gradleTestKit(),
        libs.junit.jupiter,
        libs.jackson.module.kotlin,
        libs.jackson.dataformat.yaml,
        libs.jackson.datatype.jsr310,
        libs.jackson.module.jsonSchema,
        libs.jackson.databind.yaml,
    ).forEach { testImplementation(it) }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = libs.gradle.tooling.api.get().version
    distributionType = BIN
}

tasks.withType<Test> {
    useJUnitPlatform()
//    dependsOn("serve")
//    finalizedBy("stopServe")
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED"
    )
    systemProperty("project.root.dir", project.rootDir.absolutePath)
    systemProperty("playwright.headless", "false")
    testLogging { showStandardStreams = true }
}

val functionalTest by sourceSets.creating