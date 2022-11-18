package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.KotlinEnvironment
import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.logd
import com.apollographql.ijplugin.migration.util.getFilesWithExtension
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
    for (file in migrationManager.projectRoot.getFilesWithExtension(fileExtensions)) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: $file")
      processFile(file)
    }
  }

  abstract fun processFile(file: File)
}

abstract class KotlinFilesMigrationStep(
  final override val migrationManager: MigrationManager,
  private val classpath: List<String>,
) : MigrationStep {
  private lateinit var kotlinEnvironment: KotlinEnvironment

  protected val bindingContext: BindingContext
    get() = kotlinEnvironment.bindingContext

  protected val ktPsiFactory: KtPsiFactory
    get() = kotlinEnvironment.ktPsiFactory

  override fun process() {
    kotlinEnvironment = KotlinEnvironment(listOf(migrationManager.projectRoot), classpath)
    for (ktFile in kotlinEnvironment.ktFiles) {
      logd("Processing file${if (migrationManager.dryRun) " (dry run)" else ""}: ${ktFile.virtualFilePath}")
      val changed = processKtFile(ktFile)
      if (changed) {
        if (migrationManager.dryRun) {
          logd(ktFile.text)
        } else {
          File(ktFile.virtualFilePath).writeText(ktFile.text)
          logd("Wrote file: ${ktFile.virtualFilePath}")
        }
      }
    }
  }

  abstract fun processKtFile(ktFile: KtFile): Boolean
}
