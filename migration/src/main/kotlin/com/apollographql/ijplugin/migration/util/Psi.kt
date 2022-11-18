package com.apollographql.ijplugin.migration.util

import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val DIRTY = Key<Boolean>("DIRTY")

fun PsiElement.change(element: PsiElement) {
  markContainingKtFileDirty()
  replace(element)
}

fun PsiElement.markContainingKtFileDirty() {
  containingKtFile()?.putUserData(DIRTY, true)
}

fun PsiElement.containingKtFile(): KtFile? = getStrictParentOfType()

fun KtFile.isDirty(): Boolean = getUserData(DIRTY) ?: false
