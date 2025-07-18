@file:JvmName("GraphQLNotificationUtil")

package com.intellij.lang.jsgraphql.ide.notifications

import com.intellij.lang.jsgraphql.GraphQLBundle
import com.intellij.lang.jsgraphql.GraphQLConstants
import com.intellij.lang.jsgraphql.GraphQLSettings
import com.intellij.lang.jsgraphql.ide.introspection.GraphQLIntrospectionService
import com.intellij.lang.jsgraphql.types.GraphQLException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.Nls
import javax.net.ssl.SSLException

const val GRAPHQL_NOTIFICATION_GROUP_ID = GraphQLConstants.GraphQL

fun showInvalidConfigurationNotification(
  message: String,
  configFile: VirtualFile?,
  project: Project
) {
  val notification = Notification(
    GRAPHQL_NOTIFICATION_GROUP_ID,
    GraphQLBundle.message("graphql.notification.invalid.config.file"),
    message,
    NotificationType.WARNING
  )
  if (configFile != null) {
    notification.addAction(
      object : NotificationAction(GraphQLBundle.message("graphql.notification.open.file", configFile.name)) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          FileEditorManager.getInstance(project).openFile(configFile, true)
        }
      })
  }
  Notifications.Bus.notify(notification)
}

fun showGraphQLRequestErrorNotification(
  project: Project,
  url: @NlsSafe String,
  error: Exception,
  notificationType: NotificationType,
  action: NotificationAction?
) {
  val isSSLError = error is SSLException
  val message = if (isSSLError)
    GraphQLBundle.message("graphql.notification.ssl.cert.error.title")
  else
    GraphQLBundle.message("graphql.notification.error.title")

  val notification = Notification(
    GRAPHQL_NOTIFICATION_GROUP_ID,
    message,
    "$url: ${formatExceptionMessage(error)}",
    notificationType
  )
  if (isSSLError) {
    GraphQLIntrospectionService.getInstance(project).createTrustAllHostsAction()?.let { notification.addAction(it) }
  }
  if (action != null) {
    notification.addAction(action)
  }
  Notifications.Bus.notify(notification, project)
}

fun addRetryFailedSchemaIntrospectionAction(
  notification: Notification,
  settings: GraphQLSettings,
  e: Exception,
  retry: Runnable
) {
  if (e !is GraphQLException) return

  notification.setContent(
    GraphQLBundle.message("graphql.notification.introspection.spec.error.body", formatExceptionMessage(e))
  )
  addRetryWithoutDefaultValuesAction(notification, settings, retry)
}

private fun addRetryWithoutDefaultValuesAction(
  notification: Notification,
  settings: GraphQLSettings,
  retry: Runnable
) {
  if (settings.isEnableIntrospectionDefaultValues) {
    // suggest retrying without the default values as they're a common cause of spec compliance issues
    val retryWithoutDefaultValues: NotificationAction = object : NotificationAction(
      GraphQLBundle.message("graphql.notification.retry.without.defaults")
    ) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        settings.isEnableIntrospectionDefaultValues = false
        ApplicationManager.getApplication().saveSettings()
        notification.expire()
        retry.run()
      }
    }
    notification.addAction(retryWithoutDefaultValues)
  }
}

@NlsSafe
fun formatExceptionMessage(throwable: Throwable): String {
  return StringUtil.decapitalize(ObjectUtils.coalesce(throwable.message, ""))
}
