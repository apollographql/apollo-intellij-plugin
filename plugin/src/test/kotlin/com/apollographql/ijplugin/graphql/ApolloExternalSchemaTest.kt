package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.settings.projectSettingsState
import com.apollographql.ijplugin.util.schemaFiles
import com.intellij.lang.jsgraphql.ide.config.GraphQLConfigContributor
import com.intellij.lang.jsgraphql.ide.config.GraphQLConfigProvider
import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawConfig
import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawProjectConfig
import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawSchemaPointer
import com.intellij.lang.jsgraphql.ide.config.model.GraphQLConfig
import com.intellij.lang.jsgraphql.ide.config.model.GraphQLProjectConfig
import com.intellij.lang.jsgraphql.psi.GraphQLFile
import com.intellij.lang.jsgraphql.psi.GraphQLIdentifier
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.util.ui.EDT
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ApolloExternalSchemaTest : CodeInsightFixtureTestCase<EmptyModuleFixtureBuilder<*>>() {
  private lateinit var tempDirectory: File
  private lateinit var configDirectory: File
  private lateinit var configDir: VirtualFile

  override fun setUp() {
    super.setUp()
    tempDirectory = FileUtil.createTempDirectory("apollo-external-schema", null, true)
    configDirectory = tempDirectory.resolve("project").also { check(it.mkdir()) }
    configDir = refresh(configDirectory)
    project.projectSettingsState.contributeConfigurationToGraphqlPlugin = true
  }

  override fun tearDown() {
    try {
      project.projectSettingsState.contributeConfigurationToGraphqlPlugin = true
      project.apolloKotlinProjectModelService.replaceApolloKotlinServicesForTest(emptyList())
      EDT.dispatchAllInvocationEvents()
      FileUtil.delete(tempDirectory)
    } finally {
      super.tearDown()
    }
  }

  @Test
  fun externalSchemaIsIndexedAndResolved() {
    val schemaVirtualFile = createFile(
        tempDirectory.resolve("schema.graphqls"),
        """
          type Query {
            externalField: ExternalType!
          }

          type ExternalType {
            value: String!
          }
        """.trimIndent(),
    )
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    assertFalse(projectFileIndex.isInContent(schemaVirtualFile))
    assertFalse(projectFileIndex.isInLibrarySource(schemaVirtualFile))

    val operation = myFixture.addFileToProject(
        "graphql/ExternalQuery.graphql",
        """
          query ExternalQuery {
            externalField {
              value
            }
          }
        """.trimIndent(),
    ) as GraphQLFile
    myFixture.configureFromExistingVirtualFile(operation.virtualFile)
    val externalField = PsiTreeUtil.findChildrenOfType(operation, GraphQLIdentifier::class.java)
        .single { it.text == "externalField" }

    project.service<GraphQLConfigService>()
    project.apolloKotlinProjectModelService.replaceApolloKotlinServicesForTest(
        listOf(
            ApolloKotlinService(
                gradleProjectPath = ":app",
                serviceName = "service",
                allSchemaPaths = listOf(schemaVirtualFile.path),
                allOperationPaths = listOf(myFixture.tempDirPath),
            )
        )
    )
    EDT.dispatchAllInvocationEvents()
    val library = assertOneElement(ApolloExternalSchemaLibraryRootsProvider().getAdditionalProjectLibraries(project))
    assertEquals(listOf(schemaVirtualFile), library.sourceRoots)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    EDT.dispatchAllInvocationEvents()

    val projectConfig = checkNotNull(GraphQLConfigProvider.getInstance(project).resolveProjectConfig(operation))
    val schemaPsiFile = checkNotNull(PsiManager.getInstance(project).findFile(schemaVirtualFile)) as GraphQLFile

    assertTrue(projectFileIndex.isInLibrarySource(schemaVirtualFile))
    assertTrue(projectConfig.schemaScope.contains(schemaVirtualFile))
    assertSame(schemaPsiFile, externalField.reference?.resolve()?.containingFile)
    assertEquals(listOf(schemaPsiFile), externalField.schemaFiles())
  }

  @Test
  fun parentRelativeSchemaPointerIsIncludedOutOfScopeByCanonicalPath() {
    val internalSchema = createFile(configDirectory.resolve("schema.graphqls"))
    val externalSchema = createFile(tempDirectory.resolve("shared/schema.graphqls"))
    val projectConfig = projectConfig("./../shared/schema.graphqls", "schema.graphqls")

    assertTrue(projectConfig.isIncludedOutOfScopeFile(externalSchema))
    assertFalse(projectConfig.isIncludedOutOfScopeFile(internalSchema))
  }

  @Test
  fun absoluteExternalSchemaPointerIsIncludedOutOfScopeByCanonicalPath() {
    val externalSchema = createFile(tempDirectory.resolve("shared/schema.graphqls"))
    val projectConfig = projectConfig(externalSchema.path)

    assertTrue(projectConfig.isIncludedOutOfScopeFile(externalSchema))
  }

  @Test
  fun schemaFilesResolveCanonicalAbsoluteAndRelativePointers() {
    val externalSchema = createFile(tempDirectory.resolve("shared/schema.graphqls"))
    val internalSchema = myFixture.addFileToProject("graphql/schema.graphqls", "type Query { field: String }")
    val operation = myFixture.addFileToProject("graphql/operation.graphql", "query Test { field }") as GraphQLFile
    contributeConfig(operation.virtualFile.parent, externalSchema.path, "schema.graphqls")

    val schemaFiles = operation.schemaFiles()

    assertEquals(
        listOf(externalSchema.path, internalSchema.virtualFile.path),
        schemaFiles.map { it.virtualFile.path },
    )
  }

  private fun contributeConfig(configDir: VirtualFile, vararg schemaPaths: String) {
    val contributor = object : GraphQLConfigContributor {
      override fun contributeConfigs(project: Project): Collection<GraphQLConfig> {
        return listOf(
            GraphQLConfig(
                project = project,
                dir = configDir,
                file = null,
                rawData = GraphQLRawConfig(
                    projects = mapOf(
                        "service" to GraphQLRawProjectConfig(
                            schema = schemaPaths.map(::GraphQLRawSchemaPointer),
                        )
                    )
                ),
            )
        )
      }
    }
    ExtensionTestUtil.maskExtensions(GraphQLConfigContributor.EP_NAME, listOf(contributor), testRootDisposable)
    GraphQLConfigProvider.getInstance(project).invalidate()
    EDT.dispatchAllInvocationEvents()
  }

  private fun projectConfig(vararg schemaPaths: String): GraphQLProjectConfig {
    val rootConfig = GraphQLConfig(
        project = project,
        dir = configDir,
        file = null,
        rawData = GraphQLRawConfig(
            projects = mapOf(
                "service" to GraphQLRawProjectConfig(
                    schema = schemaPaths.map(::GraphQLRawSchemaPointer),
                )
            )
        ),
    )
    return checkNotNull(rootConfig.findProject("service"))
  }

  private fun createFile(file: File, text: String = "type Query"): VirtualFile {
    check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
    file.writeText(text)
    return refresh(file)
  }

  private fun refresh(file: File): VirtualFile {
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))
  }
}

