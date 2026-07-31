package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.ApolloKotlinServiceListener
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.settings.ProjectSettingsListener
import com.apollographql.ijplugin.settings.ProjectSettingsState
import com.apollographql.ijplugin.settings.projectSettingsState
import com.apollographql.ijplugin.util.logd
import com.intellij.lang.jsgraphql.ide.config.GraphQLConfigProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.annotations.TestOnly

internal data class ApolloExternalSchemaSnapshot(
    val configuredSchemaPaths: List<String>,
    val externalSchemaRoots: Set<VirtualFile>,
    val watchRootPaths: List<String>,
    val watchRoots: List<VirtualFile>,
) {
  companion object {
    val EMPTY = ApolloExternalSchemaSnapshot(emptyList(), emptySet(), emptyList(), emptyList())
  }
}

/**
 *  Listens to availability of Tooling model, and notifies the GraphQL plugin.
 */
@Service(Service.Level.PROJECT)
class GraphQLConfigService(
    private val project: Project,
) : Disposable {
  @Volatile
  internal var externalSchemaSnapshot: ApolloExternalSchemaSnapshot = ApolloExternalSchemaSnapshot.EMPTY
    private set
  private var contributeConfigurationToGraphqlPlugin =
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin
  private val externalSchemaSnapshotRefreshScheduled = AtomicBoolean()
  private val configuredSchemaPathsRefreshRequested = AtomicBoolean()
  private val configurationReloadRequested = AtomicBoolean()
  private val watchRequestsLock = Any()
  private var watchRequests: Set<LocalFileSystem.WatchRequest> = emptySet()
  private var disposed = false

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
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        if (eventsAffectConfiguredSchemas(events, externalSchemaSnapshot.configuredSchemaPaths)) {
          scheduleExternalSchemaSnapshotRefresh(
              reloadConfiguration = false,
              refreshConfiguredSchemaPaths = false,
          )
        }
      }
    })
  }

  private fun apolloKotlinServicesAvailable() {
    scheduleExternalSchemaSnapshotRefresh(
        reloadConfiguration = true,
        refreshConfiguredSchemaPaths = true,
    )
  }

  private fun scheduleExternalSchemaSnapshotRefresh(
      reloadConfiguration: Boolean,
      refreshConfiguredSchemaPaths: Boolean,
  ) {
    if (reloadConfiguration) {
      configurationReloadRequested.set(true)
    }
    if (refreshConfiguredSchemaPaths) {
      configuredSchemaPathsRefreshRequested.set(true)
    }
    if (!externalSchemaSnapshotRefreshScheduled.compareAndSet(false, true)) return

    ApplicationManager.getApplication().invokeLater {
      externalSchemaSnapshotRefreshScheduled.set(false)
      if (project.isDisposed) return@invokeLater

      refreshExternalSchemaSnapshot(
          reloadConfiguration = configurationReloadRequested.getAndSet(false),
          refreshConfiguredSchemaPaths = configuredSchemaPathsRefreshRequested.getAndSet(false),
      )
    }
  }

  private fun refreshExternalSchemaSnapshot(
      reloadConfiguration: Boolean,
      refreshConfiguredSchemaPaths: Boolean,
  ) {
    val oldSnapshot = externalSchemaSnapshot
    val configuredSchemaPaths = if (refreshConfiguredSchemaPaths) {
      computeConfiguredSchemaPaths()
    } else {
      oldSnapshot.configuredSchemaPaths
    }
    val newSnapshot = computeExternalSchemaSnapshot(configuredSchemaPaths)
    val configuredSchemaPathsChanged = newSnapshot.configuredSchemaPaths != oldSnapshot.configuredSchemaPaths
    val rootsChanged = newSnapshot.externalSchemaRoots != oldSnapshot.externalSchemaRoots
    val watchRootPathsChanged = newSnapshot.watchRootPaths != oldSnapshot.watchRootPaths
    val watchRootsChanged = newSnapshot.watchRoots != oldSnapshot.watchRoots
    val snapshotChanged = configuredSchemaPathsChanged || rootsChanged || watchRootPathsChanged || watchRootsChanged

    if (snapshotChanged) {
      externalSchemaSnapshot = newSnapshot
      if (watchRootPathsChanged) {
        replaceWatchRequests(newSnapshot.watchRootPaths)
      }
      if (rootsChanged || watchRootsChanged) {
        WriteAction.run<Throwable> {
          AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
              project,
              null,
              oldSnapshot.externalSchemaRoots,
              newSnapshot.externalSchemaRoots,
              "Apollo external GraphQL schemas",
          )
        }
      }
    }
    if (reloadConfiguration || snapshotChanged) {
      scheduleConfigurationReload()
    }
  }

  private fun computeConfiguredSchemaPaths(): List<String> {
    if (!project.projectSettingsState.contributeConfigurationToGraphqlPlugin) return emptyList()

    val paths = project.apolloKotlinProjectModelService.getApolloKotlinServices()
        .flatMap { it.allSchemaPaths }
        .map { FileUtil.toSystemIndependentName(File(it).absolutePath) }
        .distinct()
        .sorted()
    return Collections.unmodifiableList(paths)
  }

  private fun computeExternalSchemaSnapshot(configuredSchemaPaths: List<String>): ApolloExternalSchemaSnapshot {
    if (configuredSchemaPaths.isEmpty()) return ApolloExternalSchemaSnapshot.EMPTY

    return runReadAction {
      val projectFileIndex = ProjectFileIndex.getInstance(project)
      val localFileSystem = LocalFileSystem.getInstance()
      val externalRoots = configuredSchemaPaths
          .mapNotNull(localFileSystem::findFileByPath)
          .filter { it.isValid && !it.isDirectory && !projectFileIndex.isInContent(it) }
          .sortedBy { it.path }
      val watchRootPaths = configuredSchemaPaths
          .mapNotNull { File(it).parentFile?.absolutePath }
          .map(FileUtil::toSystemIndependentName)
          .distinct()
          .filter { path ->
            val closestExistingFile = generateSequence(path) {
              File(it).parentFile?.absolutePath?.let(FileUtil::toSystemIndependentName)
            }.mapNotNull(localFileSystem::findFileByPath)
                .firstOrNull { it.isValid }
            closestExistingFile == null || !projectFileIndex.isInContent(closestExistingFile)
          }
          .sorted()
      val watchRoots = watchRootPaths
          .mapNotNull(localFileSystem::findFileByPath)
          .filter { it.isValid && it.isDirectory }
          .sortedBy { it.path }
      ApolloExternalSchemaSnapshot(
          configuredSchemaPaths = configuredSchemaPaths,
          externalSchemaRoots = Collections.unmodifiableSet(LinkedHashSet(externalRoots)),
          watchRootPaths = Collections.unmodifiableList(watchRootPaths),
          watchRoots = Collections.unmodifiableList(watchRoots),
      )
    }
  }

  private fun replaceWatchRequests(watchRootPaths: List<String>) {
    synchronized(watchRequestsLock) {
      if (disposed) return

      watchRequests = LocalFileSystem.getInstance().replaceWatchedRoots(
          watchRequests,
          watchRootPaths,
          null,
      )
    }
  }

  @TestOnly
  internal fun registeredWatchRootPaths(): Set<String> {
    return synchronized(watchRequestsLock) {
      watchRequests.mapTo(linkedSetOf()) { it.rootPath }
    }
  }

  private fun eventsAffectConfiguredSchemas(
      events: List<VFileEvent>,
      configuredSchemaPaths: List<String>,
  ): Boolean {
    if (configuredSchemaPaths.isEmpty()) return false

    return events.asSequence()
        .flatMap { it.topologyChangePaths().asSequence() }
        .map(FileUtil::toSystemIndependentName)
        .any { changedPath -> configuredSchemaPaths.any { FileUtil.isAncestor(changedPath, it, false) } }
  }

  private fun VFileEvent.topologyChangePaths(): List<String> {
    return when (this) {
      is VFileCreateEvent, is VFileCopyEvent, is VFileDeleteEvent -> listOf(path)
      is VFileMoveEvent -> listOf(oldPath, newPath)
      is VFilePropertyChangeEvent -> {
        if (propertyName == VirtualFile.PROP_NAME) listOf(oldPath, newPath) else emptyList()
      }

      else -> emptyList()
    }
  }

  private fun scheduleConfigurationReload() {
    logd("Calling scheduleConfigurationReload")
    project.service<GraphQLConfigProvider>().scheduleConfigurationReload()
  }

  override fun dispose() {
    logd("project=${project.name}")
    synchronized(watchRequestsLock) {
      disposed = true
      LocalFileSystem.getInstance().removeWatchedRoots(watchRequests)
      watchRequests = emptySet()
    }
  }
}
