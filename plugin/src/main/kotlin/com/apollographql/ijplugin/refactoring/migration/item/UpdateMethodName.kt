package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtPsiFactory

class UpdateMethodName(
  private val className: String,
  private val oldMethodName: String,
  private val newMethodName: String,
) : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return findMethodReferences(project = project, className = className, methodName = oldMethodName).toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newMethodReference = KtPsiFactory(project).createExpression(newMethodName)
    element.replace(newMethodReference)
    return null
  }
}
