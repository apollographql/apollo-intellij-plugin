package com.apollographql.ijplugin.graphql

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile

class ApolloExternalSchemaLibraryRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    val roots = snapshot(project).externalSchemaRoots.filter { it.isValid }
    if (roots.isEmpty()) return emptyList()

    return listOf(
        SyntheticLibrary.newImmutableLibrary(
            COMPARISON_ID,
            roots,
            emptyList(),
            emptySet(),
            null,
        )
    )
  }

  override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
    return snapshot(project).watchRoots.filter { it.isValid }
  }

  private fun snapshot(project: Project): ApolloExternalSchemaSnapshot {
    return project.getServiceIfCreated(GraphQLConfigService::class.java)?.externalSchemaSnapshot
        ?: ApolloExternalSchemaSnapshot.EMPTY
  }

  companion object {
    private const val COMPARISON_ID = "apollo-external-graphql-schemas"
  }
}
