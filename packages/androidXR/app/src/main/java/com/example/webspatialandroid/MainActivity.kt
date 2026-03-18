package com.example.webspatialandroid

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.webspatialandroid.ui.theme.WebSpatialAndroidTheme
import com.example.webspatiallib.Console
import com.example.webspatiallib.CoordinateSpaceMode
import com.example.webspatiallib.GestureHandler
import com.example.webspatiallib.NativeWebView
import com.example.webspatiallib.SpatialEntity
import com.example.webspatiallib.SpatialWindowComponent
import com.example.webspatiallib.SpatialWindowContainer
import com.example.webspatiallib.WindowContainerData
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

val debugSpaceToggle = false
val debugSpaceToggleTime: Long = 200
// Enable debug overlay to show spatial element state
val debugShowSpatialState = true
// Enable hardcoded test panels to verify SpatialPanel rendering works independently of web bridge
// Set to true to debug native panel rendering, false for normal operation
val debugShowTestPanels = false
// Load from WebViewAssetLoader (HTTPS) - this is the recommended approach by Google
// Assets in android_asset/web/ are served at https://appassets.androidplatform.net/assets/web/
var startURL = "${NativeWebView.getAssetLoaderBaseUrl()}web/index.html"
var console = Console()
var windowContainers = mutableStateListOf<SpatialWindowContainer>()

val cm = CommandManager()


class MainActivity : ComponentActivity() {

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Using WebViewAssetLoader (built into AndroidX WebKit) to serve assets via HTTPS
        // This is the Google-recommended approach for serving local content in WebViews
        // Assets are served from: https://appassets.androidplatform.net/assets/web/
        console.log("WebSpatial App Started -------- rootURL: " + startURL)
        console.log("Using WebViewAssetLoader for HTTPS asset serving")
        NativeWebView.commandManager = cm;

        // Initialize default window container with webpage
        val rootContainer = SpatialWindowContainer.getOrCreateSpatialWindowContainer("Root", WindowContainerData("Plain", "Root"))
        val rootEntity = SpatialEntity()
        rootEntity.coordinateSpace = CoordinateSpaceMode.ROOT
        rootEntity.setParentWindowContainer(rootContainer)
        val windowComponent = SpatialWindowComponent(this)
        windowComponent.loadURL(startURL)
        rootEntity.addComponent(windowComponent)
        windowContainers.add(rootContainer)


        enableEdgeToEdge()
        setContent {
            val session = LocalSession.current
            val spatialConfig = LocalSpatialConfiguration.current
            val isSpatialEnabled = LocalSpatialCapabilities.current.isSpatialUiEnabled
            if (isSpatialEnabled) {
                Subspace {
                    MySpatialContent(onRequestHomeSpaceMode = { spatialConfig.requestHomeSpaceMode() })
                }
            } else {
                My2DContent(onRequestFullSpaceMode = { spatialConfig.requestFullSpaceMode() })
            }
        }
    }

}

// Panel dimensions (used for positioning child panels)
val PANEL_WIDTH_DP = 1280
val PANEL_HEIGHT_DP = 800

private data class ResolvedSpatialElementPanel(
    val element: SpatialElementProps,
    val absoluteClientX: Double,
    val absoluteClientY: Double,
    val absoluteBackOffset: Double,
    val absoluteZIndex: Double,
    val childWebView: WebView?
)

