plugins {
    id("zenithproxy.plugin.dev") version "1.0.1-SNAPSHOT"
}

group = property("maven_group") as String
version = property("plugin_version") as String
val mc = property("mc") as String
val pluginId = property("plugin_id") as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        // variables in your BuildConstants.java template class
        "version" to project.version,
        "mc_version" to mc,
        "plugin_id" to pluginId,
        "maven_group" to group as String,
    )
    // the minimum supported java version for users of your plugin
    javaReleaseVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases and Dependencies"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")
}
