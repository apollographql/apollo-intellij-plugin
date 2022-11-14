package com.apollographql.ijplugin.refactoring

import com.apollographql.ijplugin.util.logw
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo

fun findOrCreatePackage(project: Project, migration: PsiMigration, qName: String): PsiPackage {
  val aPackage = JavaPsiFacade.getInstance(project).findPackage(qName)
  return aPackage ?: WriteAction.compute<PsiPackage, RuntimeException> {
    migration.createPackage(qName)
  }
}

fun findOrCreateClass(project: Project, migration: PsiMigration, qName: String): PsiClass {
  val classes = JavaPsiFacade.getInstance(project).findClasses(qName, GlobalSearchScope.allScope(project))
  return classes.firstOrNull() ?: WriteAction.compute<PsiClass, RuntimeException> {
    migration.createClass(qName)
  }
}

fun findPackageUsages(project: Project, qName: String, searchScope: GlobalSearchScope): Array<UsageInfo> {
  return findRefs(
    element = JavaPsiFacade.getInstance(project).findPackage(qName),
    searchScope = searchScope
  )
}

fun findClassUsages(project: Project, qName: String, searchScope: GlobalSearchScope): Array<UsageInfo> {
  return findRefs(
    element = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project)),
    searchScope = searchScope
  )
}

private fun findRefs(element: PsiElement?, searchScope: GlobalSearchScope): Array<UsageInfo> {
  if (element == null) return emptyArray()
  return ReferencesSearch.search(element, searchScope, true)
    .filter { it.element.isWritable }
    .map { UsageInfo(it) }
    .toTypedArray()
}

fun PsiElement.bindReferencesToElement(element: PsiElement): PsiElement? {
  for (reference in references) {
    try {
      return reference.bindToElement(element)
    } catch (t: Throwable) {
      logw(t, "bindToElement failed: ignoring")
    }
  }
  return null
}