private fun resolveSpatialElementPanels(
    elements: Collection<SpatialElementProps>,
    rootNativeWebView: NativeWebView?
): List<ResolvedSpatialElementPanel> {
    val childrenByParentId = elements
        .filter { !it.parentId.isNullOrBlank() }
        .groupBy { it.parentId!! }

    val resolved = mutableListOf<ResolvedSpatialElementPanel>()
    val visited = mutableSetOf<String>()

    fun visit(
        element: SpatialElementProps,
        inheritedClientX: Double,
        inheritedClientY: Double,
        inheritedBackOffset: Double,
        inheritedZIndex: Double
    ) {
        if (!visited.add(element.id)) {
            return
        }

        val (translationX, translationY, translationZ) = element.getTranslation()
        val absoluteClientX = inheritedClientX + element.clientX + translationX.toDouble()
        val absoluteClientY = inheritedClientY + element.clientY + translationY.toDouble()
        val absoluteBackOffset = inheritedBackOffset + element.backOffset + translationZ.toDouble()
        val absoluteZIndex = inheritedZIndex + element.zIndex
        val childWebView = rootNativeWebView?.getChildWebView(element.id)
        val shouldRender =
            element.visible &&
                element.width > 0 &&
                element.height > 0 &&
                (childWebView != null || element.shouldRenderAsSpatialPanel())

        if (shouldRender) {
            resolved += ResolvedSpatialElementPanel(
                element = element,
                absoluteClientX = absoluteClientX,
                absoluteClientY = absoluteClientY,
                absoluteBackOffset = absoluteBackOffset,
                absoluteZIndex = absoluteZIndex,
                childWebView = childWebView
            )
        }

        childrenByParentId[element.id]
            .orEmpty()
            .sortedBy { it.zIndex + it.backOffset }
            .forEach { child ->
                visit(
                    element = child,
                    inheritedClientX = absoluteClientX,
                    inheritedClientY = absoluteClientY,
                    inheritedBackOffset = absoluteBackOffset,
                    inheritedZIndex = absoluteZIndex
                )
            }
    }

    elements
        .filter { it.parentId == null && it.attachedToScene }
        .sortedBy { it.zIndex + it.backOffset }
        .forEach { root ->
            visit(root, 0.0, 0.0, 0.0, 0.0)
        }

    elements
        .filter { it.id !in visited && it.parentId == null }
        .sortedBy { it.zIndex + it.backOffset }
        .forEach { orphan ->
            visit(orphan, 0.0, 0.0, 0.0, 0.0)
        }

    return resolved.sortedBy { it.absoluteZIndex + it.absoluteBackOffset }
}

