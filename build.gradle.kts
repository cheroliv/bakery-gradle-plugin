plugins { alias(libs.plugins.bakery) }

bakery { configPath = file("site.yml").absolutePath }
