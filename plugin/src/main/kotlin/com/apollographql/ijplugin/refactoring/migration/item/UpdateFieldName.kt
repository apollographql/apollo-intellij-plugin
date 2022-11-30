package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findFieldReferences
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo

class UpdateFieldName(
  private val className: String,
  private val oldFieldName: String,
  private val newFieldName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    return findFieldReferences(project = project, className = className, fieldName = oldFieldName).map { UsageInfo(it) }
      .toTypedArray()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newFieldReference = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newFieldName, null)
    val fieldIdentifier = element.children.firstOrNull { it is PsiIdentifier && it.text == oldFieldName }
    if (fieldIdentifier != null) {
      // Java
      fieldIdentifier.replace(newFieldReference)
    } else {
      // Kotlin
      element.replace(newFieldReference)
    }
    return null
  }
}
