package com.cheroliv.bakery.scenarios

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.commons.annotation.Testable
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.Suite

/**
 * Test runner pour Cucumber
 * À placer dans src/test/scenarios/com/cheroliv/bakery/scenarios/
 */
@Suite
@Testable
@IncludeEngines("cucumber")
// Scanner tous les .feature du classpath
@ConfigurationParameter(
    key = FEATURES_PROPERTY_NAME,
    value = "classpath:"
)
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "com.cheroliv.bakery.scenarios"
)
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber.html, json:build/reports/cucumber.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "not @wip"
)
class CucumberTestRunner