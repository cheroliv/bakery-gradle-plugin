#noinspection CucumberUndefinedStep
@cucumber @bakery
Feature: Minimal bakery configuration

  Scenario: Canary
    Given a new Bakery project
    When I am executing the task 'tasks'
    Then the build should succeed
