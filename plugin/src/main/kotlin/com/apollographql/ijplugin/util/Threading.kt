package com.apollographql.ijplugin.util

import com.intellij.openapi.application.ApplicationManager

fun <T> runWriteActionInEdt(action: () -> T): T {
  var result: T? = null
  ApplicationManager.getApplication().invokeLater {
    result = ApplicationManager.getApplication().runWriteAction<T>(action)
  }
  return result!!
}
