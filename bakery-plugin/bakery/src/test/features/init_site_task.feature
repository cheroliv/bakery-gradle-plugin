#noinspection CucumberUndefinedStep
@cucumber @bakery
Feature: The initSite task initialize the static site

  Scenario: `initSite` task against empty bakery project
    Given a new Bakery project
    And I add a buildScript file with 'site.yml' as the config path in the dsl
    And does not have 'site.yml' for site configuration
    And I add the gradle settings file with gradle portal dependencies repository
    And the gradle project does not have 'jbake.properties' file for site
    And the gradle project does not have 'index.html' file for maquette
    When I am executing the task 'initSite'
    Then the project should have a 'site.yml' file for site configuration
    Then the project should have a directory named 'site' who contains 'jbake.properties' file
    Then the project should have a directory named 'maquette' who contains 'index.html' file
    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'

  Scenario: `initSite` task against empty bakery project using gradle.properties
    Given a new Bakery project
    And with buildScript file without bakery dsl
    And does not have 'site.yml' for site configuration
    And I add the gradle settings file with gradle portal dependencies repository
    And the gradle project does not have 'jbake.properties' file for site
    And the gradle project does not have 'index.html' file for maquette
    And I add gradle.properties file with the entry bakery.config.path='site.yml'
    When I am executing the task 'initSite'
    Then the project should have a 'site.yml' file for site configuration
    Then the project should have a directory named 'site' who contains 'jbake.properties' file
    Then the project should have a directory named 'maquette' who contains 'index.html' file
    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'
