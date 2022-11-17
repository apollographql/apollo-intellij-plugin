package com.apollographql.ijplugin.migration

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

interface MigrationStep {
  val migrationManager: MigrationManager
  fun process()
}

abstract class ProjectFilesMigrationStep(
  override val migrationManager: MigrationManager,
  private val fileExtensions: Set<String>,
) : MigrationStep {
  override fun process() {
    migrationManager.projectRoot.walk().forEach { file ->
      if (file.isFile && file.extension in fileExtensions) {
        logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: $file")
        processFile(file)
      }
    }
  }

  abstract fun processFile(file: File)
}

abstract class KotlinFilesMigrationStep(
  override val migrationManager: MigrationManager,
  protected val ktPsiFactory: KtPsiFactory,
  protected val bindingContext: BindingContext,
) : ProjectFilesMigrationStep(migrationManager, setOf("kt", "kts")) {
  override fun processFile(file: File) {
    val ktFile: KtFile = ktPsiFactory.createPhysicalFile(fileName = file.path, text = file.readText())
    processKtFile(ktFile)
    if (!migrationManager.dryRun) {
      file.writeText(ktFile.text)
    } else {
      logd(ktFile.text)
    }
  }

  abstract fun processKtFile(ktFile: KtFile)
}
