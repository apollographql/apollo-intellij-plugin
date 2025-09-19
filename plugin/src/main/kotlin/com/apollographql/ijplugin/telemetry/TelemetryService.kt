package com.apollographql.ijplugin.telemetry

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.icons.ApolloIcons
import com.apollographql.ijplugin.project.ApolloProjectListener
import com.apollographql.ijplugin.project.ApolloProjectService.ApolloVersion
import com.apollographql.ijplugin.project.apolloProjectService
import com.apollographql.ijplugin.settings.AppSettingsListener
import com.apollographql.ijplugin.settings.AppSettingsState
import com.apollographql.ijplugin.settings.appSettingsState
import com.apollographql.ijplugin.settings.projectSettingsState
import com.apollographql.ijplugin.studio.fieldinsights.ApolloFieldInsightsInspection
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginAutomaticCodegenTriggering
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginContributeConfigurationToGraphqlPlugin
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginHasConfiguredGraphOsApiKeys
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginHighLatencyFieldThreshold
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginLspMode
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloIjPluginVersion
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloKotlinModuleCount
import com.apollographql.ijplugin.telemetry.TelemetryProperty.GradleModuleCount
import com.apollographql.ijplugin.telemetry.TelemetryProperty.IdeVersion
import com.apollographql.ijplugin.util.NOTIFICATION_GROUP_ID_TELEMETRY
import com.apollographql.ijplugin.util.cast
import com.apollographql.ijplugin.util.createNotification
import com.apollographql.ijplugin.util.getLibraryMavenCoordinates
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.util.application
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val DATA_PRIVACY_URL = "https://www.apollographql.com/docs/graphos/data-privacy/"

private const val SEND_PERIOD_MINUTES = 30L

