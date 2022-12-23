plugins {
  id("org.jetbrains.kotlin.jvm")
}

group = "com.apollographql"
version = "1.0.0-alpha.01"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.7.21")
  implementation("org.slf4j:slf4j-simple:2.0.6")
}
