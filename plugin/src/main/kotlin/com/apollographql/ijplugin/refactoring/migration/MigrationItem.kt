package com.apollographql.ijplugin.refactoring.migration

import com.apollographql.ijplugin.refactoring.bindReferencesToElement
import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.apollographql.ijplugin.refactoring.findOrCreateClass
import com.apollographql.ijplugin.refactoring.findOrCreatePackage
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.migration.MigrationUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective

sealed interface MigrationItem {
  fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo>
  fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement?
}

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

class UpdateClassName(
  private val oldName: String,
  private val newName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    return MigrationUtil.findClassUsages(project, migration, oldName, searchScope)
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val newClass = findOrCreateClass(project, migration, newName)
    return element.bindReferencesToElement(newClass)
  }
}

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

class RemoveMethodCall(
  private val containingDeclarationName: String,
  private val methodName: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    return findMethodReferences(project = project, className = containingDeclarationName, methodName = methodName).map { UsageInfo(it) }
      .toTypedArray()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val importDirective = element.parentOfType<KtImportDirective>()
    if (importDirective != null) {
      // Reference is an import
      importDirective.delete()
    } else {
      val dotQualifiedExpression = element.parentOfType<KtDotQualifiedExpression>()
      if (dotQualifiedExpression != null) {
        // Reference is a method call
        // <some expression>.<await()> -> <some expression>
        dotQualifiedExpression.replace(dotQualifiedExpression.receiverExpression)
      }
    }
    return null
  }
}
