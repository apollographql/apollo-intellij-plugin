rootProject.name = "apollo-intellij-plugin"

@Suppress("UnstableApiUsage")
listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach {
  it.apply {
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    maven("https://storage.googleapis.com/apollo-previews/m2/")
    mavenCentral()
//    mavenLocal()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity").version("4.3")
  id("com.gradle.common-custom-user-data-gradle-plugin").version("2.3")
}

apply(from = "gradle/ge.gradle")

include(
    ":jsgraphql",
    ":plugin",
    ":test-project",
)
