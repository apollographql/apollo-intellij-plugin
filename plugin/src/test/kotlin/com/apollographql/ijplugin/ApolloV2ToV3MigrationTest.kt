package com.apollographql.ijplugin

import com.apollographql.ijplugin.refactoring.migration.ApolloV2ToV3MigrationProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@TestDataPath("\$CONTENT_ROOT/testData/migration/v2-to-v3")
@RunWith(JUnit4::class)
class ApolloV2ToV3MigrationTest : LightJavaCodeInsightFixtureTestCase() {
  private val mavenLibraries = listOf(
    "com.apollographql.apollo:apollo-runtime:2.5.14",
    "com.apollographql.apollo:apollo-coroutines-support:2.5.14",
  )

  override fun getTestDataPath() = "src/test/testData/migration/v2-to-v3"

  private val projectDescriptor = object : DefaultLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      for (library in mavenLibraries) {
        addFromMaven(model, library, true, DependencyScope.COMPILE)
      }
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return projectDescriptor
  }

  @Test
  fun testUpdatePackageName() = runMigration()

  @Test
  fun testUpdateMethodName() = runMigration()

  @Test
  fun testUpdateClassName() = runMigration()

  @Test
  fun testHttpCache() = runMigration()

  @Test
  fun testNormalizedCache() = runMigration()

  @Test
  fun testUpgradeGradlePluginInBuildGradleKts() = runMigration(extension = "gradle.kts", fileNameInProject = "build.gradle.kts")

  @Test
  fun testUpgradeGradlePluginInLibsVersionsToml() = runMigration(extension = "versions.toml", fileNameInProject = "libs.versions.toml")

  @Test
  fun testRemoveGradleDependenciesInBuildGradleKts() = runMigration(extension = "gradle.kts", fileNameInProject = "build.gradle.kts")

  @Test
  fun testRemoveGradleDependenciesInLibsVersionsToml() = runMigration(extension = "versions.toml", fileNameInProject = "libs.versions.toml")

  @Test
  fun testUpdateGradleDependenciesInBuildGradleKts() = runMigration(extension = "gradle.kts", fileNameInProject = "build.gradle.kts")

  private fun runMigration(extension: String = "kt", fileNameInProject: String? = null) {
    if (fileNameInProject != null) {
      myFixture.copyFileToProject(getTestName(true) + ".$extension", fileNameInProject)
    } else {
      myFixture.configureByFile(getTestName(true) + ".$extension")
    }

    ApolloV2ToV3MigrationProcessor(project).run()
    FileDocumentManager.getInstance().saveAllDocuments()
    if (fileNameInProject != null) {
      myFixture.checkResultByFile(fileNameInProject, getTestName(true) + "_after.$extension", true)
    } else {
      myFixture.checkResultByFile(getTestName(true) + "_after.$extension", true)
    }
  }
}
