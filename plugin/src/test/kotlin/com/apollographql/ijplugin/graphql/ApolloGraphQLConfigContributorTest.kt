package com.apollographql.ijplugin.graphql

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ApolloGraphQLConfigContributorTest : BasePlatformTestCase() {
  private lateinit var tempDirectory: File
  private lateinit var projectDirectory: File
  private lateinit var projectDir: VirtualFile

  override fun setUp() {
    super.setUp()
    tempDirectory = FileUtil.createTempDirectory("apollo-graphql-config-contributor", null, true)
    projectDirectory = tempDirectory.resolve("project").also { check(it.mkdir()) }
    projectDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDirectory))
  }

  override fun tearDown() {
    try {
      FileUtil.delete(tempDirectory)
    } finally {
      super.tearDown()
    }
  }

  @Test
  fun descendantPathIsProjectRelative() {
    val descendant = projectDirectory.resolve("module/schema.graphqls").absolutePath

    assertEquals("module/schema.graphqls", descendant.toGraphQLConfigPath(projectDir))
  }

  @Test
  fun siblingPathTraversesOutOfProject() {
    val sibling = tempDirectory.resolve("shared/schema.graphqls").absolutePath

    assertEquals("../shared/schema.graphqls", sibling.toGraphQLConfigPath(projectDir))
  }

  @Test
  fun missingPathIsNotEmpty() {
    val missingGenerated = projectDirectory.resolve("build/generated/schema.graphqls").absolutePath

    assertFalse(missingGenerated.toGraphQLConfigPath(projectDir).isEmpty())
  }

  @Test
  fun incompatibleFilesystemRootUsesAbsolutePath() {
    if (!SystemInfo.isWindows) return

    val projectDrive = projectDirectory.toPath().root.toString().first().uppercaseChar()
    val alternateDrive = if (projectDrive == 'C') 'D' else 'C'
    val incompatible = File("$alternateDrive:\\shared\\schema.graphqls")

    assertEquals(
        FileUtil.toSystemIndependentName(incompatible.absolutePath),
        incompatible.absolutePath.toGraphQLConfigPath(projectDir)
    )
  }
}
