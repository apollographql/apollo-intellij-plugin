package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.bindReferencesToElement
import com.apollographql.ijplugin.refactoring.findOrCreatePackage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.migration.MigrationUtil
import com.intellij.usageView.UsageInfo

class UpdatePackageName(
  private val oldName: String,
  private val newName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    return MigrationUtil.findPackageUsages(project, migration, oldName, searchScope)
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newPackage = findOrCreatePackage(project, migration, newName)
    element.bindReferencesToElement(newPackage)
    return null
  }
}
