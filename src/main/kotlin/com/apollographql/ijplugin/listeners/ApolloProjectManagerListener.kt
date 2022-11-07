package com.apollographql.ijplugin.listeners

import com.apollographql.ijplugin.services.apolloProjectService
import com.apollographql.ijplugin.util.log.logd
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class ApolloProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        // TODO remove testing
        logd("isApolloProject=" + project.apolloProjectService().isApolloProject)
    }
}
