package com.apollographql.ijplugin.project

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.codegen.ApolloCodegenService
import com.apollographql.ijplugin.gradle.ApolloKotlinProjectModelService
import com.apollographql.ijplugin.graphql.GraphQLConfigService
import com.apollographql.ijplugin.lsp.ApolloLspAppService
import com.apollographql.ijplugin.lsp.ApolloLspProjectService
import com.apollographql.ijplugin.settings.ProjectSettingsService
import com.apollographql.ijplugin.settings.appSettingsState
import com.apollographql.ijplugin.studio.fieldinsights.FieldInsightsService
import com.apollographql.ijplugin.telemetry.TelemetryService
import com.apollographql.ijplugin.util.isGradlePluginPresent
import com.apollographql.ijplugin.util.isKotlinPluginPresent
import com.apollographql.ijplugin.util.isLspAvailable
import com.apollographql.ijplugin.util.logd
import com.intellij.ide.lightEdit.project.LightEditDumbService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
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
    val dumbService = DumbService.getInstance(project)
    if (dumbService is LightEditDumbService) {
      // In LightEdit mode, we'll never be in 'smart mode', and calling runWhenSmart throws. So give up.
      logd("LightEdit mode detected, skipping service initialization.")
      return
    }
    dumbService.runWhenSmart {
      logd("apolloVersion=" + project.apolloProjectService.apolloVersion)
      if (isKotlinPluginPresent && isGradlePluginPresent) {
        project.service<ApolloCodegenService>()
        project.service<GraphQLConfigService>()
        project.service<ApolloKotlinProjectModelService>()
        project.service<ProjectSettingsService>()
        project.service<FieldInsightsService>()
        project.service<TelemetryService>()
      }
      if (isLspAvailable) {
        project.service<ApolloLspProjectService>()
        application.service<ApolloLspAppService>()

        switchToLspModeIfSupergraphYamlPresent(project)
      }

      project.apolloProjectService.isInitialized = true
    }
  }

  private fun switchToLspModeIfSupergraphYamlPresent(project: Project) {
    if (appSettingsState.lspModeEnabled) {
      // Already in LSP mode.
      return
    }

    val superGraphYamlFilePath = project.guessProjectDir()?.findChild("supergraph.yaml")?.path
    if (superGraphYamlFilePath != null) {
      logd("supergraph.yaml found at ${superGraphYamlFilePath}, switching to LSP mode")
      runWriteAction {
        appSettingsState.lspModeEnabled = true
      }
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
