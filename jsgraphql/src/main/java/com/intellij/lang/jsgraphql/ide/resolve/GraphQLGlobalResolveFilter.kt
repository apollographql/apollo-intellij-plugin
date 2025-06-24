package com.intellij.lang.jsgraphql.ide.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

interface GraphQLGlobalResolveFilter {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<GraphQLGlobalResolveFilter> =
      ExtensionPointName.create("com.apollographql.ijplugin.globalResolveFilter")

    @RequiresReadLock
    fun isGlobalResolveForcedFor(context: PsiFile): Boolean {
      return EP_NAME.extensionList.any { it.isGlobalResolveFor(context) }
    }
  }

  @RequiresReadLock
  fun isGlobalResolveFor(file: PsiFile): Boolean
}
