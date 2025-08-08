@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.compiler.ApolloCompiler
import com.apollographql.apollo.compiler.UsedCoordinates
import com.apollographql.apollo.compiler.codegen.SourceOutput
import com.apollographql.apollo.compiler.codegen.writeTo
import com.apollographql.apollo.compiler.ir.IrOperations
import com.apollographql.apollo.compiler.toCodegenOptions
import com.apollographql.apollo.compiler.toCodegenSchemaOptions
import com.apollographql.apollo.compiler.toInputFiles
import com.apollographql.apollo.compiler.toIrOptions
import com.apollographql.ijplugin.ApolloKotlinService2.Id
import java.io.File

data class ApolloKotlinService2(
    val gradleProjectPath: String = "",
    val serviceName: String = "",
    val schemaPaths: List<String> = emptyList(),
    val operationPaths: List<String> = emptyList(),
    val endpointUrl: String? = null,
    val endpointHeaders: Map<String, String>? = null,
    val upstreamServiceIds: List<Id> = emptyList(),
    val downstreamServiceIds: List<Id> = emptyList(),
) {
  data class Id(
      val gradleProjectPath: String = "",
      val serviceName: String = "",
  ) {
    override fun toString(): String {
      return "$gradleProjectPath/$serviceName"
    }

    companion object {
      fun fromString(string: String): Id? {
        val split = string.split("/", limit = 2)
        if (split.size != 2) return null
        return Id(split[0], split[1])
      }
    }
  }

  val id: Id
    get() = Id(gradleProjectPath, serviceName)
}

val logger = object : ApolloCompiler.Logger {
  override fun debug(message: String) {
    println("Debug: $message")
  }

  override fun error(message: String) {
    println("Error: $message")
  }

  override fun info(message: String) {
    println("Info: $message")
  }

  override fun warning(message: String) {
    println("Warning: $message")
  }
}

fun main() {
//  singleModule()
//  multiModule()
  multiModuleWithConfig()
}

private fun singleModule() {
  val schemaFiles = listOf(
      File("/Users/bod/gitrepo/apollo-kotlin-template/app/src/main/graphql/schema.graphqls"),
      File("/Users/bod/gitrepo/apollo-kotlin-template/app/src/main/graphql/extra.graphqls"),
  ).toInputFiles()
  val executableFiles = listOf(
      File("/Users/bod/gitrepo/apollo-kotlin-template/app/src/main/graphql/operations.graphql"),
  ).toInputFiles()

  val serviceName = "main"
  val codegenSchemaOptionsFile =
    File("/Users/bod/gitrepo/apollo-kotlin-template/app/build/generated/options/apollo/$serviceName/codegenSchemaOptions.json")
  val codegenOptionsFile =
    File("/Users/bod/gitrepo/apollo-kotlin-template/app/build/generated/options/apollo/$serviceName/codegenOptions.json")
  val irOptionsFile = File("/Users/bod/gitrepo/apollo-kotlin-template/app/build/generated/options/apollo/$serviceName/irOptions.json")

  val codegenSchema = ApolloCompiler.buildCodegenSchema(
      schemaFiles = schemaFiles,
      logger = logger,
      codegenSchemaOptions = codegenSchemaOptionsFile.toCodegenSchemaOptions(),
      foreignSchemas = emptyList(),
      schemaTransform = null,
  )
  ApolloCompiler.buildSchemaAndOperationsSources(
      codegenSchema = codegenSchema,
      executableFiles = executableFiles,
      irOptions = irOptionsFile.toIrOptions(),
      codegenOptions = codegenOptionsFile.toCodegenOptions(),
      layoutFactory = null,
      operationIdsGenerator = null,
      irOperationsTransform = null,
      javaOutputTransform = null,
      kotlinOutputTransform = null,
      documentTransform = null,
      logger = logger,
      operationManifestFile = null,
  ).writeTo(File("/Users/bod/Tmp/codegen"), true, null)
}

