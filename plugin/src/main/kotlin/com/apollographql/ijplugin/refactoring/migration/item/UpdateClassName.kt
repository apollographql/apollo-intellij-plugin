package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.bindReferencesToElement
import com.apollographql.ijplugin.refactoring.findOrCreateClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.migration.MigrationUtil
import com.intellij.usageView.UsageInfo

class UpdateClassName(
  private val oldName: String,
  private val newName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return MigrationUtil.findClassUsages(project, migration, oldName, searchScope).toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newClass = findOrCreateClass(project, migration, newName)
    return element.bindReferencesToElement(newClass)
  }
}
