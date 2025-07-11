/*
 * Copyright (c) 2018-present, Jim Kynde Meyer
 * All rights reserved.
 * <p>
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.intellij.lang.jsgraphql

import com.intellij.lang.jsgraphql.GraphQLSettings.GraphQLSettingsState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-wide GraphQL settings persisted in the .idea folder as graphql-settings.xml
 */
@Service(Service.Level.PROJECT)
@State(name = "GraphQLSettings", storages = [Storage("graphql-settings.xml")])
class GraphQLSettings : PersistentStateComponent<GraphQLSettingsState> {

  private var state = GraphQLSettingsState()

  override fun getState(): GraphQLSettingsState = state

  override fun loadState(newState: GraphQLSettingsState) {
    state = newState
  }

  /* Introspection */

  var introspectionQuery: String
    get() = state.introspectionQuery
    set(introspectionQuery) {
      state.introspectionQuery = introspectionQuery
    }

  var isEnableIntrospectionDefaultValues: Boolean
    get() = state.enableIntrospectionDefaultValues
    set(enableIntrospectionDefaultValues) {
      state.enableIntrospectionDefaultValues = enableIntrospectionDefaultValues
    }

  var isEnableIntrospectionRepeatableDirectives: Boolean
    get() = state.enableIntrospectionRepeatableDirectives
    set(enableIntrospectionRepeatableDirectives) {
      state.enableIntrospectionRepeatableDirectives = enableIntrospectionRepeatableDirectives
    }

  var isOpenEditorWithIntrospectionResult: Boolean
    get() = state.openEditorWithIntrospectionResult
    set(openEditorWithIntrospectionResult) {
      state.openEditorWithIntrospectionResult = openEditorWithIntrospectionResult
    }

  /* Frameworks */

  class GraphQLSettingsState {
    var introspectionQuery = ""
    var enableIntrospectionDefaultValues = true
    var enableIntrospectionRepeatableDirectives = false
    var openEditorWithIntrospectionResult = true
  }

  companion object {
    @JvmStatic
    fun getSettings(project: Project): GraphQLSettings {
      return project.getService(GraphQLSettings::class.java)
    }
  }
}
