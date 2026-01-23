#noinspection CucumberUndefinedStep
@cucumber @bakery
Feature: Tests asynchrones du plugin

  Scenario: Exécution synchrone
    Given un nouveau projet Gradle
    When j'exécute la tâche "tasks"
    Then le build devrait réussir

#bakery.configPath=
#  given and