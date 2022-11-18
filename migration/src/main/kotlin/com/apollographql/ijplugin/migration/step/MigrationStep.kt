package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.KotlinEnvironment
import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.logd
import com.apollographql.ijplugin.migration.util.getFilesWithExtension
import com.apollographql.ijplugin.migration.util.isDirty
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
    for (file in migrationManager.projectRootDir.getFilesWithExtension(fileExtensions)) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: $file")
      processFile(file)
    }
  }

  abstract fun processFile(file: File)
}

abstract class KotlinFilesMigrationStep(
  final override val migrationManager: MigrationManager,
  private val kotlinEnvironment: KotlinEnvironment,
) : MigrationStep {
  protected val bindingContext: BindingContext
    get() = kotlinEnvironment.bindingContext

  protected val ktPsiFactory: KtPsiFactory
    get() = kotlinEnvironment.ktPsiFactory

  override fun process() {
    for (ktFile in kotlinEnvironment.ktFiles) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: ${ktFile.virtualFilePath}")
      processKtFile(ktFile)
      if (ktFile.isDirty()) {
        if (migrationManager.dryRun) {
          logd("Changed contents:")
          logd(ktFile.text)
        } else {
          File(ktFile.virtualFilePath).writeText(ktFile.text)
          logd("Wrote file: ${ktFile.virtualFilePath}")
        }
      } else {
        logd("No changes")
      }
    }
  }

  abstract fun processKtFile(ktFile: KtFile)
}

class CompositeKotlinFilesMigrationStep(
  migrationManager: MigrationManager,
  kotlinEnvironment: KotlinEnvironment,
  private val steps: List<KotlinFilesMigrationStep>,
) : KotlinFilesMigrationStep(migrationManager, kotlinEnvironment) {
  override fun processKtFile(ktFile: KtFile) {
    for (step in steps) {
      logd("Processing step: ${step.javaClass.simpleName}")
      step.processKtFile(ktFile)
    }
  }
}
