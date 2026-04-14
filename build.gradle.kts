plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.wlzn"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { "" }
        }

        changeNotes = """
            <ul>
                <li>支持多项目配置，可同时管理多个 Torna 项目的 Token</li>
                <li>推送时支持下拉选择目标项目，并缓存上次选择</li>
            </ul>
        """.trimIndent()
    }

    publishing {
        val localFile = rootProject.file("local.properties")
        val mToken = if (localFile.exists()) {
            localFile.readLines()
                .firstOrNull { it.startsWith("marketplaceToken=") }
                ?.substringAfter("=")?.trim().orEmpty()
        } else ""
        token = provider { mToken }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    prepareJarSearchableOptions {
        enabled = false
    }
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
