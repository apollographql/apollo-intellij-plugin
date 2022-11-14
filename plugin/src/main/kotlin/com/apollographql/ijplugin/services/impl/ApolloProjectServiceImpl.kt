package com.apollographql.ijplugin.services.impl

import com.apollographql.ijplugin.services.ApolloProjectService
import com.apollographql.ijplugin.util.getGradleName
import com.apollographql.ijplugin.util.isApolloAndroid2Project
import com.apollographql.ijplugin.util.isApolloKotlin3Project
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.lang.jsgraphql.GraphQLFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.plugins.gradle.util.GradleConstants

private const val CODEGEN_GRADLE_TASK_NAME = "generateApolloSources"

class ApolloProjectServiceImpl(
  private val project: Project,
) : ApolloProjectService, Disposable {
  // TODO: This is initialized only once, but this could actually change during the project's lifecycle
  // TODO: find a way to invalidate this whenever project's dependencies change
  override val isApolloAndroid2Project by lazy { project.isApolloAndroid2Project }
  override val isApolloKotlin3Project by lazy { project.isApolloKotlin3Project }

  init {
    logd("project=${project.name} isApolloKotlin3Project=$isApolloKotlin3Project")
    if (isApolloKotlin3Project) observeVfsChanges()
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
            triggerCodegen(gradleModuleNames)
          }
        }
      },
    )
  }

  private fun triggerCodegen(gradleModuleNames: Set<String>) {
    logd("gradleModuleNames=$gradleModuleNames")
    with(ApplicationManager.getApplication()) {
      invokeLater {
        runWriteAction {
          stopCodegenIfOngoing()

          val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.basePath
            taskNames = gradleModuleNames.map { if (it == "") CODEGEN_GRADLE_TASK_NAME else ":$it:$CODEGEN_GRADLE_TASK_NAME" }
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
                logd()
              }

              override fun onFailure() {
                logw("triggerCodegen onFailure")
              }
            },
            ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            false,
          )
        }
      }
    }
  }

  private fun stopCodegenIfOngoing() {
    val allDescriptors = ExecutionManagerImpl.getAllDescriptors(project)
    logd("descriptors=$allDescriptors")
    for (descriptor in allDescriptors) {
      val processHandler = descriptor.processHandler
      if (processHandler is ExternalSystemProcessHandler
        && processHandler.executionName.contains(CODEGEN_GRADLE_TASK_NAME)
        && !processHandler.isProcessTerminated && !processHandler.isProcessTerminating
      ) {
        logd("Codegen is ongoing: stopping it")
        ExecutionManagerImpl.stopProcess(descriptor)
      }
    }
  }

  override fun dispose() {
    logd("project=${project.name}")
  }
}
