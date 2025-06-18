rootProject.name = "apollo-intellij-plugin"

@Suppress("UnstableApiUsage")
listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach {
  it.apply {
//    mavenLocal()
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    maven("https://storage.googleapis.com/apollo-previews/m2/")
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity").version("4.0.2")
  id("com.gradle.common-custom-user-data-gradle-plugin").version("2.3")
}

apply(from = "gradle/ge.gradle")

include(
    ":plugin",
    ":test-project",
)
