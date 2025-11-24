package com.apollographql.ijplugin.action

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.icons.ApolloIcons
import com.apollographql.ijplugin.telemetry.TelemetryEvent
import com.apollographql.ijplugin.util.toJsonString
import com.apollographql.ijplugin.util.urlEncoded
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

class OpenInSandboxAction : AbstractOperationAction(
    ApolloBundle.messagePointer("action.OpenInSandboxAction.text"),
    ApolloIcons.Action.Apollo
) {
  override val telemetryEvent: TelemetryEvent = TelemetryEvent.ApolloIjOpenInApolloSandbox()

  override fun doAction(
      project: Project,
      contents: String,
      endpointUrl: String?,
      headers: Map<String, String>?,
      variablesJson: JsonObject?,
  ) {
    // See https://www.apollographql.com/docs/graphos/explorer/sandbox/#url-parameters
    val url = buildString {
      append("https://studio.apollographql.com/sandbox/explorer?document=${contents.urlEncoded()}")
      if (endpointUrl != null) {
        append("&endpoint=${endpointUrl.urlEncoded()}")
      }
      if (!variablesJson.isNullOrEmpty()) {
        append("&variables=${variablesJson.toJsonString().urlEncoded()}")
      }
      if (!headers.isNullOrEmpty()) {
        append("&headers=${headers.toJsonString().urlEncoded()}")
      }
    }
    BrowserUtil.browse(url, project)
  }
}

private fun Map<String, String>.toJsonString() = "{" + map { (key, value) -> """"$key": "$value"""" }.joinToString() + "}"
