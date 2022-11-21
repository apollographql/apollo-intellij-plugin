package com.apollographql.ijplugin.migration

import com.apollographql.ijplugin.migration.util.getFilesWithExtension
import com.apollographql.ijplugin.migration.util.logd
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.mock.MockApplication
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPoint
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.kotlin.com.intellij.pom.PomModel
import org.jetbrains.kotlin.com.intellij.pom.PomModelAspect
import org.jetbrains.kotlin.com.intellij.pom.PomTransaction
import org.jetbrains.kotlin.com.intellij.pom.impl.PomTransactionBase
import org.jetbrains.kotlin.com.intellij.pom.tree.TreeAspect
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.impl.source.codeStyle.IndentHelper
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.TreeCopyHandler
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import sun.reflect.ReflectionFactory
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Adapted from https://github.com/detekt/detekt/blob/ec6cd40a6fa013c3f2dc4ce8343c4f9a1bd4d334/detekt-parser/src/main/kotlin/io/github/detekt/parser/KotlinEnvironmentUtils.kt
 * Licensed under the Apache License, Version 2.0 - https://github.com/detekt/detekt/blob/main/LICENSE
 */
class KotlinEnvironment(
  dirsToAnalyze: List<File>,
  classpath: List<String>,
) {
  private val kotlinCoreEnvironment = createKotlinCoreEnvironment(
    dirsToAnalyze = dirsToAnalyze,
    classpath = classpath,
    languageVersion = null,
    jvmTarget = JvmTarget.JVM_1_8,
    jdkHome = null
  )

  val ktPsiFactory = createKtPsiFactory(kotlinCoreEnvironment)

  val ktFiles = dirsToAnalyze.flatMap {
    it.getFilesWithExtension(setOf("kt")).map { file ->
      ktPsiFactory.createPhysicalFile(file.path, file.readText())
    }
  }
  val bindingContext by lazy {
    createBindingContext(kotlinCoreEnvironment, ktFiles)
  }

  private fun createKotlinCoreEnvironment(
    dirsToAnalyze: List<File>,
    classpath: List<String>,
    languageVersion: LanguageVersion?,
    jvmTarget: JvmTarget,
    jdkHome: Path?,
  ): KotlinCoreEnvironment {
    // https://github.com/JetBrains/kotlin/commit/2568804eaa2c8f6b10b735777218c81af62919c1
    setIdeaIoUseFallback()
    val configuration = createCompilerConfiguration(
      dirsToAnalyze = dirsToAnalyze,
      classpath = classpath,
      languageVersion = languageVersion,
      jvmTarget = jvmTarget,
      jdkHome = jdkHome,
    ).apply {
      put(
        CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
      )
      put(CommonConfigurationKeys.MODULE_NAME, "Apollo Migration")
    }

    KotlinCoreEnvironment.disposeApplicationEnvironment()
    val environment = KotlinCoreEnvironment.createForProduction(
      parentDisposable = Disposer.newDisposable(),
      configuration = configuration,
      configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
    val project = environment.project as MockProject
    project.registerService(PomModel::class.java, ApolloPomModel(project))


    val application = ApplicationManager.getApplication() as MockApplication
    if (application.getComponent(IndentHelper::class.java) == null) {
      application.registerService(IndentHelper::class.java, object : IndentHelper() {
        override fun getIndent(file: PsiFile, element: ASTNode): Int {
          return 4
        }

        override fun getIndent(file: PsiFile, element: ASTNode, includeNonSpace: Boolean): Int {
          return 4
        }
      })
    }
    return environment
  }

  private fun createKtPsiFactory(kotlinCoreEnvironment: KotlinCoreEnvironment): KtPsiFactory {
    return KtPsiFactory(kotlinCoreEnvironment.project, markGenerated = false)
  }

  private fun createCompilerConfiguration(
    dirsToAnalyze: List<File>,
    classpath: List<String>,
    languageVersion: LanguageVersion?,
    jvmTarget: JvmTarget,
    jdkHome: Path?,
  ): CompilerConfiguration {
    val javaFiles = dirsToAnalyze.flatMap { path ->
      path.walk()
        .filter { it.isFile && it.extension == "java" }
    }
    val kotlinFiles = dirsToAnalyze.flatMap { path ->
      path.walk()
        .filter { it.isFile }
        .filter { it.extension == "kt" || it.extension == "kts" }
        .map { it.absolutePath }
    }

    val classpathFiles = classpath.map { File(it) }
    val retrievedLanguageVersion = languageVersion ?: classpathFiles.getKotlinLanguageVersion()
    val languageVersionSettings: LanguageVersionSettings? = retrievedLanguageVersion?.let {
      LanguageVersionSettingsImpl(
        languageVersion = it,
        apiVersion = ApiVersion.createByLanguageVersion(it)
      )
    }

    return CompilerConfiguration().apply {
      if (languageVersionSettings != null) {
        put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)
      }
      put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
      addJavaSourceRoots(javaFiles)
      addKotlinSourceRoots(kotlinFiles)
      addJvmClasspathRoots(classpathFiles)

      jdkHome?.let { put(JVMConfigurationKeys.JDK_HOME, it.toFile()) }
      configureJdkClasspathRoots()
    }
  }

  private fun Iterable<File>.getKotlinLanguageVersion(): LanguageVersion? {
    val urls = map { it.toURI().toURL() }
    if (urls.isEmpty()) {
      return null
    }
    return URLClassLoader(urls.toTypedArray()).use { classLoader ->
      runCatching {
        val clazz = classLoader.loadClass("kotlin.KotlinVersion")
        val field = clazz.getField("CURRENT")
        field.isAccessible = true
        val versionObj = field[null]
        val versionString = versionObj?.toString()
        versionString?.let { LanguageVersion.fromFullVersionString(it) }
      }.getOrNull()
    }
  }

  /**
   * Adapted from https://github.com/pinterest/ktlint/blob/master/ktlint-core/src/main/kotlin/com/pinterest/ktlint/core/internal/KotlinPsiFileFactory.kt
   * Licenced under the MIT licence - https://github.com/pinterest/ktlint/blob/master/LICENSE
   */
  private class ApolloPomModel(project: Project) : UserDataHolderBase(), PomModel {
    init {
      val extension = "org.jetbrains.kotlin.com.intellij.treeCopyHandler"
      val extensionClass = TreeCopyHandler::class.java.name
      @Suppress("DEPRECATION")
      for (extensionArea in listOf(project.extensionArea, Extensions.getRootArea())) {
        // Addresses https://github.com/detekt/detekt/issues/4609
        synchronized(extensionArea) {
          if (!extensionArea.hasExtensionPoint(extension)) {
            extensionArea.registerExtensionPoint(
              extension,
              extensionClass,
              ExtensionPoint.Kind.INTERFACE
            )
          }
        }
      }
    }

    override fun runTransaction(transaction: PomTransaction) {
      val transactionCandidate = transaction as? PomTransactionBase

      val pomTransaction = requireNotNull(transactionCandidate) {
        "${PomTransactionBase::class.simpleName} type expected, actual is ${transaction.javaClass.simpleName}"
      }

      pomTransaction.run()
    }

    override fun <T : PomModelAspect?> getModelAspect(aspect: Class<T>): T? {
      if (aspect == TreeAspect::class.java) {
        val constructor = ReflectionFactory.getReflectionFactory()
          .newConstructorForSerialization(aspect, Any::class.java.getDeclaredConstructor())
        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance() as T
      }
      return null
    }
  }

  private fun createBindingContext(
    environment: KotlinCoreEnvironment,
    files: List<KtFile>,
  ): BindingContext {
    logd("Analyzing the project (may take a while)...")
    val messageCollector = object : MessageCollector by MessageCollector.NONE {
      override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        logd("KotlinCompiler - ${severity.presentableName} - ${location?.toString()}: $message")
      }
    }

    val analyzer = AnalyzerWithCompilerReport(
      messageCollector = messageCollector,
      languageVersionSettings = environment.configuration.languageVersionSettings,
      renderDiagnosticName = false,
    )
    analyzer.analyzeAndReport(files) {
      TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
        project = environment.project,
        files = files,
        trace = NoScopeRecordCliBindingTrace(),
        configuration = environment.configuration,
        packagePartProvider = environment::createPackagePartProvider,
        declarationProviderFactory = ::FileBasedDeclarationProviderFactory
      )
    }

    return analyzer.analysisResult.bindingContext
  }
}
