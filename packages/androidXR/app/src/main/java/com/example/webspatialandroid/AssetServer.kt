package com.example.webspatialandroid

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * Local HTTP server that serves web assets from the Android assets folder.
 *
 * This is necessary because Next.js and other modern web frameworks use
 * dynamic module loading that doesn't work correctly with file:// protocol.
 * Serving via HTTP ensures all JavaScript modules load correctly.
 */
class AssetServer(
    private val context: Context,
    port: Int = DEFAULT_PORT
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "AssetServer"
        const val DEFAULT_PORT = 8080
        const val ASSET_BASE_PATH = "web"

        // MIME type mappings
        private val MIME_TYPES = mapOf(
            "html" to "text/html",
            "htm" to "text/html",
            "js" to "application/javascript",
            "mjs" to "application/javascript",
            "css" to "text/css",
            "json" to "application/json",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "ico" to "image/x-icon",
            "woff" to "font/woff",
            "woff2" to "font/woff2",
            "ttf" to "font/ttf",
            "eot" to "application/vnd.ms-fontobject",
            "webmanifest" to "application/manifest+json",
            "txt" to "text/plain",
            "xml" to "application/xml",
            "webp" to "image/webp",
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "glb" to "model/gltf-binary",
            "gltf" to "model/gltf+json",
            "usdz" to "model/vnd.usdz+zip"
        )

        private var instance: AssetServer? = null

        fun getInstance(context: Context, port: Int = DEFAULT_PORT): AssetServer {
            return instance ?: AssetServer(context.applicationContext, port).also {
                instance = it
            }
        }

        fun getBaseUrl(port: Int = DEFAULT_PORT): String {
            // Use 127.0.0.1 instead of localhost for better WebView compatibility
            return "http://127.0.0.1:$port"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        var uri = session.uri

        // Log the request
        Log.d(TAG, "Request: ${session.method} $uri")

        // Handle root path
        if (uri == "/" || uri.isEmpty()) {
            uri = "/index.html"
        }

        // Remove leading slash and prepend asset base path
        val assetPath = "$ASSET_BASE_PATH${uri}"

        return try {
            // Try to open the asset
            val inputStream = context.assets.open(assetPath)
            val mimeType = getMimeType(uri)

            Log.d(TAG, "Serving: $assetPath (${mimeType})")

            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            // Asset not found - try with index.html for SPA routing
            if (!uri.contains(".")) {
                try {
                    val spaPath = "$ASSET_BASE_PATH/index.html"
                    val inputStream = context.assets.open(spaPath)
                    Log.d(TAG, "SPA fallback: $spaPath")
                    newChunkedResponse(Response.Status.OK, "text/html", inputStream)
                } catch (e2: IOException) {
                    Log.e(TAG, "404 Not Found: $assetPath")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found: $uri")
                }
            } else {
                Log.e(TAG, "404 Not Found: $assetPath")
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found: $uri")
            }
        }
    }

    private fun getMimeType(uri: String): String {
        val extension = uri.substringAfterLast('.', "").lowercase()
        return MIME_TYPES[extension] ?: "application/octet-stream"
    }

    fun startServer(): Boolean {
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "=== AssetServer started successfully ===")
            Log.i(TAG, "Listening on port: $listeningPort")
            Log.i(TAG, "Base URL: ${getBaseUrl(listeningPort)}")
            Log.i(TAG, "Asset base path: $ASSET_BASE_PATH")
            true
        } catch (e: IOException) {
            Log.e(TAG, "=== Failed to start AssetServer ===")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun stopServer() {
        stop()
        Log.i(TAG, "Server stopped")
    }

    fun getUrl(path: String = ""): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "http://localhost:$listeningPort$cleanPath"
    }
}
