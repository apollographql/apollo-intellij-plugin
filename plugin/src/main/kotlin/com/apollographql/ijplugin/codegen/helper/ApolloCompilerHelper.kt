@file:OptIn(ApolloInternal::class, ApolloExperimental::class)

package com.apollographql.ijplugin.codegen.helper

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.compiler.ApolloCompiler
import com.apollographql.apollo.compiler.ApolloCompilerPlugin
import com.apollographql.apollo.compiler.ApolloCompilerPluginEnvironment
import com.apollographql.apollo.compiler.EntryPoints
import com.apollographql.apollo.compiler.UsedCoordinates
import com.apollographql.apollo.compiler.toCodegenSchemaOptions
import com.apollographql.apollo.compiler.toInputFiles
import com.apollographql.apollo.compiler.toIrOperations
import com.apollographql.apollo.compiler.writeTo
import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.gradle.ApolloKotlinService.Id
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.apollographql.ijplugin.util.toMapOfAny
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

class ApolloCompilerHelper(
    private val project: Project,
) {
  fun generateAllSources() {
    logd()
    val allServices = project.apolloKotlinProjectModelService.getApolloKotlinServices()
    val leafServices = allServices.filter { candidateService -> allServices.none { it.upstreamServiceIds.contains(candidateService.id) } }
    val outputDirs = mutableSetOf<File>()
    for (service in leafServices) {
      outputDirs.addAll(internalGenerateSources(service))
    }
    VfsUtil.markDirtyAndRefresh(true, true, true, *outputDirs.toTypedArray())
    logd("Apollo compiler sources generated for ${leafServices.map { it.id }}")
  }

  fun generateSources(service: ApolloKotlinService) {
    val outputDirs = internalGenerateSources(service)
    VfsUtil.markDirtyAndRefresh(true, true, true, *outputDirs.toTypedArray())
  }

  private fun internalGenerateSources(service: ApolloKotlinService): Set<File> {
    try {
      logd("Running Apollo compiler for service ${service.id}")

      val schemaService = service.findSchemaService()
      if (schemaService == null) {
        logw("No schema service found for ${service.id}. Cannot generate sources.")
        return emptySet()
      }

      val codegenSchemaFile = File.createTempFile("codegenSchemaFile", null)
      logd("buildCodegenSchema (${service.id})")
      EntryPoints.buildCodegenSchema(
          plugins = schemaService.loadPlugins(),
          logger = logger,
          arguments = schemaService.pluginArgumentsJson!!.toMapOfAny(),
          normalizedSchemaFiles = schemaService.schemaPaths.map { File(it) }.toInputFiles(),
          codegenSchemaOptionsFile = File(schemaService.codegenSchemaOptionsFilePath!!),
          codegenSchemaFile = codegenSchemaFile,
      )
      ProgressManager.checkCanceled()

      val allUpstreamServiceIds = service.allUpstreamServiceIds()
      if (allUpstreamServiceIds == null) {
        logw("Failed to find upstream services for ${service.id}. Cannot generate sources.")
        return emptySet()
      }
      val allDownstreamServiceIds = schemaService.allDownstreamServiceIds()
      if (allDownstreamServiceIds == null) {
        logw("Failed to find downstream services for ${service.id}. Cannot generate sources.")
        return emptySet()
      }

      val irOperationsById = mutableMapOf<Id, File>()
      val allServiceIds = (allUpstreamServiceIds + service.id + allDownstreamServiceIds).distinct()
      for (serviceId in allServiceIds) {
        val service = service(serviceId)!!
        val irOperationsFile = File.createTempFile("irOperationsFile", null)
        logd("buildIr (${service.id})")
        EntryPoints.buildIr(
            plugins = service.loadPlugins(),
            logger = logger,
            arguments = service.pluginArgumentsJson!!.toMapOfAny(),
            graphqlFiles = service.executableFiles().toInputFiles(),
            codegenSchemaFiles = listOf(codegenSchemaFile).toInputFiles(),
            upstreamIrOperations = irOperationsById.values.toInputFiles(),
            irOptionsFile = File(service.irOptionsFilePath!!),
            irOperationsFile = irOperationsFile,
        )
        ProgressManager.checkCanceled()
        irOperationsById[service.id] = irOperationsFile
      }

      val usedCoordinates: UsedCoordinates = irOperationsById.values.map {
        it.toIrOperations().usedCoordinates
      }.fold(UsedCoordinates()) { acc, element ->
        acc.mergeWith(element)
      }
      val usedCoordinatesFile = File.createTempFile("usedCoordinates", null)
      usedCoordinates.writeTo(usedCoordinatesFile)

      val upstreamMetadata = mutableListOf<File>()
      val outputDirs = mutableSetOf<File>()
      for (serviceId in allServiceIds) {
        val service = service(serviceId)!!
        val allDownstreamServiceIds = service.allDownstreamServiceIds()
        if (allDownstreamServiceIds == null) {
          logw("Failed to find downstream services for ${service.id}. Cannot generate sources.")
          return outputDirs
        }
        val allUpstreamServiceIds = service.allUpstreamServiceIds()
        if (allUpstreamServiceIds == null) {
          logw("Failed to find upstream services for ${service.id}. Cannot generate sources.")
          return outputDirs
        }

        File(service.operationManifestFilePath!!).parentFile.mkdirs()
        val metadataOutput = File.createTempFile("metadataOutput", null)
        logd("buildSourcesFromIr (${service.id})")
        EntryPoints.buildSourcesFromIr(
            plugins = service.loadPlugins(),
            logger = logger,
            arguments = service.pluginArgumentsJson!!.toMapOfAny(),
            codegenSchemas = listOf(codegenSchemaFile).toInputFiles(),
            upstreamMetadata = upstreamMetadata.toInputFiles(),
            irOperations = irOperationsById[service.id]!!,
            usedCoordinates = usedCoordinatesFile,
            codegenOptions = File(service.codegenOptionsFilePath!!),
            operationManifest = File(service.operationManifestFilePath),
            outputDirectory = File(service.codegenOutputDirPath!!),
            metadataOutput = metadataOutput,
        )
        ProgressManager.checkCanceled()
        upstreamMetadata.add(metadataOutput)
        outputDirs.add(File(service.codegenOutputDirPath))
      }

      for (serviceId in allServiceIds) {
        val service = service(serviceId)!!
        if (File(service.codegenSchemaOptionsFilePath!!).toCodegenSchemaOptions().generateDataBuilders) {
          logd("buildDataBuilders (${service.id})")
          EntryPoints.buildDataBuilders(
              plugins = service.loadPlugins(),
              arguments = service.pluginArgumentsJson!!.toMapOfAny(),
              logger = logger,
              codegenSchemas = listOf(codegenSchemaFile).toInputFiles(),
              upstreamMetadatas = upstreamMetadata.toInputFiles(),
              downstreamUsedCoordinates = usedCoordinatesFile,
              codegenOptions = File(service.codegenOptionsFilePath!!),
              outputDirectory = File(service.dataBuildersOutputDirPath!!),
          )
          ProgressManager.checkCanceled()
          outputDirs.add(File(service.dataBuildersOutputDirPath))
        }
      }

      logd("Apollo compiler sources generated for service ${service.id} at ${service.codegenOutputDirPath}")
      return outputDirs
    } catch (e: Exception) {
      if (e is ProcessCanceledException) {
        logd("Apollo compiler was canceled")
        throw e
      }
      logw(e, "Failed to generate sources for service ${service.id}")
    }
    return emptySet()
  }

  private fun ApolloKotlinService.findSchemaService(): ApolloKotlinService? {
    if (upstreamServiceIds.isEmpty()) return this
    val upstreamService = service(upstreamServiceIds.first()) ?: return null
    return upstreamService.findSchemaService()
  }

  private fun ApolloKotlinService.allUpstreamServiceIds(): List<Id>? {
    val allUpstreamServiceIds = mutableListOf<Id>()
    for (upstreamServiceId in this.upstreamServiceIds) {
      val upstreamService = service(upstreamServiceId) ?: return null
      val upstreamServiceUpstreamServices = upstreamService.allUpstreamServiceIds() ?: return null
      allUpstreamServiceIds.addAll(upstreamServiceUpstreamServices)
      allUpstreamServiceIds.add(upstreamServiceId)
    }
    return allUpstreamServiceIds.distinct()
  }

  private fun ApolloKotlinService.allDownstreamServiceIds(): List<Id>? {
    val allDownstreamServiceIds = mutableListOf<Id>()
    for (downstreamServiceId in this.downstreamServiceIds) {
      val downstreamService = service(downstreamServiceId) ?: return null
      val downstreamServiceDownstreamServices = downstreamService.allDownstreamServiceIds() ?: return null
      allDownstreamServiceIds.add(downstreamServiceId)
      allDownstreamServiceIds.addAll(downstreamServiceDownstreamServices)
    }
    return allDownstreamServiceIds.distinct()
  }

  private fun ApolloKotlinService.executableFiles(): List<File> {
    val executableFiles = mutableListOf<File>()
    for (operationPath in operationPaths.map { File(it) }) {
      for (file in operationPath.walk()) {
        if (file.extension == "graphql") {
          executableFiles.add(file)
        }
      }
    }
    return executableFiles
  }

  private fun service(id: Id): ApolloKotlinService? = project.apolloKotlinProjectModelService.getApolloKotlinService(id)

  private val logger = object : ApolloCompiler.Logger {
    override fun debug(message: String) {
      logd("Apollo Compiler: $message")
    }

    override fun info(message: String) {
      logd("Apollo Compiler: $message")
    }

    override fun warning(message: String) {
      logw("Apollo Compiler: $message")
    }

    override fun error(message: String) {
      logw("Apollo Compiler: $message")
    }
  }

  private fun ApolloKotlinService.loadPlugins(): List<ApolloCompilerPlugin> {
    val classLoader = URLClassLoader(
        pluginDependencies.orEmpty().map { File(it).toURI().toURL() }.toTypedArray(),
        ApolloCompilerPlugin::class.java.classLoader
    )
    val plugins = ServiceLoader.load(ApolloCompilerPlugin::class.java, classLoader).toMutableList()
    val pluginProviders =
      @Suppress("DEPRECATION")
      ServiceLoader.load(com.apollographql.apollo.compiler.ApolloCompilerPluginProvider::class.java, classLoader).toList()
    for (pluginProvider in pluginProviders) {
      plugins.add(pluginProvider.create(ApolloCompilerPluginEnvironment(pluginArgumentsJson.orEmpty().toMapOfAny(), logger)))
    }
    return plugins
  }
}
