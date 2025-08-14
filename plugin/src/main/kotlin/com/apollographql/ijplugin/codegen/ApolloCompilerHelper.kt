@file:OptIn(ApolloInternal::class, ApolloExperimental::class)

package com.apollographql.ijplugin.codegen

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.compiler.ApolloCompiler
import com.apollographql.apollo.compiler.ApolloCompilerPlugin
import com.apollographql.apollo.compiler.UsedCoordinates
import com.apollographql.apollo.compiler.apolloCompilerRegistry
import com.apollographql.apollo.compiler.codegen.SourceOutput
import com.apollographql.apollo.compiler.codegen.writeTo
import com.apollographql.apollo.compiler.ir.IrOperations
import com.apollographql.apollo.compiler.toInputFiles
import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.gradle.ApolloKotlinService.Id
import com.apollographql.ijplugin.gradle.apolloKotlinProjectModelService
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.net.URLClassLoader

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

      // TODO Can only the schema service declare compiler plugins?
      val registry = apolloCompilerRegistry(
          arguments = mapOf("com.apollographql.cache.packageName" to "com.example"),
          logger = logger,
          warnIfNotFound = true,
          classLoader = URLClassLoader(
              schemaService.pluginDependencies!!.map { File(it).toURI().toURL() }.toTypedArray(),
              ApolloCompilerPlugin::class.java.classLoader
          )
      )

      val codegenSchema = ApolloCompiler.buildCodegenSchema(
          schemaFiles = schemaService.schemaPaths.map { File(it) }.toInputFiles(),
          logger = logger,
          codegenSchemaOptions = schemaService.codegenSchemaOptions!!,
          foreignSchemas = registry.foreignSchemas(),
          schemaTransform = registry.schemaDocumentTransform()
      )

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

      val irOperationsById = mutableMapOf<Id, IrOperations>()
      val allServiceIds = (allUpstreamServiceIds + service.id + allDownstreamServiceIds).distinct()
      for (serviceId in allServiceIds) {
        val service = service(serviceId)!!
        val irOperations = ApolloCompiler.buildIrOperations(
            codegenSchema = codegenSchema,
            executableFiles = service.executableFiles().toInputFiles(),
            upstreamCodegenModels = service.upstreamServiceIds.map { irOperationsById[it]!!.codegenModels },
            upstreamFragmentDefinitions = service.upstreamServiceIds.flatMap { irOperationsById[it]!!.fragmentDefinitions },
            options = service.irOptions!!,
            documentTransform = registry.executableDocumentTransform(),
            logger = logger,
        )
        irOperationsById[service.id] = irOperations
      }

      val schemaAndOperationsSourcesById = mutableMapOf<Id, SourceOutput>()
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
        val upstreamCodegenMetadata = allUpstreamServiceIds.map { schemaAndOperationsSourcesById[it]!!.codegenMetadata }
        val schemaAndOperationsSources = ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
            codegenSchema = codegenSchema,
            irOperations = irOperationsById[service.id]!!,
            downstreamUsedCoordinates = mergedDownstreamUsedCoordinates(irOperationsById, allDownstreamServiceIds + service.id),
            upstreamCodegenMetadata = upstreamCodegenMetadata,
            codegenOptions = service.codegenOptions!!,
            layout = registry.layout(codegenSchema),
            operationIdsGenerator = registry.toOperationIdsGenerator(),
            irOperationsTransform = registry.irOperationsTransform(),
            javaOutputTransform = registry.javaOutputTransform(),
            kotlinOutputTransform = registry.kotlinOutputTransform(),
            operationManifestFile = null, // TODO
        )
        schemaAndOperationsSourcesById[service.id] = schemaAndOperationsSources
        schemaAndOperationsSources.writeTo(service.codegenOutputDir!!, true, null)

        // TODO check this works
        if (upstreamCodegenMetadata.isEmpty()) {
          registry.schemaCodeGenerator().generate(codegenSchema.schema.toGQLDocument(), service.codegenOutputDir)
        }

        outputDirs.add(service.codegenOutputDir)
      }

      logd("Apollo compiler sources generated for service ${service.id} at ${service.codegenOutputDir}")
      return outputDirs
    } catch (e: Exception) {
      logw(e, "Failed to generate sources for service ${service.id}")
    }

    // TODO DataBuilders

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

  private fun mergedDownstreamUsedCoordinates(
      irOperationsById: Map<Id, IrOperations>,
      serviceIds: List<Id>,
  ): UsedCoordinates {
    return serviceIds.fold(UsedCoordinates()) { acc, serviceId ->
      acc.mergeWith(irOperationsById[serviceId]!!.usedCoordinates)
    }
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
}
