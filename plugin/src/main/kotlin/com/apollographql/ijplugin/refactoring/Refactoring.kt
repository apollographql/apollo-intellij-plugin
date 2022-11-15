package com.apollographql.ijplugin.refactoring

import com.apollographql.ijplugin.util.logw
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor

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

fun findMethodReferences(project: Project, className: String, methodName: String): Collection<PsiReference> {
  val psiLookupClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)) ?: return emptyList()
  val methods = psiLookupClass.findMethodsByName(methodName, true)
  if (methods.isEmpty()) return emptyList()
  return methods.flatMap { method ->
    val processor = RenamePsiElementProcessor.forElement(method)
    processor.findReferences(methods[0], GlobalSearchScope.projectScope(project), false)
  }
}
