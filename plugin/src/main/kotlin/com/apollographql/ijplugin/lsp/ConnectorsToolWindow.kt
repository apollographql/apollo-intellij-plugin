package com.apollographql.ijplugin.lsp

import com.intellij.json.JsonFileType
import com.intellij.lang.jsgraphql.psi.GraphQLObjectTypeDefinition
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.TitledBorder

class ConnectorsToolWindow : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val contentManager = toolWindow.contentManager
        val connectorsPanel = ConnectorsPanel(project)

        val content = contentManager.factory.createContent(
            connectorsPanel,
            "Connectors",
            false
        )
        content.isCloseable = false
        contentManager.addContent(content)

        // Store reference for external access in the content's user data
        content.putUserData(CONNECTORS_PANEL_KEY, connectorsPanel)
    }

    companion object {
        private val CONNECTORS_PANEL_KEY = com.intellij.openapi.util.Key.create<ConnectorsPanel>("connectors.panel")
        const val CONNECTORS_TOOL_WINDOW_ID = "Connectors"

        @JvmStatic
        fun showToolWindow(project: Project, connectorId: String? = null) {
            runInEdt {
                val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow(CONNECTORS_TOOL_WINDOW_ID) ?: return@runInEdt

                toolWindow.show()

                connectorId?.let { id ->
                    val content = toolWindow.contentManager.contents.firstOrNull()
                    val connectorsPanel = content?.getUserData(CONNECTORS_PANEL_KEY)
                    connectorsPanel?.selectConnector(id)
                }
            }
        }
    }
}

class ConnectorsPanel(private val project: Project) : JBPanel<ConnectorsPanel>(BorderLayout()), Disposable {

    private val connectorComboBox = ComboBox<ConnectorItem>()
    private val runButton = JButton("Run Connector")
    private lateinit var inputEditor: EditorEx
    private lateinit var outputEditor: EditorEx

    init {
        setupInputEditor()
        setupOutputEditor()
        setupUI()
        setupEventHandlers()
        loadConnectors()
    }

    private fun setupInputEditor() {
        val inputFile = LightVirtualFile("connector-input.json", JsonFileType.INSTANCE, "{\n  \n}")
        val document = FileDocumentManager.getInstance().getDocument(inputFile)!!
        inputEditor = EditorFactory.getInstance().createEditor(document, project) as EditorEx

        inputEditor.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            additionalColumnsCount = 0
            additionalLinesCount = 2
            isRightMarginShown = false
        }

