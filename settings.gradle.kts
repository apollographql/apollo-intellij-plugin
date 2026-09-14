import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "apollo-intellij-plugin"

pluginManagement {
  repositories {
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    maven("https://storage.googleapis.com/apollo-previews/m2/")
    mavenCentral()
//    mavenLocal()
    gradlePluginPortal()
  }
}
plugins {
  id("com.gradle.develocity").version("4.0.2")
  id("com.gradle.common-custom-user-data-gradle-plugin").version("2.3")
  id("org.jetbrains.intellij.platform.settings").version("2.18.1")
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    maven("https://storage.googleapis.com/apollo-previews/m2/")
    mavenCentral()
    // IntelliJ Platform Gradle Plugin Repositories Extension
    // See https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
      defaultRepositories()
    }
  }
}

apply(from = "gradle/ge.gradle")

include(
    ":jsgraphql",
    ":plugin",
    ":test-project",
)