@Service(Service.Level.PROJECT)
class TelemetryService(
    private val project: Project,
) : Disposable {

  var gradleModuleCount: Int? = null
  var apolloKotlinModuleCount: Int? = null
  var telemetryProperties: Set<TelemetryProperty> = emptySet()

  private val telemetryEventList: TelemetryEventList = TelemetryEventList()

  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private var sendTelemetryFuture: ScheduledFuture<*>? = null
  private var lastSentProperties: Set<TelemetryProperty>? = null

  init {
    logd("project=${project.name}")
    startObserveSettings()
    startObserveApolloProject()

    maybeShowTelemetryOptOutDialog()
    scheduleSendTelemetry()
  }

  private fun startObserveSettings() {
    logd()
    application.messageBus.connect(this).subscribe(AppSettingsListener.TOPIC, object : AppSettingsListener {
      var telemetryEnabled = appSettingsState.telemetryEnabled
      override fun settingsChanged(appSettingsState: AppSettingsState) {
        val telemetryEnabledChanged = telemetryEnabled != appSettingsState.telemetryEnabled
        telemetryEnabled = appSettingsState.telemetryEnabled
        logd("telemetryEnabledChanged=$telemetryEnabledChanged")
        if (telemetryEnabledChanged) {
          scheduleSendTelemetry()
        }
      }
    })
  }

  private fun startObserveApolloProject() {
    logd()
    project.messageBus.connect(this).subscribe(ApolloProjectListener.TOPIC, object : ApolloProjectListener {
      var apolloVersion = project.apolloProjectService.apolloVersion
      override fun apolloProjectChanged(apolloVersion: ApolloVersion) {
        val apolloVersionChanged = this.apolloVersion != apolloVersion
        this.apolloVersion = apolloVersion
        logd("apolloVersionChanged=$apolloVersionChanged")
        if (apolloVersionChanged) {
          scheduleSendTelemetry()
        }
      }
    })
  }

  fun logEvent(telemetryEvent: TelemetryEvent) {
    if (!appSettingsState.telemetryEnabled) return
    logd("telemetryEvent=$telemetryEvent")
    telemetryEventList.addEvent(telemetryEvent)
  }

  private fun buildTelemetrySession(): TelemetrySession {
    val properties = buildSet {
      addAll(project.getLibraryMavenCoordinates().toTelemetryProperties())
      addAll(telemetryProperties)
      gradleModuleCount?.let { add(GradleModuleCount(it)) }
      apolloKotlinModuleCount?.let { add(ApolloKotlinModuleCount(it)) }
      addAll(getIdeTelemetryProperties())
    }
    return TelemetrySession(
        instanceId = project.projectSettingsState.telemetryInstanceId,
        properties = properties,
        events = telemetryEventList.events,
    )
  }

  private fun getIdeTelemetryProperties(): Set<TelemetryProperty> = buildSet {
    var appName = ApplicationInfoEx.getInstanceEx().fullApplicationName
    ApplicationNamesInfo.getInstance().editionName?.let { edition ->
      appName += " ($edition)"
    }
    add(IdeVersion(appName))
    System.getProperties().getProperty("os.name")?.let { add(TelemetryProperty.IdeOS(it)) }
    PluginManagerCore.getPlugin(PluginId.getId("com.apollographql.ijplugin"))?.version?.let { add(ApolloIjPluginVersion(it)) }
    add(ApolloIjPluginAutomaticCodegenTriggering(project.projectSettingsState.automaticCodegenTriggering))
    add(ApolloIjPluginContributeConfigurationToGraphqlPlugin(project.projectSettingsState.contributeConfigurationToGraphqlPlugin))
    add(ApolloIjPluginHasConfiguredGraphOsApiKeys(project.projectSettingsState.apolloKotlinServiceConfigurations.isNotEmpty()))
    ProjectInspectionProfileManager.getInstance(project).currentProfile.getInspectionTool("ApolloFieldInsights", project)?.tool?.cast<ApolloFieldInsightsInspection>()
        ?.let {
          add(ApolloIjPluginHighLatencyFieldThreshold(it.thresholdMs))
        }
    add(ApolloIjPluginLspMode(appSettingsState.lspModeEnabled))
  }

  private fun scheduleSendTelemetry() {
    logd("telemetryEnabled=${appSettingsState.telemetryEnabled} apolloVersion=${project.apolloProjectService.apolloVersion}")
    sendTelemetryFuture?.cancel(true)
    if (!appSettingsState.telemetryEnabled) return
    if (project.apolloProjectService.apolloVersion == ApolloVersion.NONE) return
    sendTelemetryFuture = executor.scheduleAtFixedRate(::sendTelemetry, SEND_PERIOD_MINUTES, SEND_PERIOD_MINUTES, TimeUnit.MINUTES)
  }

  fun sendTelemetry() {
    val telemetrySession = buildTelemetrySession()
    logd("telemetrySession=$telemetrySession")
    val shouldSend = telemetrySession.events.isNotEmpty() || telemetrySession.properties != lastSentProperties
    if (!shouldSend) {
      logd("Telemetry data has not changed: not sending")
      return
    }
    try {
      runBlocking { executeTelemetryNetworkCall(telemetrySession) }
      lastSentProperties = telemetrySession.properties
      telemetryEventList.clear()
    } catch (e: Exception) {
      logw(e, "Could not send telemetry")
    }
  }

  private fun maybeShowTelemetryOptOutDialog() {
    if (appSettingsState.hasShownTelemetryOptOutDialog) return
    appSettingsState.hasShownTelemetryOptOutDialog = true
    createNotification(
        notificationGroupId = NOTIFICATION_GROUP_ID_TELEMETRY,
        title = ApolloBundle.message("telemetry.optOutDialog.title"),
        content = ApolloBundle.message("telemetry.optOutDialog.content"),
        type = NotificationType.INFORMATION,
        NotificationAction.create(ApolloBundle.message("telemetry.optOutDialog.optOut")) { _, notification ->
          appSettingsState.telemetryEnabled = false
          notification.expire()
        },
        NotificationAction.create(ApolloBundle.message("telemetry.optOutDialog.learnMore")) { _, _ ->
          BrowserUtil.browse(DATA_PRIVACY_URL, project)
        },
    )
        .apply {
          icon = ApolloIcons.Action.ApolloColor
        }
        .notify(project)
  }

  override fun dispose() {
    logd("project=${project.name}")
    executor.shutdown()
  }
}

val Project.telemetryService get() = service<TelemetryService>()
