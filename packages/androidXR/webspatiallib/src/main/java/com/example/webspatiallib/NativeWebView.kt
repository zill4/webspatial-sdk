package com.example.webspatiallib

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.lang.ref.WeakReference
import java.util.UUID

class CommandInfo {
    var command = "notFound"
    var windowContainerID = "notFound"
    var entityID = "notFound"
    var resourceID = "notFound"
    var requestID = "notFound"  // String ID like "rId_1" to match JS SDK
    lateinit var json: JsonObject
}

interface CommandManagerInterface {
    fun processCommand(senderWebview: NativeWebView, ci: CommandInfo)
}

/**
 * Callback interface for when a webspatial:// URL creates a spatial element.
 * This allows the native side to track created elements.
 */
interface SpatialElementCreatedCallback {
    fun onSpatialElementCreated(elementId: String, command: String, params: Map<String, String>)
}

class NativeWebView {
    companion object {
        lateinit var commandManager: CommandManagerInterface

        // Base URL for loading assets via WebViewAssetLoader (HTTPS)
        // This allows modern JS frameworks like Next.js to work properly
        const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
        const val ASSET_LOADER_PATH = "/assets/"

        fun getAssetLoaderBaseUrl(): String {
            return "https://$ASSET_LOADER_DOMAIN$ASSET_LOADER_PATH"
        }
    }


    val webView: WebView
    private val assetLoader: WebViewAssetLoader
    var windowComponent = WeakReference<SpatialWindowComponent>(null)

    // Callback for when spatial elements are created via webspatial:// URLs
    var spatialElementCreatedCallback: SpatialElementCreatedCallback? = null

    // Track child WebViews created for spatial elements (maps elementId to WebView)
    private val spatialChildWebViews = mutableMapOf<String, WebView>()

    @SuppressLint(
        "[ByDesign3.3]AvoidContentOrFileExecuteJS",
        "[ByDesign5.1]UsingAddJavaScriptInterface"
    )

    /**
     * Parse command info from JSON message.
     * Handles both legacy format and new webspatialBridge format.
     */
    fun getCommandInfo(json: JsonObject): CommandInfo? {
        val ret = CommandInfo()

        // Get requestID - can be string ("rId_1") or int
        val rID = json.get("requestID")
        if (rID != null) {
            ret.requestID = if (rID.jsonPrimitive.isString) {
                rID.jsonPrimitive.content
            } else {
                "rId_${rID.jsonPrimitive.int}"
            }
        } else {
            Log.w("WebSpatial", "Invalid command, missing requestID")
            return null
        }

        // Get command name
        val command = json.get("command")
        if (command != null) {
            ret.command = command.jsonPrimitive.content
        }

        // Get data object
        val data = json.get("data")
        if (data != null && data is JsonObject) {
            val windowContainerID = data.jsonObject.get("windowContainerID")
            if (windowContainerID != null) {
                ret.windowContainerID = windowContainerID.jsonPrimitive.content
            }

            val entityID = data.jsonObject.get("entityID")
            if (entityID != null) {
                ret.entityID = entityID.jsonPrimitive.content
            }

            val resourceID = data.jsonObject.get("resourceID")
            if (resourceID != null) {
                ret.resourceID = resourceID.jsonPrimitive.content
                if (ret.resourceID == "current") {
                    ret.resourceID = windowComponent.get()?.id ?: "currentIdNotFound"
                }
            }
        }

        ret.json = json
        return ret
    }

