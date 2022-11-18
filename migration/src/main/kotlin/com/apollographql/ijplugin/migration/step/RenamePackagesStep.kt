package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.logi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class RenamePackagesStep(
  migrationManager: MigrationManager,
  classpath: List<String>,
  private val packagesToRename: Collection<PackageName>,
) : KotlinFilesMigrationStep(migrationManager, classpath) {
  class PackageName(
    val oldName: String,
    val newName: String,
  )

  override fun processKtFile(ktFile: KtFile) {
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitImportList(importList: KtImportList) {
          super.visitImportList(importList)
          for (importDirective in importList.imports) {
            val importPath = importDirective.importPath ?: continue
            for (packageToRename in packagesToRename) {
              if (importPath.pathStr.startsWith(packageToRename.oldName)) {
                logi("Found package to rename: ${packageToRename.oldName} -> ${packageToRename.newName} in ${ktFile.name} at ${importDirective.textOffset}")
                val newFqName = FqName(importPath.fqName.asString().replaceFirst(packageToRename.oldName, packageToRename.newName))
                importDirective.replace(ktPsiFactory.createImportDirective(importPath.copy(newFqName)))
                break
              }
            }
          }
        }
      }
    )
  }
}
