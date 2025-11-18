import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.excludeCoroutines
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date

fun isReleaseBuild() = System.getenv("IJ_PLUGIN_RELEASE").toBoolean()

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij.platform)
  alias(libs.plugins.changelog)
  alias(libs.plugins.apollo)
}

repositories {
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    maven("https://storage.googleapis.com/apollo-previews/m2/")
  mavenCentral()
//  mavenLocal()

  intellijPlatform {
    defaultRepositories()
  }
}

group = "com.apollographql"

version = getVersionName()

// Use the global version defined in the root project + dedicated suffix if building a snapshot from the CI.
// For releases, remove the -SNAPSHOT suffix.
fun getVersionName(): String {
  val projectVersion = project.findProperty("VERSION_NAME").toString()
  return if (isReleaseBuild()) {
    projectVersion.removeSuffix("-SNAPSHOT")
  } else {
    projectVersion + ".${SimpleDateFormat("YYYY-MM-dd").format(Date())}." + System.getenv("GITHUB_SHA")?.take(7)
  }
}

kotlin {
  jvmToolchain(21)
}

// Copy specific dependencies to a directory visible to the unit tests.
// See ApolloTestCase.kt.
configurations {
  create("apolloDependencies")
}
dependencies {
  listOf(
      libs.apollo.annotations,
      libs.apollo.api,
      libs.apollo.runtime,
  ).forEach {
    add("apolloDependencies", it)
  }
}
tasks.register<Copy>("copyApolloDependencies") {
  from(configurations.getByName("apolloDependencies"))
  into(layout.buildDirectory.asFile.get().resolve("apolloDependencies"))
}

tasks {
  intellijPlatformTesting.runIde.register("runLocalIde") {
    // Use a custom IJ/AS installation. Set this property in your local ~/.gradle/gradle.properties file.
    // (for AS, it should be something like '/Applications/Android Studio.app/Contents')
    // See https://plugins.jetbrains.com/docs/intellij/android-studio.html#configuring-the-plugin-gradle-build-script
    providers.gradleProperty("apolloIntellijPlugin.ideDir").orNull?.let {
      localPath.set(file(it))
    }

    task {
      // Enables debug logging for the plugin
      systemProperty("idea.log.debug.categories", "Apollo")

      // Disable hiding frequent exceptions in logs (annoying for debugging). See com.intellij.idea.IdeaLogger.
      systemProperty("idea.logger.exception.expiration.minutes", "0")

      // Uncomment to disable internal mode - see https://plugins.jetbrains.com/docs/intellij/enabling-internal.html
      // systemProperty("idea.is.internal", "false")

      // Enable K2 mode (can't be done in the UI in sandbox mode - see https://kotlin.github.io/analysis-api/testing-in-k2-locally.html)
      systemProperty("idea.kotlin.plugin.use.k2", "true")

      jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Xmx4g")
      }
    }
  }

  withType<AbstractTestTask> {
    // Log tests
    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
      events.add(TestLogEvent.PASSED)
      events.add(TestLogEvent.FAILED)
      showStandardStreams = true
    }
    dependsOn("copyApolloDependencies")
    dependsOn(":test-project:generateApolloSources")
  }
}

// Setup fake JDK for maven dependencies to work
// See https://youtrack.jetbrains.com/issue/IJSDK-321
val mockJdkRoot = layout.buildDirectory.asFile.get().resolve("mockJDK")
tasks.register("downloadMockJdk") {
  val mockJdkRoot = mockJdkRoot
  doLast {
    val rtJar = mockJdkRoot.resolve("java/mockJDK-1.7/jre/lib/rt.jar")
    if (!rtJar.exists()) {
      rtJar.parentFile.mkdirs()
      rtJar.writeBytes(
          URI("https://github.com/JetBrains/intellij-community/raw/master/java/mockJDK-1.7/jre/lib/rt.jar").toURL()
              .openStream()
              .readBytes()
      )
    }
  }
}

tasks.test.configure {
  // Setup fake JDK for maven dependencies to work
  // See https://jetbrains-platform.slack.com/archives/CPL5291JP/p1664105522154139 and https://youtrack.jetbrains.com/issue/IJSDK-321
  // Use a relative path to make build caching work
  dependsOn("downloadMockJdk")
  systemProperty("idea.home.path", mockJdkRoot.relativeTo(project.projectDir).path)

  // Enable K2 mode - see https://kotlin.github.io/analysis-api/testing-in-k2-locally.html
  systemProperty("idea.kotlin.plugin.use.k2", "true")
}

apollo {
  service("apolloDebugServer") {
    packageName.set("com.apollographql.ijplugin.apollodebugserver")
    introspection {
      endpointUrl.set("http://localhost:12200/")
      schemaFile.set(file("src/main/graphql/schema.graphqls"))
    }
  }
}