private fun multiModule() {
  val rootDir = "/Users/bod/gitrepo/apollo-kotlin-samples/multi-modules-and-services"

  // Schema
  val schemaFiles = setOf(File("$rootDir/graphqlSchema/src/main/graphql/servicea/schema.graphqls")).toInputFiles()
  val schemaExecutableInputFiles = setOf<File>().toInputFiles()
  val schemaCodegenSchemaOptionsFile = File("$rootDir/graphqlSchema/build/gtask/generateService-aApolloOptions/codegenSchemaOptionsFile")
  val schemaCodegenOptionsFile = File("$rootDir/graphqlSchema/build/gtask/generateService-aApolloOptions/codegenOptions")
  val schemaIrOptionsFile = File("$rootDir/graphqlSchema/build/gtask/generateService-aApolloOptions/irOptionsFile")

  // Shared
  val sharedExecutableInputFiles = setOf(
      File("$rootDir/graphqlShared/src/main/graphql/servicea/operations.graphql"),
      File("$rootDir/graphqlShared/src/main/graphql/servicea/fragments/CommontypeFields.graphql"),
      File("$rootDir/graphqlShared/src/main/graphql/servicea/fragments/ScreenFields.graphql"),
  ).toInputFiles()
  val sharedCodegenOptionsFile = File("$rootDir/graphqlShared/build/gtask/generateService-aApolloOptions/codegenOptions")
  val sharedIrOptionsFile = File("$rootDir/graphqlShared/build/gtask/generateService-aApolloOptions/irOptionsFile")

  // Feature 1
  val feature1ExecutableInputFiles = setOf(File("$rootDir/feature1/src/main/graphql/servicea/operations.graphql")).toInputFiles()
  val feature1CodegenOptionsFile = File("$rootDir/feature1/build/gtask/generateService-aApolloOptions/codegenOptions")
  val feature1IrOptionsFile = File("$rootDir/feature1/build/gtask/generateService-aApolloOptions/irOptionsFile")


  val codegenSchema = ApolloCompiler.buildCodegenSchema(
      schemaFiles = schemaFiles,
      logger = logger,
      codegenSchemaOptions = schemaCodegenSchemaOptionsFile.toCodegenSchemaOptions(),
      foreignSchemas = emptyList(),
      schemaTransform = null,
  )

  val schemaIrOperations = ApolloCompiler.buildIrOperations(
      codegenSchema = codegenSchema,
      executableFiles = schemaExecutableInputFiles,
      upstreamCodegenModels = emptyList(),
      upstreamFragmentDefinitions = emptyList(),
      options = schemaIrOptionsFile.toIrOptions(),
      documentTransform = null,
      logger = logger,
  )

  val sharedIrOperations = ApolloCompiler.buildIrOperations(
      codegenSchema = codegenSchema,
      executableFiles = sharedExecutableInputFiles,
      upstreamCodegenModels = listOf(schemaIrOperations.codegenModels),
      upstreamFragmentDefinitions = schemaIrOperations.fragmentDefinitions,
      options = sharedIrOptionsFile.toIrOptions(),
      documentTransform = null,
      logger = logger,
  )

  val feature1IrOperations = ApolloCompiler.buildIrOperations(
      codegenSchema = codegenSchema,
      executableFiles = feature1ExecutableInputFiles,
      upstreamCodegenModels = listOf(sharedIrOperations.codegenModels),
      upstreamFragmentDefinitions = sharedIrOperations.fragmentDefinitions,
      options = feature1IrOptionsFile.toIrOptions(),
      documentTransform = null,
      logger = logger,
  )

  val schemaSchemaAndOperationsSources = ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
      codegenSchema = codegenSchema,
      irOperations = schemaIrOperations,
      downstreamUsedCoordinates = sharedIrOperations.usedCoordinates.mergeWith(feature1IrOperations.usedCoordinates),
      upstreamCodegenMetadata = emptyList(),
      codegenOptions = schemaCodegenOptionsFile.toCodegenOptions(),
      layout = null,
      operationIdsGenerator = null,
      irOperationsTransform = null,
      javaOutputTransform = null,
      kotlinOutputTransform = null,
      operationManifestFile = null,
  )
  schemaSchemaAndOperationsSources.writeTo(File("/Users/bod/Tmp/codegen/multi/schema"), true, null)

  val sharedSchemaAndOperationsSources = ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
      codegenSchema = codegenSchema,
      irOperations = sharedIrOperations,
      downstreamUsedCoordinates = feature1IrOperations.usedCoordinates,
      upstreamCodegenMetadata = listOf(schemaSchemaAndOperationsSources.codegenMetadata),
      codegenOptions = sharedCodegenOptionsFile.toCodegenOptions(),
      layout = null,
      operationIdsGenerator = null,
      irOperationsTransform = null,
      javaOutputTransform = null,
      kotlinOutputTransform = null,
      operationManifestFile = null,
  )
  sharedSchemaAndOperationsSources.writeTo(File("/Users/bod/Tmp/codegen/multi/shared"), true, null)

  ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
      codegenSchema = codegenSchema,
      irOperations = feature1IrOperations,
      downstreamUsedCoordinates = UsedCoordinates(),
      upstreamCodegenMetadata = listOf(schemaSchemaAndOperationsSources.codegenMetadata, sharedSchemaAndOperationsSources.codegenMetadata),
      codegenOptions = feature1CodegenOptionsFile.toCodegenOptions(),
      layout = null,
      operationIdsGenerator = null,
      irOperationsTransform = null,
      javaOutputTransform = null,
      kotlinOutputTransform = null,
      operationManifestFile = null,
  ).writeTo(File("/Users/bod/Tmp/codegen/multi/feature1"), true, null)

}


