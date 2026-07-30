package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.settings.projectSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ApolloExternalSchemaLibraryRootsProviderTest : BasePlatformTestCase() {
  private lateinit var externalDirectory: File

  override fun setUp() {
    super.setUp()
    externalDirectory = FileUtil.createTempDirectory("apollo-external-schema", null, true)
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = true
  }

  override fun tearDown() {
    try {
      project.projectSettingsState.contributeConfigurationToGraphqlPlugin = true
      project.apolloKotlinProjectModelService.replaceApolloKotlinServicesForTest(emptyList())
      UIUtil.dispatchAllInvocationEvents()
      FileUtil.delete(externalDirectory)
    } finally {
      super.tearDown()
    }
  }

  @Test
  fun externalSchemaFilesBecomeSyntheticLibrarySourceRoots() {
    val internalSchema = myFixture.addFileToProject("graphql/internal.graphqls", "type Query").virtualFile
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path, internalSchema.path, externalSchema.path)

    val provider = ApolloExternalSchemaLibraryRootsProvider()

    assertEquals(linkedSetOf(externalSchema), ApolloExternalSchemaLibraryRootsProvider.externalSchemaRoots(project))
    val library = assertOneElement(provider.getAdditionalProjectLibraries(project))
    assertEquals("apollo-external-graphql-schemas", library.comparisonId)
    assertEquals(listOf(externalSchema), library.sourceRoots)
    assertEmpty(library.binaryRoots)
    assertEmpty(library.excludedRoots)
    assertFalse(library.isShowInExternalLibrariesNode)
  }

  @Test
  fun missingExternalSchemaParentIsWatched() {
    val missingSchema = externalDirectory.resolve("generated/schema.graphqls")
    check(missingSchema.parentFile.mkdir())
    val internalSchema = myFixture.addFileToProject("graphql/internal.graphqls", "type Query").virtualFile
    setSchemaPaths(missingSchema.absolutePath, internalSchema.path)

    val watchedRoots = ApolloExternalSchemaLibraryRootsProvider().getRootsToWatch(project)

    assertEquals(listOf(refresh(missingSchema.parentFile)), watchedRoots)
  }

  @Test
  fun disabledGraphqlConfigurationContributesNoRoots() {
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path)
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = false

    val provider = ApolloExternalSchemaLibraryRootsProvider()

    assertEmpty(ApolloExternalSchemaLibraryRootsProvider.externalSchemaRoots(project))
    assertEmpty(provider.getAdditionalProjectLibraries(project))
    assertEmpty(provider.getRootsToWatch(project))
  }

  @Test
  fun disablingGraphqlConfigurationPublishesRemovedRoots() {
    val changes = mutableListOf<Pair<Set<VirtualFile>, Set<VirtualFile>>>()
    project.service<GraphQLConfigService>()
    project.messageBus.connect(testRootDisposable).subscribe(
        AdditionalLibraryRootsListener.TOPIC,
        object : AdditionalLibraryRootsListener {
          override fun libraryRootsChanged(
              presentableLibraryName: String?,
              oldRoots: Collection<VirtualFile>,
              newRoots: Collection<VirtualFile>,
              libraryNameForDebug: String,
          ) {
            changes += oldRoots.toSet() to newRoots.toSet()
          }
        }
    )
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path)
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = false
    UIUtil.dispatchAllInvocationEvents()

    assertEquals(setOf(externalSchema), changes.first().second)
    assertEmpty(changes.last().second)
  }

  private fun setSchemaPaths(vararg paths: String) {
    project.apolloKotlinProjectModelService.replaceApolloKotlinServicesForTest(
        listOf(
            ApolloKotlinService(
                gradleProjectPath = ":app",
                serviceName = "service",
                allSchemaPaths = paths.toList(),
            )
        )
    )
  }

  private fun createExternalFile(name: String): VirtualFile {
    return refresh(externalDirectory.resolve(name).also { it.writeText("type Query") })
  }

  private fun refresh(file: File): VirtualFile {
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))
  }
}
