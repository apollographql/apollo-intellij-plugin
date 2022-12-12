package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.apollographql.ijplugin.util.containingKtFileImportList
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

class UpdateMethodName(
  private val className: String,
  private val oldMethodName: String,
  private val newMethodName: String,
  private val importToAdd: String? = null,
) : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val renameUsageInfos = findMethodReferences(project = project, className = className, methodName = oldMethodName)
      .toMigrationItemUsageInfo()
    val importUsageInfos = importToAdd?.let {
      renameUsageInfos.mapNotNull { renameUsageInfo ->
        renameUsageInfo.element?.containingKtFileImportList()?.let { importList ->
          AddImportUsageInfo(this@UpdateMethodName, importList)
        }
      }
    } ?: emptyList()
    return renameUsageInfos + importUsageInfos
  }

  private class AddImportUsageInfo(migrationItem: MigrationItem, element: KtImportList) : MigrationItemUsageInfo(migrationItem, element)

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val psiFactory = KtPsiFactory(project)
    when (usage) {
      is AddImportUsageInfo -> {
        if (importToAdd == null) return null
        val newImport = psiFactory.createImportDirective(ImportPath.fromString(importToAdd))
        element.add(newImport)
      }

      else -> {
        val newMethodReference = psiFactory.createExpression(newMethodName)
        element.replace(newMethodReference)
      }
    }
    return null
  }
}
