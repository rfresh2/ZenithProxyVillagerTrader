pluginManagement {
    repositories {
        maven("https://maven.2b2t.vc/releases")
        gradlePluginPortal()
    }
}

rootProject.name = ext.properties["plugin_name"] as String