dependencies {
  // IntelliJ Platform dependencies must be declared before the intellijPlatform block
  // See https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1784
  intellijPlatform {
    // See also https://blog.jetbrains.com/platform/2025/11/intellij-platform-2025-3-what-plugin-developers-should-know/
    intellijIdeaUltimate(libs.versions.intellij.platform.version.get()) {
      useCache = true
    }

    // Plugin dependencies
    // See https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html
    bundledPlugins(
        listOf(
            "com.intellij.java",
            "org.jetbrains.kotlin",
            "com.intellij.gradle",
            "org.toml.lang",
            "com.intellij.modules.json",
        )
    )

    // To find the version of a plugin relative to the platform version, see the plugin's page on the Marketplace,
    // e.g. for the Android plugin: https://plugins.jetbrains.com/plugin/22989-android/versions/stable
    plugins(
        listOf(
            "org.jetbrains.android:252.25557.131",
        )
    )

    pluginComposedModule(implementation(project(":jsgraphql")))

    // Uncomment the version if needing a specific one, e.g. if a regression is introduced in the latest version - see https://github.com/JetBrains/intellij-plugin-verifier/releases
    pluginVerifier(/*version = "1.385"*/)

    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.Java)

    zipSigner()
  }

  // Coroutines must be excluded to avoid a conflict with the version bundled with the IDE
  // See https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#coroutinesLibraries
  implementation(libs.apollo.gradle.plugin) {
    excludeCoroutines()
  }
  implementation(libs.apollo.ast)
  implementation(libs.apollo.tooling) {
    excludeCoroutines()
  }
  implementation(libs.apollo.normalized.cache.sqlite.classic)
  implementation(libs.sqlite.jdbc)
  implementation(libs.apollo.normalized.cache.sqlite.new) {
    excludeCoroutines()
  }
  implementation(libs.apollo.runtime) {
    excludeCoroutines()
  }
  implementation(libs.apollo.compiler)

  runtimeOnly(libs.slf4j.simple)

  testImplementation(libs.google.testparameterinjector)

  // Temporary workaround for https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1663
  // Should be fixed in platformVersion 2024.3.x
  testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

// IntelliJ Platform Gradle Plugin configuration
// See https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration
intellijPlatform {
  pluginConfiguration {
    id.set("com.apollographql.ijplugin")
    name.set("Apollo GraphQL")
    version.set(project.version.toString())
    ideaVersion {
      sinceBuild = libs.versions.sinceBuild.get()

      // No untilBuild specified, the plugin wants to be compatible with all future versions
      untilBuild = provider { null }
    }
    // Extract the <!-- Plugin description --> section from README.md and provide it to the plugin's manifest
    description.set(
        projectDir.resolve("../README.md").readText().lines().run {
          val start = "<!-- Plugin description -->"
          val end = "<!-- Plugin description end -->"

          if (!containsAll(listOf(start, end))) {
            throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
          }
          subList(indexOf(start) + 1, indexOf(end))
        }.joinToString("\n").run { markdownToHTML(this) }
    )
    changeNotes.set(
        if (isReleaseBuild()) {
          "See the <a href=\"https://github.com/apollographql/apollo-intellij-plugin/releases/tag/v${project.version}\">release notes</a>."
        } else {
          "Weekly snapshot builds contain the latest changes from the <code>main</code> branch."
        } +
            """
            <br><br>
            <quote>
            Note: in previous versions, the plugin had a dependency on the <a href="https://plugins.jetbrains.com/plugin/8097-graphql">JetBrains GraphQL plugin</a>.
            Starting with v5. we have forked and integrated that plugin’s code to allow us to implement certain Apollo specific features
            (e.g. `@link` support).<br>
            Since both plugins now handle `*.graphql` files, they can’t be used at the same time, and if you are upgrading the plugin from v4,
            the IDE will therefore ask you to choose which plugin to use.<br>
            Note that at the moment, all features of the GraphQL plugin are still present in the Apollo one, and we aim to backport bug fixes
            and new features.
            </quote>
            """.trimIndent()
    )
  }

  signing {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishing {
    token.set(System.getenv("PUBLISH_TOKEN"))
    if (!isReleaseBuild()) {
      // Read more: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#specifying-a-release-channel
      channels.set(listOf("snapshots"))
    }
  }

  pluginVerification {
    ides {
      recommended()
    }
    failureLevel.set(
        setOf(
            COMPATIBILITY_PROBLEMS,
            INTERNAL_API_USAGES,
            INVALID_PLUGIN,
            PLUGIN_STRUCTURE_WARNINGS,
        )
    )
  }
}
