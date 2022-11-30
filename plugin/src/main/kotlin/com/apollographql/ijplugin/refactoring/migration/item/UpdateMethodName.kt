package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo

class UpdateMethodName(
  private val className: String,
  private val oldMethodName: String,
  private val newMethodName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    return findMethodReferences(project = project, className = className, methodName = oldMethodName).map { UsageInfo(it) }
      .toTypedArray()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newMethodReference = JavaPsiFacade.getInstance(project).elementFactory.createExpressionFromText(newMethodName, null)
    val methodIdentifier = element.children.firstOrNull { it is PsiIdentifier && it.text == oldMethodName }
    if (methodIdentifier != null) {
      // Java
      methodIdentifier.replace(newMethodReference)
    } else {
      // Kotlin
      element.replace(newMethodReference)
    }
    return null
  }
}
