package com.apollographql.ijplugin.graphql

import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawConfig
import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawProjectConfig
import com.intellij.lang.jsgraphql.ide.config.loader.GraphQLRawSchemaPointer
import com.intellij.lang.jsgraphql.ide.config.model.GraphQLConfig
import com.intellij.lang.jsgraphql.ide.config.model.GraphQLProjectConfig
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ApolloExternalSchemaTest : BasePlatformTestCase() {
  private lateinit var tempDirectory: File
  private lateinit var configDirectory: File
  private lateinit var configDir: VirtualFile

  override fun setUp() {
    super.setUp()
    tempDirectory = FileUtil.createTempDirectory("apollo-external-schema", null, true)
    configDirectory = tempDirectory.resolve("project").also { check(it.mkdir()) }
    configDir = refresh(configDirectory)
  }

  override fun tearDown() {
    try {
      FileUtil.delete(tempDirectory)
    } finally {
      super.tearDown()
    }
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

  private fun createFile(file: File): VirtualFile {
    check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
    file.writeText("type Query")
    return refresh(file)
  }

  private fun refresh(file: File): VirtualFile {
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))
  }
}
