package com.apollographql.ijplugin.icons

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class ApolloFileIconProvider : FileIconProvider {
  override fun getIcon(
      file: VirtualFile,
      flags: Int,
      project: Project?,
  ): Icon? {
    return if (file.name == "graphql") ApolloIcons.Node.GraphQL else null
  }
}
