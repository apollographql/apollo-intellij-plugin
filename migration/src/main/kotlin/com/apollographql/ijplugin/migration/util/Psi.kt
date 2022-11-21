package com.apollographql.ijplugin.migration.util

import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val DIRTY = Key<Boolean>("DIRTY")

fun PsiElement.replace(element: PsiElement, markDirty: Boolean) {
  if (markDirty) markContainingKtFileDirty()
  replace(element)
}

fun PsiElement.delete(markDirty: Boolean) {
  if (markDirty) markContainingKtFileDirty()
  delete()
}


fun PsiElement.markContainingKtFileDirty() {
  containingKtFile()?.putUserData(DIRTY, true)
}

fun PsiElement.containingKtFile(): KtFile? = getStrictParentOfType()

fun KtFile.isDirty(): Boolean = getUserData(DIRTY) ?: false
