package com.intellij.lang.jsgraphql.schema.library

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.stream.Collectors

class GraphQLLibraryManager(private val myProject: Project) {
  private val myLibraries: MutableMap<GraphQLLibraryDescriptor?, GraphQLLibrary> =
    ConcurrentHashMap<GraphQLLibraryDescriptor?, GraphQLLibrary>()
  private val myLibrariesChangeTriggered = AtomicBoolean()
  private val myAsyncRefreshRequested = AtomicBoolean()

  @Volatile
  private var myLibrariesEnabled = !ApplicationManager.getApplication().isUnitTestMode

  private val myKnownLibraryRoots = ClearableLazyValue.createAtomic(
      Supplier {
        this.allLibraries
            .stream()
            .flatMap { library: SyntheticLibrary -> library.sourceRoots.stream() }
            .collect(Collectors.toSet())
      }
  )

  fun getOrCreateLibrary(libraryDescriptor: GraphQLLibraryDescriptor): GraphQLLibrary? {
    if (ApplicationManager.getApplication().isUnitTestMode && !myLibrariesEnabled) {
      return null
    }

    val library = myLibraries.computeIfAbsent(libraryDescriptor) {
      val root = resolveLibraryRoot(libraryDescriptor)
      if (root == null) {
        LOG.warn("Unresolved library root: " + libraryDescriptor.identifier)
        // try only once during a session
        if (myAsyncRefreshRequested.compareAndSet(false, true)) {
          tryRefreshAndLoadAsync()
        }
        return@computeIfAbsent EMPTY_LIBRARY
      }
      GraphQLLibrary(libraryDescriptor, root)
    }

    return if (library === EMPTY_LIBRARY) null else library
  }

  private fun tryRefreshAndLoadAsync() {
    ApplicationManager.getApplication().invokeLater({
      WriteAction.run<RuntimeException?> {
        val definitionsDirUrl = javaClass.getClassLoader().getResource(DEFINITIONS_RESOURCE_DIR)
        if (definitionsDirUrl == null) return@run
        val urlParts = URLUtil.splitJarUrl(definitionsDirUrl.file)
        if (urlParts == null) return@run
        val jarPath = PathUtil.toSystemIndependentName(urlParts.first)
        val jarLocalFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath!!)
        if (jarLocalFile == null) return@run
        val jarFile = JarFileSystem.getInstance().refreshAndFindFileByPath(jarPath + URLUtil.JAR_SEPARATOR)
        if (jarFile == null) return@run
        val definitionsDir = VfsUtil.refreshAndFindChild(jarFile, DEFINITIONS_RESOURCE_DIR)
        if (definitionsDir == null || !definitionsDir.isDirectory) return@run
        for (libraryDescriptor in ourDefinitionResourcePaths.keys) {
          val libraryRoot = resolveLibraryRoot(libraryDescriptor)
          if (libraryRoot != null) {
            myLibraries.put(libraryDescriptor, GraphQLLibrary(libraryDescriptor, libraryRoot))
            notifyLibrariesChanged()
          }
        }
      }
    }, ModalityState.nonModal(), myProject.disposed)
  }

  val allLibraries: List<SyntheticLibrary>
    get() = ourDefinitionResourcePaths
        .keys
        .mapNotNull { libraryDescriptor -> this.getOrCreateLibrary(libraryDescriptor) }
        .filter { l -> l.libraryDescriptor.isEnabled(myProject) }

  private fun resolveLibraryRoot(descriptor: GraphQLLibraryDescriptor): VirtualFile? {
    val resourceName: String? = ourDefinitionResourcePaths[descriptor]
    if (resourceName == null) {
      LOG.error("No resource files found for library: $descriptor")
      return null
    }
    val resource = javaClass.getClassLoader().getResource("$DEFINITIONS_RESOURCE_DIR/$resourceName")
    if (resource == null) {
      LOG.error("Resource not found: $resourceName")
      return null
    }
    val root = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.convertFromUrl(resource))
    return if (root != null && root.isValid) root else null
  }

  fun isLibraryRoot(file: VirtualFile?): Boolean {
    return file != null && myKnownLibraryRoots.getValue().contains(file)
  }

  val libraryRoots: Set<VirtualFile>
    get() = myKnownLibraryRoots.getValue()

  fun notifyLibrariesChanged() {
    if (myLibrariesChangeTriggered.compareAndSet(false, true)) {
      DumbService.getInstance(myProject).smartInvokeLater({
        try {
          WriteAction.run<RuntimeException?> {
            LOG.info("GraphQL libraries changed")
            myKnownLibraryRoots.drop()
            PsiManager.getInstance(myProject).dropPsiCaches()
            @Suppress("DEPRECATION")
            ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.getInstance(), false, true)
            DaemonCodeAnalyzer.getInstance(myProject).restart()
            EditorNotifications.getInstance(myProject).updateAllNotifications()
          }
        } finally {
          myLibrariesChangeTriggered.set(false)
        }
      }, ModalityState.nonModal())
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GraphQLLibraryManager::class.java)
    const val DEFINITIONS_RESOURCE_DIR: String = "definitions"
    private val EMPTY_LIBRARY = GraphQLLibrary(GraphQLLibraryDescriptor("EMPTY", "Empty"), LightVirtualFile())

    private val ourDefinitionResourcePaths: Map<GraphQLLibraryDescriptor, String> = mapOf(
        GraphQLLibraryTypes.SPECIFICATION to "specification.graphqls",

        GraphQLLibraryTypes.LINK_V1_0 to "link-v1.0.graphqls",

        GraphQLLibraryTypes.KOTLIN_LABS_V0_3 to "kotlin_labs-v0.3.graphqls",
        GraphQLLibraryTypes.KOTLIN_LABS_V0_4 to "kotlin_labs-v0.4.graphqls",
        GraphQLLibraryTypes.KOTLIN_LABS_V0_5 to "kotlin_labs-v0.5.graphqls",

        GraphQLLibraryTypes.NULLABILITY_V0_4 to "nullability-v0.4.graphqls",

        GraphQLLibraryTypes.CACHE_V0_1 to "cache-v0.1.graphqls",
        // Note: the cache library never supported v0.2 so the plugin doesn't either
        GraphQLLibraryTypes.CACHE_V0_3 to "cache-v0.3.graphqls",

        GraphQLLibraryTypes.FAKES_V0_0 to "fakes-v0.0.graphqls",
    )

    @JvmStatic
    fun getInstance(project: Project): GraphQLLibraryManager {
      return project.getService(GraphQLLibraryManager::class.java)
    }
  }
}
