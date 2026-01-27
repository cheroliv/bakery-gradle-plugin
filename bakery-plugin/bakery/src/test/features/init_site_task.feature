#noinspection CucumberUndefinedStep
@cucumber @bakery
Feature: Bakery plugin tests

  Scenario: Canary
    Given a new Bakery project
    When I am executing the task 'tasks'
    Then the build should succeed

  Scenario: initSite task without site template or configuration site file
    Given a new Bakery project
    And I add a buildScript file with 'site.yml' as the config path in the dsl
#    And the gradle project does not have 'site.yml'
#    And I add toml dependencies configuration file
#    And I add the gradle settings file with gradle portal dependencies repository
#    And the gradle project does not have site template folder or maquette folder
#    When I am executing the task 'initSite'
#    Then the gradle project folder should have a 'site.yml' file
#    Then the gradle project folder should have a site folder who contains jbake.properties file
#    Then the gradle project folder should have a maquette folder who contains index.html file
#
#  Scenario: initSite task without site template or configuration site file with gradle.properties configured using 'bakery.configPath'
#    Given a new empty folder for the project
#    And I add a buildScript file with 'site.yml' as the config path in the dsl
#    And I add a buildScript file without dsl to set the config path
#    And the gradle project does not have 'site.yml'
#    And I add toml dependencies configuration file
#    And I add the gradle settings file with gradle portal dependencies repository
#    And the gradle project does not have site template folder or maquette folder
#    When I am executing the task 'initSite'
#    Then the gradle project folder should have a 'site.yml' file
#    Then the gradle project folder should have a site folder who contains jbake.properties file
#    Then the gradle project folder should have a maquette folder who contains index.html file