        inputEditor.isViewer = false
    }

    private fun setupOutputEditor() {
        val outputFile = LightVirtualFile("connector-output.json", JsonFileType.INSTANCE, "")
        val document = FileDocumentManager.getInstance().getDocument(outputFile)!!
        outputEditor = EditorFactory.getInstance().createEditor(document, project) as EditorEx

        outputEditor.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = true // Enable folding for JSON structure
            additionalColumnsCount = 0
            additionalLinesCount = 2
            isRightMarginShown = false
        }

        // Enable syntax highlighting for JSON
        val highlighterFactory = com.intellij.openapi.editor.highlighter.EditorHighlighterFactory.getInstance()
        outputEditor.highlighter = highlighterFactory.createEditorHighlighter(project, outputFile)

        outputEditor.isViewer = true
    }

    private fun setupUI() {
        // Top panel with connector selection and run button
        val topPanel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val gbc = GridBagConstraints()

        // Connector selection label
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(0, 0, 4, 8)
        topPanel.add(JBLabel("Connector:"), gbc)

        // Connector combo box
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.insets = JBUI.insets(0, 0, 4, 8)
        topPanel.add(connectorComboBox, gbc)

        // Run button
        gbc.gridx = 2
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.insets = JBUI.insetsTop(0)
        topPanel.add(runButton, gbc)

        add(topPanel, BorderLayout.NORTH)

        // Main content area with input/output editors
        val splitter = JBSplitter(true, 0.5f).apply {
            firstComponent = createInputPanel()
            secondComponent = createOutputPanel()
        }

        add(splitter, BorderLayout.CENTER)
    }

    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Input JSON")
        }

        val scrollPane = JBScrollPane(inputEditor.component)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createOutputPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Output")
        }

        val scrollPane = JBScrollPane(outputEditor.component)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun setupEventHandlers() {
        runButton.addActionListener {
            runConnector()
        }

        connectorComboBox.addActionListener {
            updateUIForSelectedConnector()
        }
    }

    private fun loadConnectors() {
        // Clear existing items
        connectorComboBox.removeAllItems()

        // Load actual connectors from GraphQL schema
        val connectors = discoverConnectorsFromSchema()

        if (connectors.isEmpty()) {
            // If no connectors found, add a placeholder
            connectorComboBox.addItem(ConnectorItem("no-connectors", "No connectors found", null))
            runButton.isEnabled = false
        } else {
            // Add discovered connectors
            connectors.forEach { connector ->
                connectorComboBox.addItem(connector)
            }
        }

        updateUIForSelectedConnector()
    }

    private fun discoverConnectorsFromSchema(): List<ConnectorItem> {
        val connectors = mutableListOf<ConnectorItem>()

        try {
            runReadAction {
                // Find all GraphQL files in the project using FileTypeIndex (bypassing GraphQL plugin config)
                val graphqlFileType = com.intellij.lang.jsgraphql.GraphQLFileType.INSTANCE
                val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
                val graphqlFiles = com.intellij.psi.search.FileTypeIndex.getFiles(graphqlFileType, scope)

                println("Found ${graphqlFiles.size} GraphQL files in project")

                // Convert VirtualFiles to PsiFiles and traverse them
                graphqlFiles.forEach { virtualFile ->
                    println("Processing GraphQL file: ${virtualFile.name}")
                    val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
                    println("PsiFile type: ${psiFile?.javaClass?.name}")
                    println("Is GraphQLFile: ${psiFile is com.intellij.lang.jsgraphql.psi.GraphQLFile}")

                    if (psiFile != null) {
                        // Try to process regardless of type, using a more generic visitor
                        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
                            override fun visitElement(element: com.intellij.psi.PsiElement) {
                                super.visitElement(element)

                                // Check if this is a GraphQL object type definition
                                if (element is GraphQLObjectTypeDefinition) {
                                    val typeName = element.typeNameDefinition?.name
                                    println("Found type: $typeName")

                                    // Check if the type itself has @connect directive
                                    val typeDirectives = element.directives
                                    typeDirectives.forEach { directive ->
                                        println("Found directive on type $typeName: ${directive.name}")
                                        if (directive.name == "connect") {
                                            val httpUrl = parseHttpUrlFromDirective(directive)
                                            println("Parsed URL for type $typeName: $httpUrl")
                                            if (typeName != null) {
                                                println("Adding type-level connector: $typeName")
                                                connectors.add(ConnectorItem(typeName, typeName, null, httpUrl))
                                            }
                                        }
                                    }

                                    // Check fields for @connect directives
                                    val fieldsDefinition = element.fieldsDefinition
                                    fieldsDefinition?.fieldDefinitionList?.forEach { field ->
                                        val fieldName = field.nameIdentifier.text
                                        field.directives.forEach { directive ->
                                            println("Found directive on field $typeName.$fieldName: ${directive.name}")
                                            if (directive.name == "connect") {
                                                val httpUrl = parseHttpUrlFromDirective(directive)
                                                println("Parsed URL for field $typeName.$fieldName: $httpUrl")
                                                if (typeName != null) {
                                                    val connectorId = "$typeName.$fieldName"
                                                    println("Adding field-level connector: $connectorId")
                                                    connectors.add(ConnectorItem(connectorId, typeName, fieldName, httpUrl))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    } else {
                        println("Could not get PsiFile for ${virtualFile.name}")
                    }
                }

                println("Total connectors found: ${connectors.size}")
            }
        } catch (e: Exception) {
            // If there's an error discovering connectors, fall back to empty list
            println("Error discovering connectors: ${e.message}")
            e.printStackTrace()
        }

        return connectors.distinctBy { it.id }.sortedBy { it.displayName }
    }

    private fun updateUIForSelectedConnector() {
        val selectedConnector = connectorComboBox.selectedItem as? ConnectorItem
        runButton.isEnabled = selectedConnector != null
    }

    private fun parseHttpUrlFromDirective(directive: com.intellij.lang.jsgraphql.psi.GraphQLDirective): String? {
        try {
            // Look for the "http" argument in the @connect directive
            directive.arguments?.argumentList?.forEach { argument ->
                if (argument.name == "http") {
                    // Parse the http argument value which should be an object like {GET: "url"}
                    val value = argument.value
                    if (value is com.intellij.lang.jsgraphql.psi.GraphQLObjectValue) {
                        value.objectFieldList.forEach { field ->
                            if (field.name == "GET") {
                                val urlValue = field.value
                                if (urlValue is com.intellij.lang.jsgraphql.psi.GraphQLStringValue) {
                                    // Remove quotes from the string value
                                    return urlValue.text.trim('"')
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing HTTP URL from directive: ${e.message}")
        }
        return null
    }

    private fun runConnector() {
        val selectedConnector = connectorComboBox.selectedItem as? ConnectorItem ?: return
        val inputJson = inputEditor.document.text

        if (selectedConnector.id == "no-connectors") {
            return // Don't run if no real connector is selected
        }

        // Show loading state
        runButton.isEnabled = false
        runButton.text = "Running..."

        // Make HTTP request asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = if (selectedConnector.httpUrl != null) {
                    executeHttpRequest(selectedConnector.httpUrl, inputJson)
                } else {
                    createErrorResponse("No HTTP URL configured for this connector")
                }

                // Update UI on EDT
                withContext(Dispatchers.Main) {
                    WriteAction.run<RuntimeException> {
                        outputEditor.document.setText(result)
                    }
                }
            } catch (e: Exception) {
                val errorResult = createErrorResponse("Failed to execute connector: ${e.message}")
                withContext(Dispatchers.Main) {
                    WriteAction.run<RuntimeException> {
                        outputEditor.document.setText(errorResult)
                    }
                }
            } finally {
                // Reset button state
                withContext(Dispatchers.Main) {
                    runButton.isEnabled = true
                    runButton.text = "Run Connector"
                }
            }
        }
    }

    private suspend fun executeHttpRequest(url: String, inputJson: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()

                // Process the URL and input JSON to handle templating and query params
                val processedUrl = processUrlWithInputs(url, inputJson)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(processedUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                // Parse response data as JsonElement
                val responseData = try {
                    if (isJsonResponse(response)) {
                        Json.parseToJsonElement(response.body())
                    } else {
                        JsonPrimitive(response.body())
                    }
                } catch (e: Exception) {
                    JsonPrimitive("Error parsing response: ${e.message}")
                }

                // Create properly structured response using buildJsonObject
                val connectorResponse = buildJsonObject {
                    put("status", response.statusCode())
                    put("url", processedUrl)
                    put("headers", buildJsonObject {
                        response.headers().map().forEach { (key, values) ->
                            put(key, JsonPrimitive(values.joinToString(", ")))
                        }
                    })
                    put("body", responseData)
                }

                // Pretty print JSON with indentation
                val prettyJson = Json { prettyPrint = true }
                prettyJson.encodeToString(JsonElement.serializer(), connectorResponse)
            } catch (e: IOException) {
                createErrorResponse("Network error: ${e.message}")
            } catch (e: Exception) {
                createErrorResponse("Request failed: ${e.message}")
            }
        }
    }

    private fun processUrlWithInputs(url: String, inputJson: String): String {
        try {
            val trimmedInput = inputJson.trim()
            if (trimmedInput.isEmpty() || trimmedInput == "{}") {
                return url // No inputs to process
            }

            // Use proper JSON parsing instead of regex
            val parsedInput = try {
                Json.decodeFromString<JsonElement>(trimmedInput)
            } catch (e: Exception) {
                println("Error parsing input JSON: ${e.message}")
                return url
            }

            // Handle {$args.id} replacement
            if (url.contains("{\$args.id}")) {
                val jsonObject = parsedInput.jsonObject
                val args = jsonObject["\$args"]?.jsonObject
                val id = args?.get("id")?.jsonPrimitive?.content
                if (id != null) {
                    return url.replace("{\$args.id}", id)
                }
            }

            // Handle $batch.id to query parameters
            if (parsedInput is JsonObject) {
                val batch = parsedInput["\$batch"]?.jsonArray
                if (batch != null) {
                    val ids = batch.mapNotNull { item ->
                        item.jsonObject["id"]?.jsonPrimitive?.content
                    }

                    if (ids.isNotEmpty()) {
                        val queryParams = ids.joinToString("&") { "userId=$it" }
                        return if (url.contains("?")) {
                            "$url&$queryParams"
                        } else {
                            "$url?$queryParams"
                        }
                    }
                }
            }

            return url
        } catch (e: Exception) {
            println("Error processing URL with inputs: ${e.message}")
            return url // Return original URL if processing fails
        }
    }

    private fun isJsonResponse(response: HttpResponse<String>): Boolean {
        val contentType = response.headers().firstValue("content-type").orElse("")
        return contentType.contains("application/json") &&
               response.body().trim().let { it.startsWith("{") || it.startsWith("[") }
    }

    private fun createErrorResponse(message: String): String {
        val errorResponse = buildJsonObject {
            put("status", "error")
            put("message", message)
            put("timestamp", java.time.Instant.now().toString())
        }
        return errorResponse.toString()
    }

    fun selectConnector(connectorId: String) {
        for (i in 0 until connectorComboBox.itemCount) {
            val item = connectorComboBox.getItemAt(i)
            if (item.id == connectorId) {
                connectorComboBox.selectedIndex = i
                break
            }
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(inputEditor)
        EditorFactory.getInstance().releaseEditor(outputEditor)
    }
}

data class ConnectorItem(
    val id: String,
    val typeName: String,
    val fieldName: String? = null,
    val httpUrl: String? = null
) {
    val displayName: String
        get() = if (fieldName != null) "$typeName.$fieldName" else typeName

    override fun toString(): String = displayName
}
