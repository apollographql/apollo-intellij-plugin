package com.apollographql.ijplugin.refactoring.migration

import com.apollographql.ijplugin.util.logd
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ApolloV2ToV3MigrationAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    logd()
    ApolloV2ToV3MigrationProcessor(e.project ?: return).run()
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    presentation.isEnabled = project != null
    presentation.isVisible = !ActionPlaces.isPopupPlace(event.place)
  }
}