fun getServices(): Map<Id, ApolloKotlinService2> {
  val rootDir = "/Users/bod/gitrepo/apollo-kotlin-samples/multi-modules-and-services"

  val services = mutableMapOf<Id, ApolloKotlinService2>()
  val schemaService = ApolloKotlinService2(
      gradleProjectPath = ":graphqlSchema",
      serviceName = "service-a",
      schemaPaths = listOf("$rootDir/graphqlSchema/src/main/graphql/servicea/schema.graphqls"),
      operationPaths = listOf("$rootDir/graphqlSchema/src/main/graphql/servicea"),
      endpointUrl = null,
      endpointHeaders = null,
      upstreamServiceIds = emptyList(),
      downstreamServiceIds = listOf(Id(":graphqlShared", "service-a")),
  )
  services[schemaService.id] = schemaService

  val sharedService = ApolloKotlinService2(
      gradleProjectPath = ":graphqlShared",
      serviceName = "service-a",
      schemaPaths = emptyList(),
      operationPaths = listOf("$rootDir/graphqlShared/src/main/graphql/servicea"),
      endpointUrl = null,
      endpointHeaders = null,
      upstreamServiceIds = listOf(Id(":graphqlSchema", "service-a")),
      downstreamServiceIds = listOf(Id(":feature1", "service-a")),
  )
  services[sharedService.id] = sharedService

  val feature1Service = ApolloKotlinService2(
      gradleProjectPath = ":feature1",
      serviceName = "service-a",
      schemaPaths = emptyList(),
      operationPaths = listOf("$rootDir/feature1/src/main/graphql/servicea"),
      endpointUrl = null,
      endpointHeaders = null,
      upstreamServiceIds = listOf(Id(":graphqlShared", "service-a")),
      downstreamServiceIds = emptyList(),
  )
  services[feature1Service.id] = feature1Service

  return services
}

