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
  @JvmField
  val FILE: Icon = load("/icons/graphql.svg")

  object Logos {
    @JvmField
    val GraphQL: Icon = FILE

    @JvmField
    val Relay: Icon = load("/icons/relay.svg")

    @JvmField
    val Apollo: Icon = load("/icons/apollo.svg")
  }

  object Files {
    @JvmField
    val GraphQL: Icon = FILE

    @JvmField
    val GraphQLConfig: Icon = load("/icons/graphqlConfig.svg")

    @JvmField
    val GraphQLSchema: Icon = load("/icons/graphqlSchema.svg")

    @JvmField
    val GraphQLScratch: Icon = LayeredIcon.layeredIcon { arrayOf(GraphQL, AllIcons.Actions.Scratch) }
  }

  object UI {
    @JvmField
    val GraphQLToolWindow: Icon = load("/icons/graphqlToolWindow.svg")

    @JvmField
    val GraphQLVariables: Icon = load("/icons/variable.svg")

    @JvmField
    val GraphQLNode: Icon = load("/icons/graphqlNode.svg")
  }

  object Schema {
    @JvmField
    val Field: Icon = load("/icons/field.svg")

    @JvmField
    val Scalar: Icon = load("/icons/scalar.svg")

    @JvmField
    val Enum: Icon = load("/icons/enum.svg")

    @JvmField
    val Type: Icon = load("/icons/type.svg")

    @JvmField
    val Interface: Icon = load("/icons/interface.svg")

    @JvmField
    val Query: Icon = load("/icons/query.svg")

    @JvmField
    val Attribute: Icon = load("/icons/attribute.svg")

    @JvmField
    val Subscription: Icon = load("/icons/subscription.svg")

    @JvmField
    val Mutation: Icon = load("/icons/mutation.svg")

    @JvmField
    val Fragment: Icon = load("/icons/fragment.svg")
  }

  private fun load(path: String): Icon {
    return IconLoader.getIcon(path, GraphQLIcons::class.java)
  }
}
