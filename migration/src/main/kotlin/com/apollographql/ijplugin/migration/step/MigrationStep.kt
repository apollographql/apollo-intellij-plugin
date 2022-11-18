package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.createBindingContext
import com.apollographql.ijplugin.migration.createKtPsiFactory
import com.apollographql.ijplugin.migration.logd
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.psi.KtFile
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
    for (file in migrationManager.projectRoot.getFilesWithExtension(fileExtensions)) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: $file")
      processFile(file)
    }
  }

  abstract fun processFile(file: File)
}

abstract class KotlinFilesMigrationStep(
  final override val migrationManager: MigrationManager,
  kotlinCoreEnvironment: KotlinCoreEnvironment,
) : MigrationStep {
  protected val ktPsiFactory = createKtPsiFactory(kotlinCoreEnvironment)
  private val ktFiles = migrationManager.projectRoot.getFilesWithExtension(setOf("kt")).map { file ->
    ktPsiFactory.createPhysicalFile(file.path, file.readText())
  }
  protected val bindingContext = createBindingContext(kotlinCoreEnvironment, ktFiles)

  override fun process() {
    for (ktFile in ktFiles) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: ${ktFile.virtualFilePath}")
      processKtFile(ktFile)
      logd("File processed")
      logd(ktFile.text)
    }
  }

  abstract fun processKtFile(ktFile: KtFile)
}

private fun File.getFilesWithExtension(extensions: Set<String>): List<File> {
  return walk().filter { it.isFile && it.extension in extensions }.toList()
}
