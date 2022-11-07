package com.apollographql.akip.listeners

import com.apollographql.akip.services.apolloProjectService
import com.apollographql.akip.util.log.logd
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class AkipProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        // TODO remove testing
        logd("isApolloProject=" + project.apolloProjectService().isApolloProject)
    }
}
