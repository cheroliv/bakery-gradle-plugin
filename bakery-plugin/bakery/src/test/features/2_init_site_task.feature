#noinspection CucumberUndefinedStep
@cucumber @bakery
Feature: The initSite task initialize the static site

  # TODO: failed scenarios and priority of DSL over gradle.properties

  # with DSL, without site.yml, without site, without maquette, without gradle.properties
  # gradle.properties : bakery.config.path='site.yml'
  Scenario: `initSite` task against empty bakery project
    Given a new Bakery project
    And 'build.gradle.kts' file use 'site.yml' as the config path in the DSL
    And does not have 'site.yml' for site configuration
    And 'settings.gradle.kts' set gradle portal dependencies repository with 'gradlePluginPortal'
    And the gradle project does not have 'site' directory for site
    And the gradle project does not have 'index.html' file for maquette
    And the output of the task 'tasks' contains 'initSite' from the group 'Bakery' and 'Initialise site and maquette folders.'
    When I am executing the task 'initSite'
    Then the project should have a 'site.yml' file for site configuration
    Then the project should have a directory named 'site' who contains 'jbake.properties' file
    Then the project should have a directory named 'maquette' who contains 'index.html' file
    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'
    Then after running 'initSite' the task is not available using 'site.yml' configuration

  # with DSL, with site.yml, without site, without maquette, without gradle.properties
  # gradle.properties : bakery.config.path='site.yml'
  Scenario: `initSite` task against an existing bakery project with DSL and configuration without site and maquette
    Given an existing empty Bakery project using DSL with 'site.yml' file
    And the output of the task 'tasks' contains 'initSite' from the group 'Bakery' and 'Initialise site and maquette folders.'
    And 'build.gradle.kts' file use 'site.yml' as the config path in the DSL
    And 'settings.gradle.kts' set gradle portal dependencies repository with 'gradlePluginPortal'
    And the gradle project does not have 'site' directory for site
    And the gradle project does not have 'index.html' file for maquette
    When I am executing the task 'initSite'
    Then after running 'initSite' the task is not available using 'site.yml' configuration
    Then the project should have a 'site.yml' file for site configuration
    Then the project should have a directory named 'site' who contains 'jbake.properties' file
    Then the project should have a directory named 'maquette' who contains 'index.html' file
    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'

  # without gradle.properties, without DSL, without site.yml, without site, without maquette
  # gradle.properties : bakery.config.path='site.yml'
  Scenario: `initSite` task against empty bakery project using gradle.properties
    Given a new Bakery project
    And with buildScript file without bakery DSL
    And does not have 'site.yml' for site configuration
    And 'settings.gradle.kts' set gradle portal dependencies repository with 'gradlePluginPortal'
    And the gradle project does not have 'site' directory for site
    And the gradle project does not have 'index.html' file for maquette
    And I add gradle.properties file with the entry bakery.config.path='site.yml'
    When I am executing the task 'initSite'
    Then the project should have a 'site.yml' file for site configuration
    Then the project should have a directory named 'site' who contains 'jbake.properties' file
    Then the project should have a directory named 'maquette' who contains 'index.html' file
    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'
    Then after running 'initSite' the task is not available using 'site.yml' configuration

#  avec gradle.property et site.yml qui existe mais sans site et maquette

#  Scenario: `initSite` task against an existing bakery project using gradle.properties without site and maquette
#    Given an existing empty Bakery project using DSL with 'site.yml' file
#    And with buildScript file without bakery DSL
#    And does not have 'site.yml' for site configuration
#    And 'settings.gradle.kts' set gradle portal dependencies repository with 'gradlePluginPortal'
#    And the gradle project does not have 'site' directory for site
#    And the gradle project does not have 'index.html' file for maquette
#    And I add gradle.properties file with the entry bakery.config.path='site.yml'
#    And the output of the task 'tasks' contains 'initSite' from the group 'Bakery' and 'Initialise site and maquette folders.'

#    Given an existing empty Bakery project using DSL with 'site.yml' file
#    And the output of the task 'tasks' contains 'initSite' from the group 'Bakery' and 'Initialise site and maquette folders.'
#    And 'build.gradle.kts' file use 'site.yml' as the config path in the DSL
#    And 'settings.gradle.kts' set gradle portal dependencies repository with 'gradlePluginPortal'
#    And the gradle project does not have 'site' directory for site
#    And the gradle project does not have 'index.html' file for maquette
#    When I am executing the task 'initSite'
#    Then after running 'initSite' the task is not available using 'site.yml' configuration
#    Then the project should have a 'site.yml' file for site configuration
#    Then the project should have a directory named 'site' who contains 'jbake.properties' file
#    Then the project should have a directory named 'maquette' who contains 'index.html' file
#    Then the project should have a file named '.gitignore' who contains 'site.yml', '.gradle', 'build' and '.kotlin'
#    Then the project should have a file named '.gitattributes' who contains 'eol' and 'crlf'

#    TODO: cli parameters, env variables, prompt scenarios