@RunWith(JUnit4::class)
class ApolloExternalSchemaVirtualFileSystemTest : BasePlatformTestCase() {
  @Test
  fun schemaFilesResolveRelativePointerFromOriginatingVirtualFileSystem() {
    val schema = myFixture.addFileToProject("excluded/schema.graphqls", "type Query { field: String }") as GraphQLFile
    val operation = myFixture.addFileToProject("graphql/operation.graphql", "query Test { field }") as GraphQLFile
    PsiTestUtil.addExcludedRoot(myFixture.module, checkNotNull(schema.virtualFile.parent))
    contributeConfig(operation.virtualFile.parent, "../excluded/schema.graphqls")
    val configProvider = GraphQLConfigProvider.getInstance(project)

    assertNotNull(configProvider.getForConfigFile(operation.virtualFile.parent))
    assertNotNull(configProvider.resolveProjectConfig(operation))
    assertFalse(
        FilenameIndex.getVirtualFilesByName(schema.name, GlobalSearchScope.allScope(project)).contains(schema.virtualFile)
    )

    val schemaFiles = operation.schemaFiles()

    assertEquals(listOf(schema.virtualFile.path), schemaFiles.map { it.virtualFile.path })
  }

  private fun contributeConfig(configDir: VirtualFile, vararg schemaPaths: String) {
    val contributor = object : GraphQLConfigContributor {
      override fun contributeConfigs(project: Project): Collection<GraphQLConfig> {
        return listOf(
            GraphQLConfig(
                project = project,
                dir = configDir,
                file = null,
                rawData = GraphQLRawConfig(
                    projects = mapOf(
                        "service" to GraphQLRawProjectConfig(
                            schema = schemaPaths.map(::GraphQLRawSchemaPointer),
                        )
                    )
                ),
            )
        )
      }
    }
    ExtensionTestUtil.maskExtensions(GraphQLConfigContributor.EP_NAME, listOf(contributor), testRootDisposable)
    GraphQLConfigProvider.getInstance(project).invalidate()
    EDT.dispatchAllInvocationEvents()
  }
}