private fun multiModuleWithConfig() {
  val services = getServices()
  val featureService = services[Id.fromString(":feature1/service-a")]!!
  val schemaService = services.findSchemaService(featureService)

  val codegenSchema = ApolloCompiler.buildCodegenSchema(
      schemaFiles = schemaService.schemaPaths.map { File(it) }.toInputFiles(),
      logger = logger,
      codegenSchemaOptions = schemaService.codegenSchemaOptionsFile().toCodegenSchemaOptions(),
      foreignSchemas = emptyList(),
      schemaTransform = null,
  )

  val irOperationsById = mutableMapOf<Id, IrOperations>()
  val allUpstreamServiceIds = services.allUpstreamServices(featureService)
  for (serviceId in allUpstreamServiceIds + featureService.id) {
    val service = services[serviceId]!!
    val irOperations = ApolloCompiler.buildIrOperations(
        codegenSchema = codegenSchema,
        executableFiles = service.executableFiles().toInputFiles(),
        upstreamCodegenModels = service.upstreamServiceIds.map { irOperationsById[it]!!.codegenModels },
        upstreamFragmentDefinitions = service.upstreamServiceIds.flatMap { irOperationsById[it]!!.fragmentDefinitions },
        options = service.irOptionsFile().toIrOptions(),
        documentTransform = null,
        logger = logger,
    )
    irOperationsById[service.id] = irOperations
  }

  val schemaAndOperationsSourcesByServiceId = mutableMapOf<Id, SourceOutput>()
  for (serviceId in allUpstreamServiceIds + featureService.id) {
    val service = services[serviceId]!!
    val schemaAndOperationsSources = ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
        codegenSchema = codegenSchema,
        irOperations = irOperationsById[service.id]!!,
        downstreamUsedCoordinates = mergedDownstreamUsedCoordinates(irOperationsById, services.allDownstreamServices(service) + service.id),
        upstreamCodegenMetadata = services.allUpstreamServices(service).map { schemaAndOperationsSourcesByServiceId[it]!!.codegenMetadata },
        codegenOptions = service.codegenOptionsFile().toCodegenOptions(),
        layout = null,
        operationIdsGenerator = null,
        irOperationsTransform = null,
        javaOutputTransform = null,
        kotlinOutputTransform = null,
        operationManifestFile = null,
    )
    schemaAndOperationsSourcesByServiceId[service.id] = schemaAndOperationsSources
    schemaAndOperationsSources.writeTo(File("/Users/bod/Tmp/codegen/multi2/${service.gradleProjectPath.removePrefix(":")}"), true, null)
  }
}

fun mergedDownstreamUsedCoordinates(
    irOperationsById: Map<Id, IrOperations>,
    serviceIds: List<Id>,
): UsedCoordinates {
  return serviceIds.fold(UsedCoordinates()) { acc, serviceId ->
    acc.mergeWith(irOperationsById[serviceId]!!.usedCoordinates)
  }
}

private fun Map<Id, ApolloKotlinService2>.findSchemaService(service: ApolloKotlinService2): ApolloKotlinService2 {
  if (service.upstreamServiceIds.isEmpty()) return service
  return findSchemaService(this[service.upstreamServiceIds.first()]!!)
}

private fun Map<Id, ApolloKotlinService2>.allUpstreamServices(service: ApolloKotlinService2): List<Id> {
  val upstreamServices = mutableListOf<Id>()
  for (upstreamServiceId in service.upstreamServiceIds) {
    upstreamServices.addAll(allUpstreamServices(this[upstreamServiceId]!!))
    upstreamServices.add(upstreamServiceId)
  }
  return upstreamServices
}

private fun Map<Id, ApolloKotlinService2>.allDownstreamServices(service: ApolloKotlinService2): List<Id> {
  val downstreamServices = mutableListOf<Id>()
  for (downstreamServiceId in service.downstreamServiceIds) {
    downstreamServices.addAll(allDownstreamServices(this[downstreamServiceId]!!))
    downstreamServices.add(downstreamServiceId)
  }
  return downstreamServices
}

private fun ApolloKotlinService2.gradlePathProjectOnDisk(): File {
  return File("/Users/bod/gitrepo/apollo-kotlin-samples/multi-modules-and-services/${gradleProjectPath.removePrefix(":")}")
}

private fun ApolloKotlinService2.codegenSchemaOptionsFile(): File {
  @Suppress("DEPRECATION")
  return File(gradlePathProjectOnDisk(), "build/gtask/generate${serviceName.capitalize()}ApolloOptions/codegenSchemaOptionsFile")
}

private fun ApolloKotlinService2.irOptionsFile(): File {
  @Suppress("DEPRECATION")
  return File(gradlePathProjectOnDisk(), "build/gtask/generate${serviceName.capitalize()}ApolloOptions/irOptionsFile")
}

private fun ApolloKotlinService2.codegenOptionsFile(): File {
  @Suppress("DEPRECATION")
  return File(gradlePathProjectOnDisk(), "build/gtask/generate${serviceName.capitalize()}ApolloOptions/codegenOptions")
}


private fun ApolloKotlinService2.executableFiles(): List<File> {
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
