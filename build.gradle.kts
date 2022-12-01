fun properties(key: String) = project.findProperty(key).toString()

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.7.20" apply false
}

repositories {
  mavenCentral()
}
