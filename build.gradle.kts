plugins {
    id("zenithproxy.plugin.dev") version "1.0.0-SNAPSHOT"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc = properties["mc"] as String

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version
    )
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
