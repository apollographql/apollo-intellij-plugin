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
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val CODEGEN_GRADLE_TASK_NAME = "generateApolloSources"

class ApolloProjectServiceImpl(
  private val project: Project,
) : ApolloProjectService, Disposable {

  // TODO: This is initialized only once, but this could actually change during the project's lifecycle
  // TODO: find a way to invalidate this whenever project's dependencies change
  override val isApolloAndroid2Project by lazy { project.isApolloAndroid2Project }
  override val isApolloKotlin3Project by lazy { project.isApolloKotlin3Project }

  private val triggerCodegenExecutor = Executors.newSingleThreadScheduledExecutor()
  private var triggerCodegenFuture: ScheduledFuture<*>? = null

  init {
    logd("project=${project.name} isApolloKotlin3Project=$isApolloKotlin3Project")
    if (isApolloKotlin3Project) {
      observeVfsChanges()
      observeDocumentChanges()
    }
  }

  private fun observeDocumentChanges() {
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val vFile = PsiDocumentManager.getInstance(project).getPsiFile(event.document)?.virtualFile ?: return
        logd("vFile=${vFile.path}")
        val gradleModuleName = getGradleModuleNameForFile(vFile)
        if (gradleModuleName == null) {
          // Not a GraphQL file or could not get Gradle module name: ignore
          return
        }
        scheduleCodegen(setOf(gradleModuleName))
      }
    }, this)
  }

  private fun observeVfsChanges() {
    project.messageBus.connect().subscribe(
      VirtualFileManager.VFS_CHANGES,
      object : BulkFileListener {
        override fun before(events: MutableList<out VFileEvent>) {
          handleEvents(events.filterIsInstance<VFileDeleteEvent>())
        }

        override fun after(events: MutableList<out VFileEvent>) {
          handleEvents(events.filterNot { it is VFileDeleteEvent })
        }

        private fun handleEvents(events: List<VFileEvent>) {
          val gradleModuleNames = mutableSetOf<String>()
          for (event in events) {
            val vFile = event.file!!
            logd("vFile=${vFile.path}")
            val gradleModuleName = getGradleModuleNameForFile(vFile)
            if (gradleModuleName == null) {
              // Not a GraphQL file or could not get Gradle module name: ignore
              continue
            }
            gradleModuleNames += gradleModuleName
          }
          if (gradleModuleNames.isNotEmpty()) {
            scheduleCodegen(gradleModuleNames)
          }
        }
      },
    )
  }

  private fun getGradleModuleNameForFile(vFile: VirtualFile): String? {
    if (vFile.fileType !is GraphQLFileType) {
      // Only care for GraphQL files
      return null
    }
    val moduleForFile = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(vFile)
    if (moduleForFile == null) {
      // A file from an external project: ignore
      return null
    }
    return moduleForFile.getGradleName()
  }

  private fun scheduleCodegen(gradleModuleNames: Set<String>) {
    triggerCodegenFuture?.cancel(false)
    triggerCodegenFuture = triggerCodegenExecutor.schedule({ triggerCodegen(gradleModuleNames) }, 4, TimeUnit.SECONDS)
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
                ApplicationManager.getApplication().invokeLater {
                  ToolWindowManager.getInstance(project).activateEditorComponent()
                }
              }

              override fun onFailure() {
                logw("triggerCodegen onFailure")
                ApplicationManager.getApplication().invokeLater {
                  ToolWindowManager.getInstance(project).activateEditorComponent()
                }
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
    triggerCodegenExecutor.shutdownNow()
  }
}
