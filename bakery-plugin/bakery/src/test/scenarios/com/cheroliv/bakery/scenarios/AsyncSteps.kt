package com.cheroliv.bakery.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

class AsyncSteps(private val world: TestWorld) {

    @Given("a new Bakery project")
    fun createNewBakeryProject() {
        world.createGradleProject()
        assertThat(world.projectDir).exists()
    }

    @When("I am executing the task {string}")
    fun runTaskByName(taskName: String) = runBlocking {
        world.executeGradle(taskName)
    }

    @When("I'm launching the {string} task asynchronously")
    fun launchingAsyncTask(taskName: String) {
        world.executeGradleAsync(taskName)
            .run(::assertThat)
            .describedAs("The task '$taskName' should be successful")
            .isNotNull
    }

    @When("I am waiting for all asynchronous operations to complete")
    fun waitingEnd() = runBlocking {
        world.awaitAll()
    }

    @Then("the build should succeed")
    fun buildShouldSucceed() {
        assertThat(world.buildResult).isNotNull
    }
}
