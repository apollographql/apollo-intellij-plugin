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

include(
    ":plugin",
    ":test-project",
)
