package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.settings.projectSettingsState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.Collections

class ApolloExternalSchemaLibraryRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    val roots = externalSchemaRoots(project)
    if (roots.isEmpty()) return emptyList()

    return listOf(
        SyntheticLibrary.newImmutableLibrary(
            COMPARISON_ID,
            roots.toList(),
            emptyList(),
            emptySet(),
            null,
        )
    )
  }

  override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
    if (!project.projectSettingsState.contributeConfigurationToGraphqlPlugin) return emptyList()

    val parentPaths = configuredSchemaPaths(project)
        .mapNotNull { File(it).parentFile?.absolutePath }
        .distinct()

    return runReadAction {
      val projectFileIndex = ProjectFileIndex.getInstance(project)
      parentPaths
          .mapNotNull { LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(it)) }
          .filter { it.isValid && it.isDirectory && !projectFileIndex.isInContent(it) }
          .sortedBy { it.path }
    }
  }

  companion object {
    private const val COMPARISON_ID = "apollo-external-graphql-schemas"

    // ProjectFileIndex refreshes additional-library contributors, which can call this provider recursively.
    private val fileIndexRecursionGuard =
      RecursionManager.createGuard<Project>(ApolloExternalSchemaLibraryRootsProvider::class.java.name)

    internal fun externalSchemaRoots(project: Project): Set<VirtualFile> {
      if (!project.projectSettingsState.contributeConfigurationToGraphqlPlugin) return emptySet()

      return fileIndexRecursionGuard.computePreventingRecursion<Set<VirtualFile>, RuntimeException>(project, false) {
        val schemaPaths = configuredSchemaPaths(project)
        runReadAction {
          val projectFileIndex = ProjectFileIndex.getInstance(project)
          val roots = schemaPaths
              .mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) }
              .filter { it.isValid && !it.isDirectory && !projectFileIndex.isInContent(it) }
              .sortedBy { it.path }
          Collections.unmodifiableSet(LinkedHashSet(roots))
        }
      } ?: emptySet()
    }
  }
}

private fun configuredSchemaPaths(project: Project): List<String> {
  return project.apolloKotlinProjectModelService.getApolloKotlinServices()
      .flatMap { it.allSchemaPaths }
      .distinct()
}
