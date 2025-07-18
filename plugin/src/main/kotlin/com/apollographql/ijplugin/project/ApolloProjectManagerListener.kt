package com.apollographql.ijplugin.project

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.codegen.ApolloCodegenService
import com.apollographql.ijplugin.gradle.GradleToolingModelService
import com.apollographql.ijplugin.graphql.GraphQLConfigService
import com.apollographql.ijplugin.lsp.ApolloLspAppService
import com.apollographql.ijplugin.lsp.ApolloLspProjectService
import com.apollographql.ijplugin.settings.ProjectSettingsService
import com.apollographql.ijplugin.studio.fieldinsights.FieldInsightsService
import com.apollographql.ijplugin.telemetry.TelemetryService
import com.apollographql.ijplugin.util.isGradlePluginPresent
import com.apollographql.ijplugin.util.isKotlinPluginPresent
import com.apollographql.ijplugin.util.isLspAvailable
import com.apollographql.ijplugin.util.logd
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.application
import javax.swing.Action

class ApolloProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    logd()
    val descriptor = PluginManagerCore.getPlugin(PluginId.getId("com.intellij.lang.jsgraphql"))
    logd("com.intellij.lang.jsgraphql isEnabled=${descriptor?.isEnabled}")
    if (descriptor?.isEnabled == true) {
      runInEdt {
        IncompatiblePluginDialog(project).show()
      }
    }

    // Initialize all services on project open.
    // But wait for 'smart mode' to do it.
    // Most of these services can't operate without the Kotlin and Gradle plugins (e.g. in RustRover).
    DumbService.getInstance(project).runWhenSmart {
      logd("apolloVersion=" + project.apolloProjectService.apolloVersion)
      if (isKotlinPluginPresent && isGradlePluginPresent) {
        project.service<ApolloCodegenService>()
        project.service<GraphQLConfigService>()
        project.service<GradleToolingModelService>()
        project.service<ProjectSettingsService>()
        project.service<FieldInsightsService>()
        project.service<TelemetryService>()
      }
      if (isLspAvailable) {
        project.service<ApolloLspProjectService>()
        application.service<ApolloLspAppService>()
      }

      project.apolloProjectService.isInitialized = true
    }
  }
}

private class IncompatiblePluginDialog(private val project: Project) : DialogWrapper(project, true) {
  init {
    setTitle(ApolloBundle.message("incompatiblePluginDialog.title"))
    init()
  }

  override fun createActions(): Array<out Action?> {
    return arrayOf(okAction)
  }

  override fun doOKAction() {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, @Suppress("DialogTitleCapitalization") "preferences.pluginManager")
    super.doOKAction()
  }

  override fun createCenterPanel() = panel {
    row {
      text(ApolloBundle.message("incompatiblePluginDialog.text"))
    }
  }
}
