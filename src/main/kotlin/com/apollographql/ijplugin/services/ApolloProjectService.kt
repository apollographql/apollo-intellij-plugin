package com.apollographql.ijplugin.services

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface ApolloProjectService {
  val isApolloProject: Boolean
}

fun Project.apolloProjectService() = service<ApolloProjectService>()