@SuppressLint("RestrictedApi")
@Composable
fun MySpatialContent(onRequestHomeSpaceMode: () -> Unit) {
    val session = checkNotNull(LocalSession.current)
    val scope = rememberCoroutineScope()

    // Store session in holder for CommandManager access
    XRSessionHolder.setSession(session)

    // Observe spatial element state for enable-xr behavior
    val depthOffset by SpatialElementState.panelDepthOffset
    val material by SpatialElementState.panelMaterial
    val spatialElements = SpatialElementState.elements

    // DEBUG: Test panel OUTSIDE the main SpatialBox using SpatialRow
    // This tests if the issue is with SpatialBox or with panel rendering in general
    if (debugShowTestPanels) {
        SpatialRow {
            // Simple test panel - should appear to the left of main content
            SpatialPanel(
                SubspaceModifier
                    .width(250.dp)
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8b5cf6))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("OUTSIDE BOX", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
                        Text("SpatialRow test", color = Color.White, fontSize = 12.sp)
                        Text("No Z offset", color = Color.Yellow, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    // For every window container, displays its contents
    windowContainers.forEach { c ->
        // Use SpatialBox to position multiple panels in 3D space
        SpatialBox {
            val root = c.getEntities().entries.firstOrNull { it.value.coordinateSpace == CoordinateSpaceMode.ROOT }
            val rootWindowComponent =
                root?.value?.components?.find { it is SpatialWindowComponent } as? SpatialWindowComponent
            val rootNativeWebView = rootWindowComponent?.nativeWebView
            val spatialPanelElements = resolveSpatialElementPanels(spatialElements.values, rootNativeWebView)

            // Main WebView panel at Z=0 (the base layer)
            // Note: movable/resizable now use SpatialPanel parameters instead of modifiers in alpha09
            SpatialPanel(
                modifier = SubspaceModifier
                    .width(PANEL_WIDTH_DP.dp)
                    .height(PANEL_HEIGHT_DP.dp)
                    .offset(0.dp, 0.dp, 0.dp)
            ) {
                if (root != null) {
                    val wc = root.value.components.find { it is SpatialWindowComponent } as? SpatialWindowComponent
                    if (wc != null) {
                        SpatialWebViewUI(wc, Modifier)
                    }
                }
                Orbiter(
                    position = ContentEdge.Top,
                    offset = 20.dp,
                    offsetType = OrbiterOffsetType.InnerEdge,
                    alignment = Alignment.End,
                    shape = SpatialRoundedCornerShape(CornerSize(28.dp))
                ) {
                    HomeSpaceModeIconButton(
                        onClick = onRequestHomeSpaceMode,
                        modifier = Modifier.size(56.dp)
                    ).apply {
                        if (debugSpaceToggle) {
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(debugSpaceToggleTime)
                                onRequestHomeSpaceMode()
                            }
                        }
                    }
                }

                // Debug overlay showing spatial element state
                if (debugShowSpatialState) {
                    Orbiter(
                        position = ContentEdge.Top,
                        offset = 20.dp,
                        offsetType = OrbiterOffsetType.InnerEdge,
                        alignment = Alignment.Start,
                        shape = SpatialRoundedCornerShape(CornerSize(12.dp))
                    ) {
                        Surface(
                            color = Color(0xCC1A1A2E),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                val elementCount = spatialElements.size
                                val panelCount = spatialPanelElements.size
                                Text(
                                    text = "Spatial Elements: $elementCount\n" +
                                           "Spatial Panels: $panelCount\n" +
                                           "Material: $material",
                                    color = Color.White,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Create separate SpatialPanels for each spatialized element with depth
            spatialPanelElements.forEach { resolvedElement ->
                key(resolvedElement.element.id) {
                    val element = resolvedElement.element
                    // Get the NativeWebView for gesture forwarding
                    val wc = root?.value?.components?.find { it is SpatialWindowComponent } as? SpatialWindowComponent
                    val webView = wc?.nativeWebView
                    val childWebView = resolvedElement.childWebView

                    // Create gesture handler for this webview
                    val gestureHandler = remember(webView) {
                        webView?.let { GestureHandler(WeakReference(it)) }
                    }

                    // Create gesture callbacks that use GestureHandler
                    val gestureCallbacks = if (webView != null && gestureHandler != null) {
                        SpatialGestureCallbacks(
                            onTap = if (element.enableTapGesture) { x, y ->
                                webView.simulateClickAt(x, y)
                                // Also send spatialtap event via GestureHandler
                                gestureHandler.handleTap(element.id, Triple(x, y, element.backOffset.toFloat()))
                            } else null,
                            onDragStart = if (element.enableDragGesture) { x, y ->
                                gestureHandler.handleDragStart(element.id, Triple(x, y, element.backOffset.toFloat()))
                            } else null,
                            onDrag = if (element.enableDragGesture) { x, y, _, _ ->
                                gestureHandler.handleDrag(element.id, Triple(x, y, element.backOffset.toFloat()))
                            } else null,
                            onDragEnd = if (element.enableDragGesture) { _, _ ->
                                gestureHandler.handleDragEnd(element.id, Triple(0f, 0f, element.backOffset.toFloat()))
                            } else null,
                            onRotate = if (element.enableRotateGesture) { x, y, rotation ->
                                gestureHandler.handleRotate(element.id, rotation, Triple(x, y, element.backOffset.toFloat()))
                            } else null,
                            onMagnify = if (element.enableMagnifyGesture) { x, y, scale ->
                                gestureHandler.handleMagnify(element.id, scale, Triple(x, y, element.backOffset.toFloat()))
                            } else null
                        )
                    } else null

                    SpatializedElementPanel(
                        element = element,
                        absoluteClientX = resolvedElement.absoluteClientX,
                        absoluteClientY = resolvedElement.absoluteClientY,
                        absoluteBackOffset = resolvedElement.absoluteBackOffset,
                        childWebView = childWebView,
                        gestures = gestureCallbacks
                    )
                }
            }

            // DEBUG: Hardcoded test panels to verify SpatialPanel rendering works
            // These panels are independent of web bridge - if they appear, native rendering works
            if (debugShowTestPanels) {

                // Test panel 1: Near (50dp behind main panel)
                SpatialPanel(
                    SubspaceModifier
                        .width(200.dp)
                        .height(150.dp)
                        .offset((-300).dp, 100.dp, 50.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF22c55e))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("TEST PANEL 1", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("Z: 50dp", color = Color.White)
                            Text("Native test", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                // Test panel 2: Mid (100dp behind main panel)
                SpatialPanel(
                    SubspaceModifier
                        .width(200.dp)
                        .height(150.dp)
                        .offset((-300).dp, (-50).dp, 100.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFf59e0b))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("TEST PANEL 2", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("Z: 100dp", color = Color.White)
                            Text("Native test", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                // Test panel 3: Far (200dp behind main panel)
                SpatialPanel(
                    SubspaceModifier
                        .width(200.dp)
                        .height(150.dp)
                        .offset((-300).dp, (-200).dp, 200.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFef4444))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("TEST PANEL 3", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("Z: 200dp", color = Color.White)
                            Text("Native test", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

            }
        }
    }
}

/**
 * Gesture callbacks for spatial elements.
 * All coordinates are in WebView/CSS pixel space.
 */
data class SpatialGestureCallbacks(
    val onTap: ((Float, Float) -> Unit)? = null,
    val onDragStart: ((Float, Float) -> Unit)? = null,
    val onDrag: ((Float, Float, Float, Float) -> Unit)? = null, // (x, y, deltaX, deltaY)
    val onDragEnd: ((Float, Float) -> Unit)? = null,
    val onRotate: ((Float, Float, Float) -> Unit)? = null, // (x, y, rotationRadians)
    val onMagnify: ((Float, Float, Float) -> Unit)? = null  // (x, y, scale)
)

/**
 * Renders a single spatialized element as a SpatialPanel at the correct Z depth.
 * Handles tap, drag, rotate, and magnify gestures and forwards them to the WebView.
 *
 * @param element The spatial element properties
 * @param onTap Optional callback for tap events with (webViewX, webViewY) coordinates
 * @param gestures Full gesture callbacks (takes precedence over onTap if provided)
 */
@SuppressLint("RestrictedApi")
@Composable
fun SpatializedElementPanel(
    element: SpatialElementProps,
    absoluteClientX: Double = element.clientX,
    absoluteClientY: Double = element.clientY,
    absoluteBackOffset: Double = element.backOffset,
    childWebView: WebView? = null,
    onTap: ((Float, Float) -> Unit)? = null,
    gestures: SpatialGestureCallbacks? = null
) {
    // Get density for coordinate conversion
    val density = LocalDensity.current

    // Convert CSS pixels to dp (approximate, depends on screen density)
    // For XR, we use a fixed conversion factor
    val pxToDp = 1f // Adjust based on testing

    val widthDp = (element.width * pxToDp).dp
    val heightDp = (element.height * pxToDp).dp

    // Calculate position offset from panel center
    // clientX/Y are from top-left of WebView, we need offset from center
    val centerOffsetX = ((absoluteClientX + element.width / 2) - PANEL_WIDTH_DP / 2).dp
    val centerOffsetY = -((absoluteClientY + element.height / 2) - PANEL_HEIGHT_DP / 2).dp // Flip Y axis

    // Z offset: negative = towards viewer (backOffset is positive for "behind")
    // In our coordinate system, we want elements with backOffset to be BEHIND the main panel
    // So we use positive Z to push them back
    val zOffsetDp = (if (absoluteBackOffset > 0.0) {
        maxOf(absoluteBackOffset.toFloat(), 20f)
    } else {
        absoluteBackOffset.toFloat()
    }).dp

    val cornerRadiusDp = element.cornerRadius.dp

    SpatialPanel(
        SubspaceModifier
            .width(widthDp)
            .height(heightDp)
            .offset(centerOffsetX, centerOffsetY, zOffsetDp)
    ) {
        // Content of the spatial panel
        val bitmap = element.getBitmap()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadiusDp))
                .background(
                    // When we have a bitmap with transparent areas, we need a solid background
                    // to prevent seeing through to the black XR void
                    when (element.backgroundMaterial) {
                        "translucent" -> Color(0x88334466)
                        "thin" -> Color(0x44556677)
                        "thick" -> Color(0xCC223344)
                        "regular" -> Color(0x99445566)
                        "transparent" -> if (element.hasBitmapContent()) Color(0xFF1a1a2e) else Color.Transparent
                        "none" -> if (childWebView != null) Color.Transparent else Color(0xFF1a1a2e)
                        else -> Color(0xFF1a1a2e) // Default dark background
                    }
                )
                .then(
                    if (debugShowSpatialState) {
                        Modifier.border(2.dp, Color(0xFF4ade80), RoundedCornerShape(cornerRadiusDp))
                    } else Modifier
                )
                // Add tap gesture handling
                .then(
                    if (
                        childWebView == null &&
                        (onTap != null || gestures?.onTap != null) &&
                        element.enableTapGesture
                    ) {
                        Modifier.pointerInput(element.id + "_tap") {
                            detectTapGestures { offset ->
                                val webViewX = absoluteClientX + (offset.x / density.density)
                                val webViewY = absoluteClientY + (offset.y / density.density)
                                (gestures?.onTap ?: onTap)?.invoke(webViewX.toFloat(), webViewY.toFloat())
                            }
                        }
                    } else Modifier
                )
                // Add drag gesture handling
                .then(
                    if (childWebView == null && gestures != null && element.enableDragGesture) {
                        Modifier.pointerInput(element.id + "_drag") {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val webViewX = absoluteClientX + (offset.x / density.density)
                                    val webViewY = absoluteClientY + (offset.y / density.density)
                                    gestures.onDragStart?.invoke(webViewX.toFloat(), webViewY.toFloat())
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val webViewX = absoluteClientX + (change.position.x / density.density)
                                    val webViewY = absoluteClientY + (change.position.y / density.density)
                                    val deltaX = dragAmount.x / density.density
                                    val deltaY = dragAmount.y / density.density
                                    gestures.onDrag?.invoke(webViewX.toFloat(), webViewY.toFloat(), deltaX, deltaY)
                                },
                                onDragEnd = {
                                    // Note: We don't have final position here, use (0,0) as placeholder
                                    gestures.onDragEnd?.invoke(0f, 0f)
                                }
                            )
                        }
                    } else Modifier
                )
                // Add rotate and magnify (pinch) gesture handling
                .then(
                    if (
                        childWebView == null &&
                        gestures != null &&
                        (element.enableRotateGesture || element.enableMagnifyGesture)
                    ) {
                        Modifier.pointerInput(element.id + "_transform") {
                            detectTransformGestures { centroid, _, zoom, rotation ->
                                val webViewX = absoluteClientX + (centroid.x / density.density)
                                val webViewY = absoluteClientY + (centroid.y / density.density)

                                if (element.enableRotateGesture && rotation != 0f) {
                                    gestures.onRotate?.invoke(webViewX.toFloat(), webViewY.toFloat(), rotation)
                                }

                                if (element.enableMagnifyGesture && zoom != 1f) {
                                    gestures.onMagnify?.invoke(webViewX.toFloat(), webViewY.toFloat(), zoom)
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            if (childWebView != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        if (childWebView.parent is ViewGroup) {
                            (childWebView.parent as ViewGroup).removeView(childWebView)
                        }
                        childWebView.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(Color.Transparent.toArgb())
                        }
                    },
                )
            } else if (bitmap != null) {
                // Render captured bitmap
                // Use FillBounds to stretch the image to fill the panel exactly
                // This ensures the bitmap fills the entire panel area without letterboxing
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Spatial element ${element.id}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    alignment = androidx.compose.ui.Alignment.TopStart
                )
            } else {
                // Placeholder - show element info when no bitmap available
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📦",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Z: ${element.backOffset.toInt()}px",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${element.width.toInt()}×${element.height.toInt()}",
                        color = Color.Gray,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun My2DContent(onRequestFullSpaceMode: () -> Unit) {
    Surface(color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Add a button to transition to full space (maybe this should be handled by the webpage instead?)
            // Note: LocalHasXrSpatialFeature was removed in alpha09 - button always shown on XR devices
            if (true) {
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier.padding(32.dp)
                ).apply {
                    if (debugSpaceToggle) {
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(debugSpaceToggleTime)
                            onRequestFullSpaceMode()
                        }
                    }

                }
            }
            // In 2D mode (homespace) we can only show one panel so we pick the first window containers root entity
            windowContainers.forEach { c ->
                val root = c.getEntities().entries.firstOrNull { it.value.coordinateSpace == CoordinateSpaceMode.ROOT }
                if (root != null) {
                    val wc = root.value.components.find { it is SpatialWindowComponent } as? SpatialWindowComponent
                    if (wc != null) {
                        SpatialWebViewUI(wc, Modifier)
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.hello_android_xr), modifier = modifier)
}

@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    WebSpatialAndroidTheme {
        My2DContent(onRequestFullSpaceMode = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FullSpaceModeButtonPreview() {
    WebSpatialAndroidTheme {
        FullSpaceModeIconButton(onClick = {})
    }
}

@PreviewLightDark
@Composable
fun HomeSpaceModeButtonPreview() {
    WebSpatialAndroidTheme {
        HomeSpaceModeIconButton(onClick = {})
    }
}