    /**
     * Send callback to JavaScript with command result.
     * Format matches visionOS: {id: "rId_1", data: {success: true, data: {...}}}
     */
    fun completeEvent(requestID: String, data: String = "{}", success: Boolean = true) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                // Format: {id: "rId_1", data: {success: true, data: actualData}}
                val callback = "window.__SpatialWebEvent && window.__SpatialWebEvent({id:'$requestID', data: {success: $success, data: $data}})"
                webView.evaluateJavascript(callback) { result ->
                    Log.d("WebSpatial", "Callback sent for $requestID")
                }
            } catch (e: Exception) {
                Log.e("WebSpatial", "Exception during callback: ${e.message}")
            }
        }
    }

    /**
     * Send error callback to JavaScript.
     */
    fun completeEventWithError(requestID: String, code: String, message: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val errorData = """{"code":"$code","message":"$message"}"""
                val callback = "window.__SpatialWebEvent && window.__SpatialWebEvent({id:'$requestID', data: {success: false, data: $errorData}})"
                webView.evaluateJavascript(callback) { result ->
                    Log.d("WebSpatial", "Error callback sent for $requestID: $message")
                }
            } catch (e: Exception) {
                Log.e("WebSpatial", "Exception during error callback: ${e.message}")
            }
        }
    }


    @SuppressLint(
        "[ByDesign5.1]UsingAddJavaScriptInterface",
        "[ByDesign3.3]AvoidContentOrFileExecuteJS"
    )
    constructor(context: Context) {
        // Enable WebView debugging for Chrome DevTools (chrome://inspect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        Log.i("WebSpatial", "=== Initializing NativeWebView ===")

        // Initialize WebViewAssetLoader to serve assets via HTTPS
        // This is required for modern JS frameworks like Next.js that use dynamic imports
        // Assets in android_asset/web/ will be available at:
        // https://appassets.androidplatform.net/assets/web/
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_LOADER_DOMAIN)
            .addPathHandler(ASSET_LOADER_PATH, WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        Log.i("WebSpatial", "WebViewAssetLoader initialized")
        Log.i("WebSpatial", "Base URL: ${getAssetLoaderBaseUrl()}")

        webView = WebView(context)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportZoom(true)
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.defaultTextEncodingName = "utf-8"
        // Enable file access for fallback
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.domStorageEnabled = true
        // These are deprecated but kept as fallback
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        webView.settings.allowUniversalAccessFromFileURLs = true

        // Add "WebSpatial/" to user agent so SDK detects this as a spatial environment
        val defaultUserAgent = webView.settings.userAgentString
        webView.settings.userAgentString = "$defaultUserAgent WebSpatial/${BuildConfig.NATIVE_VERSION}"
        Log.d("WebSpatial", "User agent set: ${webView.settings.userAgentString}")

        val nWebView = this;

        // Add __WebSpatialData interface for version info and legacy message handling
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun getNativeVersion(): String {
                return BuildConfig.NATIVE_VERSION
            }

            @JavascriptInterface
            fun getBackendName(): String {
                return "AndroidXR"
            }

            @JavascriptInterface
            fun androidNativeMessage(message: String) {
                // Legacy message handler - still supported for backwards compatibility
                processMessage(message)
            }
        }, "__WebSpatialData")

        // Add webspatialBridge interface - this is what the SDK uses to communicate
        webView.addJavascriptInterface(object {
            /**
             * Main entry point for SDK commands.
             * @param requestID String like "rId_1"
             * @param command Command name like "UpdateSpatialized2DElementProperties"
             * @param message JSON string with command data
             * @return Empty string for async, or JSON result for sync commands
             */
            @JavascriptInterface
            fun postMessage(requestID: String, command: String, message: String): String {
                Log.d("WebSpatial", "Bridge received: $command (id: $requestID)")

                // Build the full message JSON that CommandManager expects
                val fullMessage = buildJsonMessage(requestID, command, message)
                processMessage(fullMessage)

                // Return empty string - all commands are async via __SpatialWebEvent callback
                return ""
            }

            /**
             * Create a spatial element from a window.open('webspatial://...') call.
             * This is called by the injected JavaScript interceptor.
             * @param elementId The pre-generated element ID
             * @param command The webspatial command (e.g., "createSpatialized2DElement")
             * @param message JSON string with command parameters
             * @return JSON result
             */
            @JavascriptInterface
            fun createSpatialElement(elementId: String, command: String, message: String): String {
                Log.d("WebSpatial", "createSpatialElement: id=$elementId, command=$command")

                try {
                    // Parse parameters
                    val json = Json.parseToJsonElement(message).jsonObject
                    val params = mutableMapOf<String, String>()
                    json["params"]?.jsonObject?.entries?.forEach { (key, value) ->
                        params[key] = value.jsonPrimitive.content
                    }

                    // Notify callback to create the element
                    val callback = spatialElementCreatedCallback
                    Log.d("WebSpatial", "createSpatialElement callback is ${if (callback != null) "SET" else "NULL"}")

                    Handler(Looper.getMainLooper()).post {
                        Log.d("WebSpatial", "createSpatialElement invoking callback for $elementId")
                        try {
                            if (callback != null) {
                                callback.onSpatialElementCreated(elementId, command, params)
                                Log.d("WebSpatial", "createSpatialElement callback completed for $elementId")
                            } else {
                                Log.w("WebSpatial", "createSpatialElement callback was null!")
                            }
                        } catch (e: Exception) {
                            Log.e("WebSpatial", "createSpatialElement callback FAILED for $elementId: ${e.message}", e)
                        }
                    }

                    return """{"success": true, "elementId": "$elementId"}"""
                } catch (e: Exception) {
                    Log.e("WebSpatial", "Error creating spatial element: ${e.message}", e)
                    return """{"success": false, "error": "${e.message}"}"""
                }
            }
        }, "webspatialBridge")

        // Set up WebViewClient with WebViewAssetLoader integration
        webView.webViewClient = object : WebViewClient() {
            // Intercept requests and serve via WebViewAssetLoader
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.let {
                    Log.d("WebSpatial", "Intercepting request: ${it.url}")
                    val response = assetLoader.shouldInterceptRequest(it.url)
                    if (response != null) {
                        Log.d("WebSpatial", "Serving via AssetLoader: ${it.url}")
                        return response
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.i("WebSpatial", "=== Page started loading ===")
                Log.i("WebSpatial", "URL: $url")
                // Inject WebSpatial globals at page start
                injectWebSpatialGlobals()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i("WebSpatial", "=== Page finished loading ===")
                Log.i("WebSpatial", "URL: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("WebSpatial", "=== WebView ERROR ===")
                Log.e("WebSpatial", "Code: $errorCode, Description: $description")
                Log.e("WebSpatial", "Failing URL: $failingUrl")
            }
        }

        // Set up WebChromeClient to capture JavaScript console messages and handle window.open
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val level = when (it.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "E"
                        ConsoleMessage.MessageLevel.WARNING -> "W"
                        ConsoleMessage.MessageLevel.DEBUG -> "D"
                        else -> "I"
                    }
                    Log.println(
                        when (level) {
                            "E" -> Log.ERROR
                            "W" -> Log.WARN
                            "D" -> Log.DEBUG
                            else -> Log.INFO
                        },
                        "WebSpatial-JS",
                        "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                    )
                }
                return true
            }

            /**
             * Handle window.open() calls from JavaScript.
             * This intercepts webspatial:// URLs used by the SDK to create spatial elements.
             *
             * Flow:
             * 1. SDK calls window.open('webspatial://createSpatialized2DElement?...')
             * 2. onCreateWindow fires - we create a child WebView
             * 3. The webspatial:// URL navigation triggers shouldOverrideUrlLoading
             * 4. We parse the URL, create spatial element, and inject __SpatialId
             * 5. SDK polls for windowProxy.__SpatialId
             * 6. SDK then calls UpdateSpatialized2DElementProperties with the actual properties
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                Log.d("WebSpatial", "onCreateWindow called, isDialog=$isDialog, isUserGesture=$isUserGesture")

                if (view == null || resultMsg == null) {
                    Log.w("WebSpatial", "onCreateWindow: view or resultMsg is null")
                    return false
                }

                // Generate element ID upfront
                val elementId = "spatial_${UUID.randomUUID().toString().take(8)}"

                // Create a child WebView for this spatial element
                val childWebView = WebView(view.context)
                childWebView.settings.javaScriptEnabled = true

                // Track pending webspatial URL for this child WebView
                var pendingCommand: String? = null
                var pendingParams: Map<String, String>? = null

                childWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        Log.d("WebSpatial", "Child WebView URL loading: $url")

                        // Check if this is a webspatial:// URL
                        if (url.startsWith("webspatial://")) {
                            val parsed = parseWebSpatialUrl(url)
                            pendingCommand = parsed.first
                            pendingParams = parsed.second
                            Log.d("WebSpatial", "Parsed webspatial:// URL: command=${parsed.first}")

                            // Load about:blank to make the WebView "ready"
                            view?.loadUrl("about:blank")
                            return true
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let {
                            Log.d("WebSpatial", "Child WebView URL loading (legacy): $it")
                            if (it.startsWith("webspatial://")) {
                                val parsed = parseWebSpatialUrl(it)
                                pendingCommand = parsed.first
                                pendingParams = parsed.second

                                view?.loadUrl("about:blank")
                                return true
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("WebSpatial", "Child WebView page finished: $url, elementId=$elementId")

                        // Inject __SpatialId after page loads (whether about:blank or anything else)
                        val injectScript = """
                            window.__SpatialId = '$elementId';
                            console.log('[WebSpatial] Child window ready, __SpatialId: $elementId');
                        """.trimIndent()

                        view?.evaluateJavascript(injectScript) { result ->
                            Log.d("WebSpatial", "Injected __SpatialId=$elementId (result: $result)")
                        }

                        // Create the spatial element when webspatial:// command was received
                        pendingCommand?.let { cmd ->
                            spatialElementCreatedCallback?.onSpatialElementCreated(
                                elementId, cmd, pendingParams ?: emptyMap()
                            )
                            Log.i("WebSpatial", "Created spatial element: id=$elementId, command=$cmd")
                            pendingCommand = null
                            pendingParams = null
                        }
                    }
                }

                // Store the child WebView
                spatialChildWebViews[elementId] = childWebView

                // Send the WebView transport to accept the window.open result
                val transport = resultMsg.obj as? WebView.WebViewTransport
                if (transport != null) {
                    transport.webView = childWebView
                    resultMsg.sendToTarget()
                    Log.d("WebSpatial", "WebView transport sent for elementId=$elementId")
                    return true
                }

                Log.w("WebSpatial", "Failed to get WebView transport")
                return false
            }
        }
    }

    /**
     * Parse a webspatial:// URL into command and parameters.
     */
    private fun parseWebSpatialUrl(url: String): Pair<String, Map<String, String>> {
        try {
            val uri = Uri.parse(url)
            val command = uri.host ?: ""
            val params = mutableMapOf<String, String>()

            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { value ->
                    params[key] = value
                }
            }

            return Pair(command, params)
        } catch (e: Exception) {
            Log.e("WebSpatial", "Error parsing webspatial URL: ${e.message}", e)
            return Pair("", emptyMap())
        }
    }

    /**
     * Get a child WebView by element ID.
     */
    fun getChildWebView(elementId: String): WebView? {
        return spatialChildWebViews[elementId]
    }

    /**
     * Remove a child WebView when the spatial element is destroyed.
     */
    fun removeChildWebView(elementId: String) {
        spatialChildWebViews.remove(elementId)?.destroy()
    }

    /**
     * Build JSON message in the format CommandManager expects.
     */
    private fun buildJsonMessage(requestID: String, command: String, message: String): String {
        return try {
            val dataJson = if (message.isNotEmpty()) {
                Json.parseToJsonElement(message)
            } else {
                JsonObject(emptyMap())
            }

            val fullJson = buildJsonObject {
                put("requestID", requestID)
                put("command", command)
                put("data", dataJson)
            }
            fullJson.toString()
        } catch (e: Exception) {
            Log.e("WebSpatial", "Error building message: ${e.message}")
            """{"requestID":"$requestID","command":"$command","data":{}}"""
        }
    }

    /**
     * Process a JSON message and route to CommandManager.
     */
    private fun processMessage(message: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val json = Json.parseToJsonElement(message)
                val ci = getCommandInfo(json.jsonObject)
                if (ci != null) {
                    NativeWebView.commandManager.processCommand(this, ci)
                    Log.d("WebSpatial", "Processing command: ${ci.command}")
                }
            } catch (e: Exception) {
                Log.e("WebSpatial", "Error processing message: ${e.message}")
            }
        }
    }

    /**
     * Inject WebSpatial global variables into the page.
     * This includes a custom window.open handler for webspatial:// URLs.
     */
    private fun injectWebSpatialGlobals() {
        val script = """
            (function() {
                window.WebSpatailNativeVersion = '${BuildConfig.NATIVE_VERSION}';
                window.WebSpatailEnabled = true;

                // Map to track spatial elements created via window.open
                window.__spatialElements = window.__spatialElements || {};

                // Store original window.open
                const originalWindowOpen = window.open.bind(window);

                // Override window.open to handle webspatial:// URLs
                window.open = function(url, target, features) {
                    if (url && url.startsWith('webspatial://')) {
                        console.log('[WebSpatial] Intercepting window.open for: ' + url);

                        // Parse the webspatial:// URL
                        const parsed = new URL(url);
                        const command = parsed.hostname;
                        const params = {};
                        parsed.searchParams.forEach((value, key) => {
                            params[key] = value;
                        });

                        // Generate element ID
                        const elementId = 'spatial_' + Math.random().toString(36).substr(2, 8);

                        // Create a fake window object that the SDK can interact with
                        // Must have document.body.innerHTML for SDK compatibility
                        const fakeDocument = {
                            body: {
                                innerHTML: '',
                                appendChild: function() {},
                                removeChild: function() {},
                                style: {}
                            },
                            head: {
                                appendChild: function() {},
                                removeChild: function() {}
                            },
                            documentElement: {
                                style: {}
                            },
                            createElement: function(tag) {
                                return {
                                    style: {},
                                    setAttribute: function() {},
                                    getAttribute: function() { return null; },
                                    appendChild: function() {},
                                    innerHTML: ''
                                };
                            },
                            getElementById: function() { return null; },
                            querySelector: function() { return null; },
                            querySelectorAll: function() { return []; },
                            write: function() {},
                            close: function() {}
                        };

                        const fakeWindow = {
                            __SpatialId: null,
                            closed: false,
                            document: fakeDocument,
                            location: { href: 'about:blank' },
                            close: function() { this.closed = true; },
                            focus: function() {},
                            blur: function() {},
                            postMessage: function() {},
                            // The SDK checks for this method to know the window is "ready"
                            open: function(newUrl, newTarget) {
                                console.log('[WebSpatial] fakeWindow.open called: ' + newUrl);
                                if (newUrl) {
                                    this.location.href = newUrl;
                                }
                                return this;
                            }
                        };

                        // Store for lookup
                        window.__spatialElements[elementId] = fakeWindow;

                        // Send command to native via bridge
                        const message = JSON.stringify({
                            command: command,
                            elementId: elementId,
                            params: params
                        });

                        // Use the bridge to create the element
                        if (window.webspatialBridge && window.webspatialBridge.createSpatialElement) {
                            const result = window.webspatialBridge.createSpatialElement(elementId, command, message);
                            console.log('[WebSpatial] createSpatialElement result: ' + result);
                        }

                        // Inject __SpatialId after a small delay to simulate async creation
                        setTimeout(function() {
                            fakeWindow.__SpatialId = elementId;
                            console.log('[WebSpatial] Set __SpatialId on fakeWindow: ' + elementId);
                        }, 16);

                        return fakeWindow;
                    }

                    // For non-webspatial URLs, use original window.open
                    return originalWindowOpen(url, target, features);
                };

                console.log('[WebSpatial] Android XR bridge initialized, version: ${BuildConfig.NATIVE_VERSION}');
                console.log('[WebSpatial] window.open interceptor installed');
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d("WebSpatial", "Globals and window.open interceptor injected")
        }
    }

    fun navigateToURL(url: String) {
        webView.post {
            webView.loadUrl(url)
        }
    }

    /**
     * Simulates a click at the specified coordinates in the WebView.
     * Used to forward tap events from SpatialPanels to the underlying HTML content.
     *
     * @param x X coordinate in CSS pixels
     * @param y Y coordinate in CSS pixels
     */
    fun simulateClickAt(x: Float, y: Float) {
        webView.post {
            webView.evaluateJavascript("""
                (function() {
                    var elem = document.elementFromPoint($x, $y);
                    if (elem) {
                        // Create and dispatch click event
                        var clickEvent = new MouseEvent('click', {
                            bubbles: true,
                            cancelable: true,
                            clientX: $x,
                            clientY: $y
                        });
                        elem.dispatchEvent(clickEvent);
                        console.log('[WebSpatial] Simulated click on:', elem.tagName, 'at', $x, $y);
                    }
                })();
            """.trimIndent(), null)
        }
    }

    /**
     * Simulates a touch event at the specified coordinates in the WebView.
     * More accurate than click for touch interactions.
     *
     * @param x X coordinate in CSS pixels
     * @param y Y coordinate in CSS pixels
     * @param eventType Type of touch event: 'start', 'end', or 'move'
     */
    fun simulateTouchAt(x: Float, y: Float, eventType: String = "tap") {
        webView.post {
            webView.evaluateJavascript("""
                (function() {
                    var elem = document.elementFromPoint($x, $y);
                    if (elem) {
                        var touchObj = new Touch({
                            identifier: Date.now(),
                            target: elem,
                            clientX: $x,
                            clientY: $y,
                            radiusX: 2.5,
                            radiusY: 2.5,
                            rotationAngle: 0,
                            force: 1
                        });

                        if ('$eventType' === 'tap') {
                            // Simulate touchstart then touchend for a tap
                            var touchStart = new TouchEvent('touchstart', {
                                bubbles: true,
                                cancelable: true,
                                touches: [touchObj],
                                targetTouches: [touchObj],
                                changedTouches: [touchObj]
                            });
                            var touchEnd = new TouchEvent('touchend', {
                                bubbles: true,
                                cancelable: true,
                                touches: [],
                                targetTouches: [],
                                changedTouches: [touchObj]
                            });
                            elem.dispatchEvent(touchStart);
                            elem.dispatchEvent(touchEnd);

                            // Also dispatch click for compatibility
                            var clickEvent = new MouseEvent('click', {
                                bubbles: true,
                                cancelable: true,
                                clientX: $x,
                                clientY: $y
                            });
                            elem.dispatchEvent(clickEvent);
                        }
                        console.log('[WebSpatial] Simulated touch $eventType on:', elem.tagName, 'at', $x, $y);
                    }
                })();
            """.trimIndent(), null)
        }
    }
}