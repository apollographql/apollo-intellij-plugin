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
