import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bakery)
}

bakery { configPath = file("site.yml").absolutePath }

tasks.named<Wrapper>("wrapper") {
    gradleVersion = libs.gradle.tooling.api.get().version
    distributionType = BIN
}