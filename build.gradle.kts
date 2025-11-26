plugins {
  alias(libs.plugins.kotlin.jvm).apply(false)
  alias(libs.plugins.apollo).apply(false)
}

buildscript {
  dependencies {
    // See https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2062
    classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  }
}
