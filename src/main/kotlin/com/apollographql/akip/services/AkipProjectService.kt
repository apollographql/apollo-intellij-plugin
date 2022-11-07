package com.apollographql.akip.services

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface AkipProjectService {
    val isApolloProject: Boolean
}

fun Project.apolloProjectService() = service<AkipProjectService>()
