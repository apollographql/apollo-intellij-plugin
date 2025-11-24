package com.apollographql.ijplugin.action

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.telemetry.TelemetryEvent
import com.apollographql.ijplugin.util.showNotification
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import java.awt.datatransfer.StringSelection

class CopyOperationToClipboardAction : AbstractOperationAction(
    ApolloBundle.messagePointer("action.CopyOperationToClipboardAction.text"),
    AllIcons.General.Copy
) {
  override val telemetryEvent: TelemetryEvent = TelemetryEvent.ApolloIjCopyOperationToClipboard()

  override fun doAction(
      project: Project,
      contents: String,
      endpointUrl: String?,
      headers: Map<String, String>?,
      variablesJson: JsonObject?,
  ) {
    CopyPasteManager.getInstance().setContents(StringSelection(contents))
    showNotification(
        project = project,
        content = ApolloBundle.message("action.CopyOperationToClipboardAction.success.content"),
        type = NotificationType.INFORMATION
    )
  }
}
