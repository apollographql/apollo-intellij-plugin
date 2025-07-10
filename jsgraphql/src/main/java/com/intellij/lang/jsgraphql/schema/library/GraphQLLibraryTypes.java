package com.intellij.lang.jsgraphql.schema.library;

import com.intellij.lang.jsgraphql.GraphQLBundle;
import com.intellij.lang.jsgraphql.GraphQLSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class GraphQLLibraryTypes {
  public static GraphQLLibraryDescriptor SPECIFICATION = new GraphQLLibraryDescriptor("SPECIFICATION") {
    @Override
    public @NotNull String getPresentableText() {
      return GraphQLBundle.message("graphql.library.built.in");
    }
  };

  public static GraphQLLibraryDescriptor RELAY = new GraphQLLibraryDescriptor("RELAY") {
    @Override
    public boolean isEnabled(@NotNull Project project) {
      return GraphQLSettings.getSettings(project).isRelaySupportEnabled();
    }

    @Override
    public @NotNull String getPresentableText() {
      return GraphQLBundle.message("graphql.library.relay");
    }
  };

  public static GraphQLLibraryDescriptor FEDERATION = new GraphQLLibraryDescriptor("FEDERATION") {
    @Override
    public boolean isEnabled(@NotNull Project project) {
      return GraphQLSettings.getSettings(project).isFederationSupportEnabled();
    }

    @Override
    public @NotNull String getPresentableText() {
      return GraphQLBundle.message("graphql.library.federation");
    }
  };

  public static GraphQLLibraryDescriptor LINK_V1_0 = new GraphQLLibraryDescriptor("LINK_V1_0") {
    @Override
    public @NotNull String getPresentableText() {
      return "Link v1.0";
    }
  };

  public static GraphQLLibraryDescriptor KOTLIN_LABS_V0_3 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.3", "Kotlin Labs v0.3");
  public static GraphQLLibraryDescriptor KOTLIN_LABS_V0_4 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.4", "Kotlin Labs v0.4");
  public static GraphQLLibraryDescriptor KOTLIN_LABS_V0_5 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/kotlin_labs/v0.5", "Kotlin Labs v0.5");

  public static GraphQLLibraryDescriptor NULLABILITY_V0_4 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/nullability/v0.4", "Nullability v0.4");

  public static GraphQLLibraryDescriptor CACHE_V0_1 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/cache/v0.1", "Cache v0.1");

  public static GraphQLLibraryDescriptor FAKES_V0_0 = new LinkableGraphQLLibraryDescriptor("https://specs.apollo.dev/fakes/v0.0", "Fakes v0.0");

  private GraphQLLibraryTypes() {
  }
}

