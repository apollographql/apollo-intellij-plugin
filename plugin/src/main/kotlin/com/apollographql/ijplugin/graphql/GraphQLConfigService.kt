package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.ApolloKotlinServiceListener
import com.apollographql.ijplugin.settings.ProjectSettingsListener
import com.apollographql.ijplugin.settings.ProjectSettingsState
import com.apollographql.ijplugin.settings.projectSettingsState
import com.apollographql.ijplugin.util.logd
import com.intellij.lang.jsgraphql.ide.config.GraphQLConfigProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.vfs.VirtualFile

/**
 *  Listens to availability of Tooling model, and notifies the GraphQL plugin.
 */
@Service(Service.Level.PROJECT)
class GraphQLConfigService(
    private val project: Project,
) : Disposable {
  private var publishedExternalSchemaRoots: Set<VirtualFile> = emptySet()
  private var contributeConfigurationToGraphqlPlugin =
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin

  init {
    logd("project=${project.name}")
    project.messageBus.connect(this).subscribe(ApolloKotlinServiceListener.TOPIC, object : ApolloKotlinServiceListener {
      override fun apolloKotlinServicesAvailable() {
        this@GraphQLConfigService.apolloKotlinServicesAvailable()
      }
    })
    project.messageBus.connect(this).subscribe(ProjectSettingsListener.TOPIC, object : ProjectSettingsListener {
      override fun settingsChanged(projectSettingsState: ProjectSettingsState) {
        val newValue = projectSettingsState.contributeConfigurationToGraphqlPlugin
        if (newValue == contributeConfigurationToGraphqlPlugin) return

        contributeConfigurationToGraphqlPlugin = newValue
        apolloKotlinServicesAvailable()
      }
    })
  }

  private fun apolloKotlinServicesAvailable() {
    val newRoots = ApolloExternalSchemaLibraryRootsProvider.externalSchemaRoots(project)
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater

      val oldRoots = publishedExternalSchemaRoots
      if (newRoots != oldRoots) {
        WriteAction.run<Throwable> {
          AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
              project,
              null,
              oldRoots,
              newRoots,
              "Apollo external GraphQL schemas",
          )
          publishedExternalSchemaRoots = newRoots
        }
      }
      scheduleConfigurationReload()
    }
  }

  private fun scheduleConfigurationReload() {
    logd("Calling scheduleConfigurationReload")
    project.service<GraphQLConfigProvider>().scheduleConfigurationReload()
  }

  override fun dispose() {
    logd("project=${project.name}")
  }
}
