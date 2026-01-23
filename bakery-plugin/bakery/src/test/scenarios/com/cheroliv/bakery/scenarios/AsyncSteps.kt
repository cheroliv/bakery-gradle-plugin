package com.cheroliv.bakery.scenarios

import io.cucumber.java.en.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

class AsyncSteps(private val world: TestWorld) {

    @Given("a new Gradle project")
    fun unNouveauProjetGradle() {
        world.createGradleProject()
        assertThat(world.projectDir).exists()
    }

    @When("I am executing the task {string}")
    fun jExecuteLaTache(taskName: String) = runBlocking {
        world.executeGradle(taskName)
    }

    @When("I'm launching the {string} task asynchronously")
    fun jeLanceTacheAsync(taskName: String) {
        @Suppress("DeferredResultUnused")
        world.executeGradleAsync(taskName)
    }

    @When("I am waiting for all asynchronous operations to complete")
    fun jAttendsFinOperations() = runBlocking {
        world.awaitAll()
    }

    @Then("the build should succeed")
    fun leBuildDevraitReussir() {
        assertThat(world.buildResult).isNotNull
    }
}
