package com.apollographql.ijplugin.gradle

import com.apollographql.ijplugin.util.capitalizeFirstLetter
import java.io.File

fun projectIdeModelFile(projectDirectory: File) =
  File(projectDirectory, "build/generated/apollo/ide/project.json")

fun serviceIdeModelFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/generated/apollo/ide/services/$serviceName.json")

fun codegenSchemaOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/codegenSchemaOptionsFile")

fun irOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/irOptionsFile")

fun codegenOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/codegenOptions")

fun codegenOutputDir(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/generated/source/apollo/$serviceName")
