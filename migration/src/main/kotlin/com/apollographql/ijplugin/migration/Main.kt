package com.apollographql.ijplugin.migration

import com.apollographql.ijplugin.migration.step.CompositeKotlinFilesMigrationStep
import com.apollographql.ijplugin.migration.step.RemoveMethodCallStep
import com.apollographql.ijplugin.migration.step.RenameClassesStep
import com.apollographql.ijplugin.migration.step.RenameMethodsStep
import com.apollographql.ijplugin.migration.step.RenamePackagesStep
import java.io.File

fun main() {
  // TODO include test project in this project
//  val projectRootDir = File("/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/src/main/kotlin")
//  val projectRootDir = File("/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin")
  val projectRootDir = File("/Users/bod/gitrepo/HedvigInsurance/app")

  // TODO don't have these hard-coded
  val classpath = listOf(
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-coroutines-support/2.5.13/17b37d884f177dceb7c7432c0f89b18cddf8bedb/apollo-coroutines-support-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-runtime/2.5.13/de77cfcebe49afde6472535b7423d85053008bf5/apollo-runtime-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-http-cache-api/2.5.13/b83528a86f2c1df44b7f9c38e0b61ab33d49b388/apollo-http-cache-api-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-jvm/2.5.13/b3b722c52d4a625c69f30317514fac73e5542965/apollo-normalized-cache-jvm-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-api-jvm/2.5.13/965db57dfba566c7acf1df9ad06c52466b0d0b8a/apollo-normalized-cache-api-jvm-2.5.13.jar",
    "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-api-jvm/2.5.13/73b7b3a005a753e9d86d0d22c09fc6ae136728d6/apollo-api-jvm-2.5.13.jar",

//  "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/classes/java/main",
//  "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/classes/kotlin/main",
//  "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/resources/main",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-coroutines-support/2.5.13/17b37d884f177dceb7c7432c0f89b18cddf8bedb/apollo-coroutines-support-2.5.13.jar",
//  "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/feature1/build/libs/feature1.jar",
//  "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/graphqlSchema/build/libs/graphqlSchema.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-runtime/2.5.13/de77cfcebe49afde6472535b7423d85053008bf5/apollo-runtime-2.5.13.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-http-cache-api/2.5.13/b83528a86f2c1df44b7f9c38e0b61ab33d49b388/apollo-http-cache-api-2.5.13.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-jvm/2.5.13/b3b722c52d4a625c69f30317514fac73e5542965/apollo-normalized-cache-jvm-2.5.13.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-api-jvm/2.5.13/965db57dfba566c7acf1df9ad06c52466b0d0b8a/apollo-normalized-cache-api-jvm-2.5.13.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-api-jvm/2.5.13/73b7b3a005a753e9d86d0d22c09fc6ae136728d6/apollo-api-jvm-2.5.13.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.5.0/d8cebccdcddd029022aa8646a5a953ff88b13ac8/kotlinx-coroutines-core-jvm-1.5.0.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.6.10/e80fe6ac3c3573a80305f5ec43f86b829e8ab53d/kotlin-stdlib-jdk8-1.6.10.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.6.10/e1c380673654a089c4f0c9f83d0ddfdc1efdb498/kotlin-stdlib-jdk7-1.6.10.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/3.12.11/301adbdad6ff9d613a2ba4772443560d25df5538/okhttp-3.12.11.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio/2.9.0/dcc813b08ce5933f8bdfd1dfbab4ad4bd170e7a/okio-jvm-2.9.0.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.benasher44/uuid-jvm/0.2.0/4f45ddff7626ca3342c80897afa235cdbf540ba2/uuid-jvm-0.2.0.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.6.10/b8af3fe6f1ca88526914929add63cf5e7c5049af/kotlin-stdlib-1.6.10.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.6.10/c118700e3a33c8a0d9adc920e9dec0831171925/kotlin-stdlib-common-1.6.10.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.nytimes.android/cache/2.0.2/dd9712ca1fffcb2d5dfbe2878c1b788be9bc7d76/cache-2.0.2.jar",
//  "/Users/bod/.gradle/caches/modules-2/files-2.1/com.google.code.findbugs/jsr305/3.0.1/f7be08ec23c21485b9b5a1cf1654c2ec8c58168d/jsr305-3.0.1.jar",


  )

  val kotlinEnvironment = KotlinEnvironment(
    dirsToAnalyze = listOf(projectRootDir),
    classpath = classpath,
  )

  val migrationManager = MigrationManager(projectRootDir, dryRun = true)
  migrationManager.addSteps(
    CompositeKotlinFilesMigrationStep(
      migrationManager = migrationManager,
      kotlinEnvironment = kotlinEnvironment,
      steps = listOf(
        RenameMethodsStep(
          migrationManager = migrationManager,
          kotlinEnvironment = kotlinEnvironment,
          methodsToRename = setOf(
            RenameMethodsStep.MethodName("com.apollographql.apollo.ApolloClient", "builder", "Builder"),
            RenameMethodsStep.MethodName("com.apollographql.apollo.ApolloClient", "mutate", "mutation"),
            RenameMethodsStep.MethodName("com.apollographql.apollo.ApolloClient", "subscribe", "subscription"),
          )
        ),

        RemoveMethodCallStep(
          migrationManager = migrationManager,
          kotlinEnvironment = kotlinEnvironment,
          methodCallsToRemove = setOf(
            RemoveMethodCallStep.MethodName("com.apollographql.apollo.coroutines", "await"),
          )
        ),

        RenameClassesStep(
          migrationManager = migrationManager,
          kotlinEnvironment = kotlinEnvironment,
          classesToRename = setOf(
            RenameClassesStep.ClassName("com.apollographql.apollo.api.Response", "ApolloResponse"),
          )
        ),

        // Renaming packages as the last step as previous steps depend on imports being correct
        RenamePackagesStep(
          migrationManager = migrationManager,
          kotlinEnvironment = kotlinEnvironment,
          packagesToRename = setOf(
            RenamePackagesStep.PackageName("com.apollographql.apollo", "com.apollographql.apollo3"),
          )
        ),
      )
    )
  )

  migrationManager.process()
}

