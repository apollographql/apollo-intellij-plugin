plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij.platform.module)
  alias(libs.plugins.grammarkit)
}

repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}

kotlin {
  jvmToolchain(21)
}

sourceSets {
  main {
    java.srcDirs("src/main/gen", "src/main/java")
  }
}

tasks {
  generateParser {
    purgeOldFiles.set(true)
    sourceFile.set(file("src/main/grammars/GraphQLParser.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("com/intellij/lang/jsgraphql/GraphQLParser.java")
    pathToPsiRoot.set("com/intellij/lang/jsgraphql/psi")
  }
  generateLexer {
    purgeOldFiles.set(false)
    sourceFile.set(file("src/main/grammars/GraphQLLexer.flex"))
    targetOutputDir.set(file("src/main/gen/com/intellij/lang/jsgraphql"))

    dependsOn("generateParser")
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaUltimate(libs.versions.intellij.platform.version.get()) {
      useCache = true
    }
    bundledPlugins(
        listOf(
            "org.jetbrains.kotlin",
            "com.intellij.modules.json",
            "org.jetbrains.plugins.yaml",
            "JavaScript",
            "org.intellij.intelliLang",
        )
    )
  }

  implementation(libs.cdimascio.dotenv)
  implementation(libs.atlassian.commonmark)
}
