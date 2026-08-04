package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.ApolloKotlinProjectModelService
import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.settings.projectSettingsState
import com.intellij.mock.MockProject
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
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
  fun rootsToWatchDoesNotCreateApolloModelService() {
    val uninitializedProject = MockProject(null, testRootDisposable)
    assertNull(uninitializedProject.getServiceIfCreated(ApolloKotlinProjectModelService::class.java))

    assertEmpty(ApolloExternalSchemaLibraryRootsProvider().getRootsToWatch(uninitializedProject))

    assertNull(uninitializedProject.getServiceIfCreated(ApolloKotlinProjectModelService::class.java))
  }

  @Test
  fun externalSchemaFilesBecomeSyntheticLibrarySourceRoots() {
    project.service<GraphQLConfigService>()
    val internalSchema = myFixture.addFileToProject("graphql/internal.graphqls", "type Query").virtualFile
    val firstExternalSchema = createExternalFile("z-schema.graphqls")
    val secondExternalSchema = createExternalFile("a-schema.graphqls")
    setSchemaPaths(firstExternalSchema.path, internalSchema.path, secondExternalSchema.path, firstExternalSchema.path)
    UIUtil.dispatchAllInvocationEvents()

    val library = assertOneElement(ApolloExternalSchemaLibraryRootsProvider().getAdditionalProjectLibraries(project))
    assertEquals("apollo-external-graphql-schemas", library.comparisonId)
    assertEquals(listOf(secondExternalSchema, firstExternalSchema), library.sourceRoots)
    assertEmpty(library.binaryRoots)
    assertEmpty(library.excludedRoots)
    assertFalse(library.isShowInExternalLibrariesNode)
  }

  @Test
  fun providerUsesPublishedRootsUntilLibraryChangeRuns() {
    project.service<GraphQLConfigService>()
    val firstSchema = createExternalFile("first.graphqls")
    val secondSchema = createExternalFile("second.graphqls")
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    setSchemaPaths(firstSchema.path)
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(listOf(firstSchema), provider.sourceRoots())

    setSchemaPaths(secondSchema.path)

    assertEquals(listOf(firstSchema), provider.sourceRoots())
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(listOf(secondSchema), provider.sourceRoots())
  }

  @Test
  fun watchRequestsAreAddedReplacedAndRemovedBeforeLibraryChange() {
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    val service = project.service<GraphQLConfigService>()
    val watchRootsDuringChanges = mutableListOf<Collection<VirtualFile>>()
    val registeredPathsDuringChanges = mutableListOf<Set<String>>()
    project.messageBus.connect(testRootDisposable).subscribe(
        AdditionalLibraryRootsListener.TOPIC,
        AdditionalLibraryRootsListener { _, _, _, _ ->
          watchRootsDuringChanges += provider.getRootsToWatch(project)
          registeredPathsDuringChanges += service.registeredWatchRootPaths()
        },
    )
    val firstMissingSchema = externalDirectory.resolve("generated-one/schema.graphqls")
    val secondMissingSchema = externalDirectory.resolve("generated-two/schema.graphqls")
    check(firstMissingSchema.parentFile.mkdir())
    check(secondMissingSchema.parentFile.mkdir())
    val firstParent = refresh(firstMissingSchema.parentFile)
    val secondParent = refresh(secondMissingSchema.parentFile)

    setSchemaPaths(firstMissingSchema.absolutePath)
    UIUtil.dispatchAllInvocationEvents()
    setSchemaPaths(secondMissingSchema.absolutePath)
    UIUtil.dispatchAllInvocationEvents()
    setSchemaPaths()
    UIUtil.dispatchAllInvocationEvents()

    assertEquals(listOf(firstParent), watchRootsDuringChanges[0])
    assertEquals(setOf(firstParent.path), registeredPathsDuringChanges[0])
    assertEquals(listOf(secondParent), watchRootsDuringChanges[1])
    assertEquals(setOf(secondParent.path), registeredPathsDuringChanges[1])
    assertEmpty(watchRootsDuringChanges[2])
    assertEmpty(registeredPathsDuringChanges[2])
    assertEmpty(provider.getRootsToWatch(project))
  }

  @Test
  fun creatingConfiguredSchemaPublishesRoot() {
    project.service<GraphQLConfigService>()
    val schemaFile = externalDirectory.resolve("generated/schema.graphqls")
    check(schemaFile.parentFile.mkdir())
    val parent = refresh(schemaFile.parentFile)
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    setSchemaPaths(schemaFile.absolutePath)
    waitForIndexes()
    assertEquals(listOf(parent), provider.getRootsToWatch(project))
    assertEmpty(provider.sourceRoots())

    val schema = VfsTestUtil.createFile(parent, schemaFile.name, "type Query")
    waitForIndexes()

    assertEquals(listOf(schema), provider.sourceRoots())
    assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(schema))
  }

  @Test
  fun creatingConfiguredSchemaWithInitiallyMissingParentPublishesRoot() {
    val service = project.service<GraphQLConfigService>()
    val schemaFile = externalDirectory.resolve("generated/missing/schema.graphqls")
    val parentPath = FileUtil.toSystemIndependentName(schemaFile.parentFile.absolutePath)
    val contentDirectory = externalDirectory.resolve("project-content").also { check(it.mkdir()) }
    val contentRoot = refresh(contentDirectory)
    PsiTestUtil.addContentRoot(myFixture.module, contentRoot)
    val missingContentSchemaPath = "${contentRoot.path}/generated/schema.graphqls"
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    setSchemaPaths(schemaFile.absolutePath, missingContentSchemaPath)
    waitForIndexes()

    assertFalse(schemaFile.parentFile.exists())
    assertEmpty(provider.getRootsToWatch(project))
    assertEquals(setOf(parentPath), service.registeredWatchRootPaths())
    assertEmpty(provider.sourceRoots())

    check(schemaFile.parentFile.mkdirs())
    val parent = refresh(schemaFile.parentFile)
    val schema = VfsTestUtil.createFile(parent, schemaFile.name, "type Query { created: String }")
    waitForSourceRoot(provider, schema)

    assertEquals(listOf(schema), provider.sourceRoots())
    assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(schema))
  }

  @Test
  fun deletingAndRecreatingConfiguredSchemaRefreshesRoots() {
    project.service<GraphQLConfigService>()
    val parent = refresh(externalDirectory)
    val schema = VfsTestUtil.createFile(parent, "schema.graphqls", "type Query")
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    setSchemaPaths(schema.path)
    waitForIndexes()
    assertEquals(listOf(schema), provider.sourceRoots())
    assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(schema))

    VfsTestUtil.deleteFile(schema)
    waitForIndexes()
    assertFalse(schema.isValid)
    assertEmpty(provider.sourceRoots())

    val recreatedSchema = VfsTestUtil.createFile(parent, "schema.graphqls", "type Query { recreated: String }")
    waitForIndexes()
    assertNotSame(schema, recreatedSchema)
    assertFalse(schema.isValid)
    assertTrue(recreatedSchema.isValid)
    assertEquals(listOf(recreatedSchema), provider.sourceRoots())
    assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(recreatedSchema))
  }

  @Test
  fun deletingAndRecreatingConfiguredSchemaParentPublishesNewRoot() {
    val service = project.service<GraphQLConfigService>()
    val schemaFile = externalDirectory.resolve("generated/schema.graphqls")
    check(schemaFile.parentFile.mkdir())
    val parent = refresh(schemaFile.parentFile)
    val schema = VfsTestUtil.createFile(parent, schemaFile.name, "type Query")
    val parentPath = parent.path
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    setSchemaPaths(schema.path)
    waitForIndexes()
    assertEquals(listOf(schema), provider.sourceRoots())
    assertEquals(setOf(parentPath), service.registeredWatchRootPaths())

    VfsTestUtil.deleteFile(parent)
    waitForIndexes()
    assertFalse(parent.isValid)
    assertFalse(schema.isValid)
    assertEmpty(provider.getRootsToWatch(project))
    assertEmpty(provider.sourceRoots())
    assertEquals(setOf(parentPath), service.registeredWatchRootPaths())

    check(schemaFile.parentFile.mkdir())
    val recreatedParent = refresh(schemaFile.parentFile)
    val recreatedSchema = VfsTestUtil.createFile(recreatedParent, schemaFile.name, "type Query { recreated: String }")
    waitForSourceRoot(provider, recreatedSchema)

    assertNotSame(schema, recreatedSchema)
    assertFalse(schema.isValid)
    assertTrue(recreatedSchema.isValid)
    assertEquals(listOf(recreatedSchema), provider.sourceRoots())
    assertTrue(ProjectFileIndex.getInstance(project).isInLibrarySource(recreatedSchema))
  }

  @Test
  fun disabledGraphqlConfigurationContributesNoRoots() {
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path)
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = false

    val provider = ApolloExternalSchemaLibraryRootsProvider()

    assertEmpty(provider.getAdditionalProjectLibraries(project))
    assertEmpty(provider.getRootsToWatch(project))
  }

  @Test
  fun disablingGraphqlConfigurationPublishesRemovedRoots() {
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    val changes = mutableListOf<Pair<Set<VirtualFile>, Set<VirtualFile>>>()
    val rootsDuringChanges = mutableListOf<Collection<VirtualFile>>()
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
            rootsDuringChanges += provider.sourceRoots()
          }
        }
    )
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path)
    UIUtil.dispatchAllInvocationEvents()
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = false
    UIUtil.dispatchAllInvocationEvents()

    assertEquals(setOf(externalSchema), changes.first().second)
    assertEquals(listOf(externalSchema), rootsDuringChanges.first())
    assertEmpty(changes.last().second)
    assertEmpty(rootsDuringChanges.last())
  }

  @Test
  fun removingConfiguredSchemasPublishesEmptyRoots() {
    val provider = ApolloExternalSchemaLibraryRootsProvider()
    val rootsDuringChanges = mutableListOf<Collection<VirtualFile>>()
    project.service<GraphQLConfigService>()
    project.messageBus.connect(testRootDisposable).subscribe(
        AdditionalLibraryRootsListener.TOPIC,
        AdditionalLibraryRootsListener { _, _, _, _ -> rootsDuringChanges += provider.sourceRoots() },
    )
    val externalSchema = createExternalFile("schema.graphqls")
    setSchemaPaths(externalSchema.path)
    UIUtil.dispatchAllInvocationEvents()

    setSchemaPaths()
    UIUtil.dispatchAllInvocationEvents()

    assertEquals(listOf(externalSchema), rootsDuringChanges.first())
    assertEmpty(rootsDuringChanges.last())
    assertEmpty(provider.sourceRoots())
  }

  private fun waitForIndexes() {
    UIUtil.dispatchAllInvocationEvents()
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun waitForSourceRoot(
      provider: ApolloExternalSchemaLibraryRootsProvider,
      schema: VirtualFile,
  ) {
    PlatformTestUtil.waitWithEventsDispatching(
        {
          val roots = provider.sourceRoots()
          "Configured schema was not attached after ${schema.parent.path} was created; roots=$roots"
        },
        { schema.isValid && provider.sourceRoots().singleOrNull() == schema },
        30,
    )
    waitForIndexes()
  }

  private fun ApolloExternalSchemaLibraryRootsProvider.sourceRoots(): Collection<VirtualFile> {
    return getAdditionalProjectLibraries(project).singleOrNull()?.sourceRoots.orEmpty()
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
