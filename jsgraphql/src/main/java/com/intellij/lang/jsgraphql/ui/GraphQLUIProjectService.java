package com.intellij.lang.jsgraphql.ui;

import com.intellij.lang.jsgraphql.ide.config.model.GraphQLConfigEndpoint;
import com.intellij.lang.jsgraphql.ide.project.schemastatus.GraphQLEndpointsModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.http.HttpRequest;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public interface GraphQLUIProjectService {
  String GRAPH_QL_VARIABLES_JSON = "graphql.variables.json";

  /**
   * Indicates that this virtual file backs a GraphQL variables editor
   */
  Key<Boolean> IS_GRAPH_QL_VARIABLES_VIRTUAL_FILE = Key.create(GRAPH_QL_VARIABLES_JSON);

  /**
   * Gets the variables editor associated with a .graphql query editor
   */
  Key<Editor> GRAPH_QL_VARIABLES_EDITOR = Key.create(GRAPH_QL_VARIABLES_JSON + ".variables.editor");

  /**
   * Gets the query editor associated with a GraphQL variables editor
   */
  Key<Editor> GRAPH_QL_QUERY_EDITOR = Key.create(GRAPH_QL_VARIABLES_JSON + ".query.editor");

  Key<JPanel> GRAPH_QL_QUERY_COMPONENT = Key.create(GRAPH_QL_VARIABLES_JSON + ".query.component");

  Key<GraphQLEndpointsModel> GRAPH_QL_ENDPOINTS_MODEL = Key.create("graphql.endpoints.model");

  Key<Boolean> GRAPH_QL_EDITOR_QUERYING = Key.create("graphql.editor.querying");

  void executeGraphQL(@NotNull Editor editor, @NotNull VirtualFile virtualFile);

  void showQueryResult(@NotNull String jsonResponse);

  void projectOpened();

  String stripClientDirectives(@NotNull Editor editor, String query);

  List<AnAction> getAdditionalActions();

  static GraphQLUIProjectService getInstance(@NotNull Project project) {
    return project.getService(GraphQLUIProjectService.class);
  }

  static void setHeadersFromOptions(GraphQLConfigEndpoint endpoint, HttpRequest request) {
    final Map<String, Object> headers = endpoint.getHeaders();
    for (Map.Entry<String, Object> entry : headers.entrySet()) {
      Object value = entry.getValue();
      if (value == null) continue;

      request.setHeader(entry.getKey(), String.valueOf(value));
    }
  }
}
