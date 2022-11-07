package com.apollographql.ijplugin.services.impl

import com.apollographql.ijplugin.services.ApolloProjectService
import com.apollographql.ijplugin.util.getGradleName
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.jsgraphql.GraphQLFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.plugins.gradle.util.GradleConstants


class ApolloProjectServiceImpl(
  private val project: Project,
) : ApolloProjectService, Disposable {

  override val isApolloProject: Boolean by lazy {
    var isApolloProject = false
    ProjectRootManager.getInstance(project).orderEntries().librariesOnly().forEachLibrary { library ->
      if (library.name?.contains("com.apollographql.apollo3") == true) {
        isApolloProject = true
        false
      } else {
        true
      }
    }
    isApolloProject
  }

  init {
    logd("ApolloProjectServiceImpl init project=${project.name} isApolloProject=$isApolloProject")
    if (isApolloProject) observeVfsChanges()
  }

  private fun observeVfsChanges() {
    project.messageBus.connect().subscribe(
      VirtualFileManager.VFS_CHANGES,
      object : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
          val gradleModuleNames = mutableSetOf<String>()
          for (event in events) {
            val vFile = event.file!!
            if (vFile.fileType !is GraphQLFileType) {
              // Only care for GraphQL files
              continue
            }
            val moduleForFile = project.service<ProjectRootManager>().fileIndex.getModuleForFile(vFile)
            logd("moduleForFile=${moduleForFile}")
            if (moduleForFile == null) {
              // A file from an external project: ignore
              continue
            }
            val moduleGradleName = moduleForFile.getGradleName()
            logd("moduleGradleName=$moduleGradleName")
            if (moduleGradleName == null) {
              // Could not get Gradle module name: ignore
              continue
            }
            gradleModuleNames += moduleGradleName
          }
          if (gradleModuleNames.isNotEmpty()) {
            generateApolloSources(gradleModuleNames)
          }
        }
      },
    )
  }

  private fun generateApolloSources(gradleModuleNames: Set<String>) {
    logd("generateApolloSources gradleModuleNames=$gradleModuleNames")
    ApplicationManager.getApplication().runWriteAction {
      val taskSettings = ExternalSystemTaskExecutionSettings().apply {
        externalProjectPath = project.basePath
        taskNames = gradleModuleNames.map { if (it == "") "generateApolloSources" else "$it:generateApolloSources" }
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
      }
      logd("taskNames=${taskSettings.taskNames}")

      ExternalSystemUtil.runTask(
        taskSettings,
        DefaultRunExecutor.EXECUTOR_ID,
        project,
        GradleConstants.SYSTEM_ID,
        object : TaskCallback {
          override fun onSuccess() {
            logd("generateApolloSources onSuccess")
          }

          override fun onFailure() {
            logw("generateApolloSources onFailure")
          }
        },
        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
        false,
      )
    }
  }

  override fun dispose() {
    logd("ApolloProjectServiceImpl dispose project=${project.name}")
  }
}
