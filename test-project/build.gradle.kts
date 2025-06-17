import com.apollographql.apollo.annotations.ApolloExperimental

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.apollo)
}

dependencies {
  implementation(libs.apollo.runtime)
}

apollo {
  service("main") {
    packageName.set("com.example.generated")
    @OptIn(ApolloExperimental::class)
    generateInputBuilders.set(true)
  }
}
