package com.apollographql.ijplugin.migration

import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import java.io.File

fun main() {
  val projectRootDir = File("/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/src/main/kotlin")
//  val projectRootDir = File("/Users/bod/gitrepo/apollo-kotlin-v3-sample-for-intellij-plugin/src/main/kotlin")
  val dirsToAnalyze = listOf(projectRootDir)
  val kotlinCoreEnvironment = createKotlinCoreEnvironment(
    dirsToAnalyze = dirsToAnalyze,
    classpath = listOf(
      "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/classes/java/main",
      "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/classes/kotlin/main",
      "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/app/build/resources/main",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-coroutines-support/2.5.13/17b37d884f177dceb7c7432c0f89b18cddf8bedb/apollo-coroutines-support-2.5.13.jar",
      "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/feature1/build/libs/feature1.jar",
      "/Users/bod/gitrepo/apollo-android-v2-sample-for-intellij-plugin/graphqlSchema/build/libs/graphqlSchema.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-runtime/2.5.13/de77cfcebe49afde6472535b7423d85053008bf5/apollo-runtime-2.5.13.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-http-cache-api/2.5.13/b83528a86f2c1df44b7f9c38e0b61ab33d49b388/apollo-http-cache-api-2.5.13.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-jvm/2.5.13/b3b722c52d4a625c69f30317514fac73e5542965/apollo-normalized-cache-jvm-2.5.13.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-normalized-cache-api-jvm/2.5.13/965db57dfba566c7acf1df9ad06c52466b0d0b8a/apollo-normalized-cache-api-jvm-2.5.13.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo/apollo-api-jvm/2.5.13/73b7b3a005a753e9d86d0d22c09fc6ae136728d6/apollo-api-jvm-2.5.13.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.5.0/d8cebccdcddd029022aa8646a5a953ff88b13ac8/kotlinx-coroutines-core-jvm-1.5.0.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.6.10/e80fe6ac3c3573a80305f5ec43f86b829e8ab53d/kotlin-stdlib-jdk8-1.6.10.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.6.10/e1c380673654a089c4f0c9f83d0ddfdc1efdb498/kotlin-stdlib-jdk7-1.6.10.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/3.12.11/301adbdad6ff9d613a2ba4772443560d25df5538/okhttp-3.12.11.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio/2.9.0/dcc813b08ce5933f8bdfd1dfbab4ad4bd170e7a/okio-jvm-2.9.0.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.benasher44/uuid-jvm/0.2.0/4f45ddff7626ca3342c80897afa235cdbf540ba2/uuid-jvm-0.2.0.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.6.10/b8af3fe6f1ca88526914929add63cf5e7c5049af/kotlin-stdlib-1.6.10.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.6.10/c118700e3a33c8a0d9adc920e9dec0831171925/kotlin-stdlib-common-1.6.10.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.nytimes.android/cache/2.0.2/dd9712ca1fffcb2d5dfbe2878c1b788be9bc7d76/cache-2.0.2.jar",
      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.google.code.findbugs/jsr305/3.0.1/f7be08ec23c21485b9b5a1cf1654c2ec8c58168d/jsr305-3.0.1.jar",

//      "/Users/bod/gitrepo/apollo-kotlin-v3-sample-for-intellij-plugin/build/classes/java/main",
//      "/Users/bod/gitrepo/apollo-kotlin-v3-sample-for-intellij-plugin/build/classes/kotlin/main",
//      "/Users/bod/gitrepo/apollo-kotlin-v3-sample-for-intellij-plugin/build/resources/main",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-rx3-support/3.6.2/31ef3c06bdcfea5b8e22aae7868830f55f25083/apollo-rx3-support-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-reactive/1.6.4/e35b77a7cb75319cc59d4a174c58739887c4aafc/kotlinx-coroutines-reactive-1.6.4.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-normalized-cache-sqlite-jvm/3.6.2/5d68a9e4a553856c8e597f32ec56e4fe76d36d10/apollo-normalized-cache-sqlite-jvm-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-normalized-cache-jvm/3.6.2/da9153b744752749f90e94c4d5d004e738b94fcf/apollo-normalized-cache-jvm-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-runtime-jvm/3.6.2/8c1170d3b46114475b08130ca0012d0d9f0618d/apollo-runtime-jvm-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4/2c997cd1c0ef33f3e751d3831929aeff1390cb30/kotlinx-coroutines-core-jvm-1.6.4.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-rx3/1.6.4/234ab28307f6066921667441519c44416e7fc231/kotlinx-coroutines-rx3-1.6.4.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.sqldelight/sqlite-driver/1.5.3/a800987b6e2a0cb7c05587c0599e271d1dc42d98/sqlite-driver-1.5.3.jar",
//      "/Users/bod/.m2/repository/com/squareup/okhttp3/okhttp/4.9.3/okhttp-4.9.3.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-normalized-cache-api-jvm/3.6.2/e15dae9e0e59b1434ec017d9de5d9e9a8154d037/apollo-normalized-cache-api-jvm-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-api-jvm/3.6.2/4d829704103a07b2c2115b913c108e93799ce816/apollo-api-jvm-3.6.2.jar",
//      "/Users/bod/.m2/repository/com/squareup/okio/okio/3.2.0/okio-3.2.0.jar",
//      "/Users/bod/.m2/repository/com/squareup/okio/okio-jvm/3.2.0/okio-jvm-3.2.0.jar",
//      "/Users/bod/.m2/repository/com/benasher44/uuid-jvm/0.3.1/uuid-jvm-0.3.1.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.sqldelight/jdbc-driver/1.5.3/f7dc41caacc914b6ca32768d9a1e618fabc104bd/jdbc-driver-1.5.3.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.squareup.sqldelight/runtime-jvm/1.5.3/c1a8319b11e8d6351b6800149793b72f7086dca5/runtime-jvm-1.5.3.jar",
//      "/Users/bod/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.7.20/kotlin-stdlib-jdk8-1.7.20.jar",
//      "/Users/bod/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.7.20/kotlin-stdlib-jdk7-1.7.20.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-jvm/0.17.0/d3d2e380b6ee28231118101d09ae6dd06a1db1cb/atomicfu-jvm-0.17.0.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-mpp-utils-jvm/3.6.2/b737b0ef03e5bc8db5ea728e375df8f3cee08b37/apollo-mpp-utils-jvm-3.6.2.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/com.apollographql.apollo3/apollo-annotations-jvm/3.6.2/56845f7f094157eb27cacd21f207e40fddc85c75/apollo-annotations-jvm-3.6.2.jar",
//      "/Users/bod/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.7.20/kotlin-stdlib-1.7.20.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/io.reactivex.rxjava3/rxjava/3.1.3/c0c522d8eaa17e7d00ed9a2b6e524f1ccf9413aa/rxjava-3.1.3.jar",
//      "/Users/bod/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-common/1.7.20/kotlin-stdlib-common-1.7.20.jar",
//      "/Users/bod/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar",
//      "/Users/bod/.m2/repository/org/reactivestreams/reactive-streams/1.0.3/reactive-streams-1.0.3.jar",
//      "/Users/bod/.gradle/caches/modules-2/files-2.1/org.xerial/sqlite-jdbc/3.34.0/fd29bb0124e3f79c80b2753162a6a3873c240bcf/sqlite-jdbc-3.34.0.jar",
    ),
    languageVersion = null,
    jvmTarget = JvmTarget.JVM_1_8,
    jdkHome = null,
  )
  val ktPsiFactory = createKtPsiFactory(kotlinCoreEnvironment)
  val ktFiles = dirsToAnalyze.flatMap { file ->
    file.walk()
      .filter { it.isFile }
      .filter { it.extension == "kt" || it.extension == "kts" }
  }
    .map { file ->
      ktPsiFactory.createPhysicalFile(fileName = file.path, text = file.readText())
    }

  val bindingContext = createBindingContext(kotlinCoreEnvironment, ktFiles)

  val migrationManager = MigrationManager(projectRootDir)
  migrationManager.addStep(
    object : KotlinFilesMigrationStep(migrationManager, ktPsiFactory, bindingContext) {
      override fun processKtFile(ktFile: KtFile) {
        ktFile.accept(
          object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
              super.visitCallExpression(expression)
              logd(expression.text + "/" + expression.calleeExpression?.text)
//              if (expression.calleeExpression?.text == "mutate") {
              logd(expression.getResolvedCall(bindingContext))
//              }
            }
          }
        )
      }
    }
  )

  migrationManager.process()

//  ktFile.accept(object: KtTreeVisitorVoid(){
//    override fun visitKtElement(element: KtElement) {
//      println(">"+element.text+"<")
//    }
//
//    override fun visitNamedFunction(function: KtNamedFunction) {
//      println("visitNamedFunction "+function.name)
//      function.replace(ktPsiFactory.createFunction("fun ${function.name}() = 1"))
//    }
//  })
//  println(ktFile.text)
//  println("Hello, world!")
}

