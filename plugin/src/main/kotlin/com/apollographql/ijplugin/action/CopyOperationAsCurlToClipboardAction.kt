package com.apollographql.ijplugin.action

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.icons.ApolloIcons
import com.apollographql.ijplugin.telemetry.TelemetryEvent
import com.apollographql.ijplugin.util.escapeShell
import com.apollographql.ijplugin.util.showNotification
import com.apollographql.ijplugin.util.toJsonString
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.datatransfer.StringSelection

class CopyOperationAsCurlToClipboardAction : AbstractOperationAction(
    ApolloBundle.messagePointer("action.CopyOperationAsCurlToClipboardAction.text"),
    ApolloIcons.Action.Curl
) {
  override val telemetryEvent: TelemetryEvent = TelemetryEvent.ApolloIjCopyOperationAsCurlToClipboard()

  override fun doAction(
      project: Project,
      contents: String,
      endpointUrl: String?,
      headers: Map<String, String>?,
      variablesJson: JsonObject?,
  ) {
    val curlCommand = buildString {
      append("curl -X POST \\\n")

      if (endpointUrl != null) {
        append("'${endpointUrl.escapeShell()}' \\\n")
      }

      append("-H 'content-type: application/json' \\\n")
      append("-H 'accept: multipart/mixed;incrementalSpec=v0.2, multipart/mixed;deferSpec=20220824, application/graphql-response+json, application/json' \\\n")
      if (!headers.isNullOrEmpty()) {
        for ((key, value) in headers) {
          append("-H '$key: ${value.escapeShell()}' \\\n")
        }
      }

      val body = graphQLRequestBody(
          query = contents,
          variables = variablesJson,
      ).toJsonString()
      append("-d '${body.escapeShell()}'")
    }

    CopyPasteManager.getInstance().setContents(StringSelection(curlCommand))
    showNotification(
        project = project,
        content = ApolloBundle.message("action.CopyOperationAsCurlToClipboardAction.success.content"),
        type = NotificationType.INFORMATION
    )
  }
}

private fun graphQLRequestBody(
    query: String,
    variables: JsonObject?,
): JsonObject {
  return JsonObject(
      buildMap {
        put("query", JsonPrimitive(query))
        if (!variables.isNullOrEmpty()) {
          put("variables", variables)
        }
      }
  )
}
