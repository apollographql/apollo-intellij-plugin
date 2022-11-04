package com.apollographql.akip.listeners

import com.apollographql.akip.services.ApolloProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class AkipProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        // TODO remove testing
        println("isApolloProject=" + project.service<ApolloProjectService>().isApolloProject)
    }


}
