fun properties(key: String) = project.findProperty(key).toString()

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.7.22" apply false
}

repositories {
  mavenCentral()
}
