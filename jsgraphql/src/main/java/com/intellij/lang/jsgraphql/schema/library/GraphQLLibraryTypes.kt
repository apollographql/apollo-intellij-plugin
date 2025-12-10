package com.intellij.lang.jsgraphql.schema.library

import com.intellij.lang.jsgraphql.GraphQLBundle

object GraphQLLibraryTypes {
  @JvmField
  val SPECIFICATION = GraphQLLibraryDescriptor("SPECIFICATION", GraphQLBundle.message("graphql.library.built.in"))

  val LINK_V1_0 = GraphQLLibraryDescriptor("LINK_V1_0", "Link v1.0")

  val KOTLIN_LABS_V0_3 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.3", "Kotlin Labs v0.3")
  val KOTLIN_LABS_V0_4 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.4", "Kotlin Labs v0.4")
  val KOTLIN_LABS_V0_5 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.5", "Kotlin Labs v0.5")

  val NULLABILITY_V0_4 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/nullability/v0.4", "Nullability v0.4")

  // Note: the cache library never supported cache v0.2 so the plugin doesn't either
  val CACHE_V0_1 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/cache/v0.1", "Cache v0.1")
  val CACHE_V0_3 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/cache/v0.3", "Cache v0.3")
  val CACHE_V0_4 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/cache/v0.4", "Cache v0.4")

  val FAKES_V0_0 = LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/fakes/v0.0", "Fakes v0.0")
}
