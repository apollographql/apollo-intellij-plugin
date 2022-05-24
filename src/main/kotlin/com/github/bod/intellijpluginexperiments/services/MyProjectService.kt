package com.github.bod.intellijpluginexperiments.services

import com.intellij.openapi.project.Project
import com.github.bod.intellijpluginexperiments.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
