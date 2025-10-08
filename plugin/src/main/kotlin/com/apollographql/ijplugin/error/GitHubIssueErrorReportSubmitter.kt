package com.apollographql.ijplugin.error

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.util.urlEncoded
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import java.awt.Component

class GitHubIssueErrorReportSubmitter : ErrorReportSubmitter() {
  override fun getReportActionText() = ApolloBundle.message("errorReport.actionText")

  override fun submit(
      events: Array<out IdeaLoggingEvent>,
      additionalInfo: String?,
      parentComponent: Component,
      consumer: Consumer<in SubmittedReportInfo>,
  ): Boolean {
    val event = events.firstOrNull()
    val eventMessage = event?.message?.let { "`$it`" } ?: "(no message)"
    val eventThrowable = event?.throwableText ?: "(no stack trace)"
    val exceptionClassName =
      event?.throwableText?.lines()?.firstOrNull()?.split(':')?.firstOrNull()?.split('.')?.lastOrNull()?.let { ": $it" }.orEmpty()
    val issueTitle = "Internal error${exceptionClassName}".urlEncoded()
    val ideNameAndVersion = ApplicationInfoEx.getInstanceEx().let { appInfo ->
      appInfo.fullApplicationName + "  " + "Build #" + appInfo.build.asString()
    }
    val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("com.apollographql.ijplugin"))?.version ?: "unknown"
    val properties = System.getProperties()
    val jdk = properties.getProperty("java.version", "unknown") +
        "; VM: " + properties.getProperty("java.vm.name", "unknown") +
        "; Vendor: " + properties.getProperty("java.vendor", "unknown")
    val os = SystemInfo.getOsNameAndVersion()
    val issueBody = """
      |An internal error happened.
      |
      |Message: $eventMessage
      |
      |### Environment
      |- Plugin version: $pluginVersion
      |- IDE: $ideNameAndVersion
      |- JDK: $jdk
      |- OS: $os
      |
      |### Additional information
      |${additionalInfo.orEmpty()}
      |
      |### Stack trace
      |```
      |$eventThrowable
      |```
    """.trimMargin().urlEncoded()
    val gitHubUrl = "https://github.com/apollographql/apollo-intellij-plugin/issues/new?labels=bug&title=${issueTitle}&body=${issueBody}"
        .substring(0..<8192)
    BrowserUtil.browse(gitHubUrl)
    consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
    return true
  }
}
