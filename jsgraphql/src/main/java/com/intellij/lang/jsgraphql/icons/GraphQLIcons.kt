/*
 *  Copyright (c) 2015-present, Jim Kynde Meyer
 *  All rights reserved.
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 */
package com.intellij.lang.jsgraphql.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import javax.swing.Icon

object GraphQLIcons {
  object Logos {
    val GraphQL: Icon = load("/icons/graphql.svg")
  }

  object Files {
    @JvmField
    val GraphQL: Icon = Logos.GraphQL

    @JvmField
    val GraphQLConfig: Icon = load("/icons/graphqlConfig.svg")

    val GraphQLSchema: Icon = load("/icons/graphqlSchema.svg")

    val GraphQLScratch: Icon = LayeredIcon.layeredIcon { arrayOf(GraphQL, AllIcons.Actions.Scratch) }
  }

  object UI {
    @JvmField
    val GraphQLToolWindow: Icon = load("/icons/graphqlToolWindow.svg")

    val GraphQLVariables: Icon = load("/icons/variable.svg")

    val GraphQLNode: Icon = load("/icons/graphqlNode.svg")
  }

  private fun load(path: String): Icon {
    return IconLoader.getIcon(path, GraphQLIcons::class.java)
  }
}
