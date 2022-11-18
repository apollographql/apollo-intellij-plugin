package com.apollographql.ijplugin.migration

import com.apollographql.ijplugin.migration.step.RenameClassesStep
import com.apollographql.ijplugin.migration.step.RenameMethodsStep
import com.apollographql.ijplugin.migration.step.RenamePackagesStep
import java.io.File

fun main() {
  // TODO include test project in this project
  val projectRootDir = File("/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/src/main/kotlin")

  // TODO don't have these hard-coded
  val classpath = listOf(
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-coroutines-support/2.5.13/17b37d884f177dceb7c7432c0f89b18cddf8bedb/apollo-coroutines-support-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-runtime/2.5.13/de77cfcebe49afde6472535b7423d85053008bf5/apollo-runtime-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-http-cache-api/2.5.13/b83528a86f2c1df44b7f9c38e0b61ab33d49b388/apollo-http-cache-api-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-jvm/2.5.13/b3b722c52d4a625c69f30317514fac73e5542965/apollo-normalized-cache-jvm-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-api-jvm/2.5.13/965db57dfba566c7acf1df9ad06c52466b0d0b8a/apollo-normalized-cache-api-jvm-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-api-jvm/2.5.13/73b7b3a005a753e9d86d0d22c09fc6ae136728d6/apollo-api-jvm-2.5.13.jar",
  )

  val migrationManager = MigrationManager(projectRootDir, dryRun = false)
  migrationManager.addSteps(
    RenameMethodsStep(
      migrationManager = migrationManager,
      classpath = classpath,
      methodsToRename = setOf(
        RenameMethodsStep.MethodName("com.apollographql.apollo.ApolloClient", "mutate", "mutation"),
        RenameMethodsStep.MethodName("com.apollographql.apollo.ApolloClient", "subscribe", "subscription"),
      )
    ),

    RenameClassesStep(
      migrationManager = migrationManager,
      classpath = classpath,
      classesToRename = setOf(
        RenameClassesStep.ClassName("com.apollographql.apollo.api.Response", "ApolloResponse"),
      )
    ),

    // Renaming packages as the last step as previous steps depend on imports being correct
    RenamePackagesStep(
      migrationManager = migrationManager,
      classpath = classpath,
      packagesToRename = setOf(
        RenamePackagesStep.PackageName("com.apollographql.apollo", "com.apollographql.apollo3"),
      )
    ),
  )

  migrationManager.process()
}

