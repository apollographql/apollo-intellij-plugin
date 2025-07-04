package com.intellij.lang.jsgraphql.ide.validation

import com.intellij.openapi.util.Key

/**
 * Indicates whether GraphQL annotations should be disabled. This will be true when the LSP mode is enabled.
 */
object DisableGraphQLAnnotations : Key<Boolean>(DisableGraphQLAnnotations::class.java.name)
