fun properties(key: String) = project.findProperty(key).toString()

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.8.0" apply false
}

repositories {
  mavenCentral()
}
