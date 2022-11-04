package com.apollographql.akip.services.internal

import com.apollographql.akip.services.ApolloProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager

class ApolloProjectServiceImpl(
    private val project: Project,
) : ApolloProjectService {

    override val isApolloProject: Boolean by lazy {
        var isApolloProject = false
        ProjectRootManager.getInstance(project).orderEntries().librariesOnly().forEachLibrary { library ->
            if (library.name?.contains("com.apollographql.apollo3") == true) {
                isApolloProject = true
                false
            } else {
                true
            }
        }
        isApolloProject
    }
}
