# WebSpatial Android XR Port

This document explains the architecture, development decisions, and implementation details for the Android XR port of WebSpatial.

## Overview

The Android XR port enables web developers to create spatial applications using the WebSpatial SDK that run on Android XR devices. It provides a native Android shell that hosts a WebView, bridging web content to the Android XR Jetpack SDK.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Application                          │
│              (Vite + React + WebSpatial SDK)                │
└─────────────────────────────────────────────────────────────┘
                              │
                    WebSpatial Bridge
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Android WebView                          │
│              (WebViewAssetLoader + NativeWebView)            │
└─────────────────────────────────────────────────────────────┘
                              │
                    JavaScript Interface
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Android XR Jetpack SDK                     │
│         (SpatialPanel, SubspaceModifier, Compose XR)         │
└─────────────────────────────────────────────────────────────┘
```

## Key Development Decisions

### 1. Vite + React over Next.js

**Decision:** Use Vite + React instead of Next.js for the demo application.

**Rationale:**

| Aspect | Next.js | Vite + React |
|--------|---------|--------------|
| **WebView Compatibility** | Poor - RSC, hydration issues, chunk loading errors | Excellent - native ES modules |
| **Build Output** | Complex - RSC payloads, multiple bundle formats | Simple - standard ES modules |
| **Static Export** | Requires special config, known issues | Works out of the box |
| **Module Loading** | Dynamic imports can fail in WebView | Native ES modules work reliably |
| **Bundle Size** | Larger due to RSC runtime | Smaller, optimized bundles |
| **Debugging** | Difficult - hydration mismatches obscure errors | Straightforward |

**Known Next.js Issues in WebViews:**
- React Server Components (RSC) don't work in static export properly
- Hydration errors in WebView environments due to browser API differences
- ChunkLoadError when loading dynamic imports
- `assetPrefix` configuration doesn't work reliably for all asset types
- iOS/Android WebViews auto-detecting content causes hydration mismatches

**References:**
- [Next.js WebView issues on GitHub](https://github.com/vercel/next.js/issues/67458)
- [ChunkLoadError discussions](https://github.com/vercel/next.js/issues/38507)
- [Static export limitations](https://github.com/vercel/next.js/discussions/43867)

### 2. WebViewAssetLoader over NanoHTTPD

**Decision:** Use AndroidX WebViewAssetLoader instead of an embedded HTTP server.

**Rationale:**

| Aspect | NanoHTTPD (Custom Server) | WebViewAssetLoader |
|--------|---------------------------|-------------------|
| **Protocol** | HTTP (cleartext) | HTTPS (secure) |
| **Secure Context** | No - limits Web APIs | Yes - full API access |
| **Setup Complexity** | Requires server management, port handling | Built-in, zero config |
| **Google Recommendation** | Not recommended | Official recommended approach |
| **Same-Origin Policy** | Must configure CORS | Automatic compliance |
| **Process Overhead** | Separate server thread | Integrated into WebView |

**WebViewAssetLoader Benefits:**
- Assets served via `https://appassets.androidplatform.net/assets/`
- Treated as secure context (enables Service Workers, Geolocation, etc.)
- No network security configuration needed for cleartext
- Built into AndroidX WebKit library
- Handles MIME types automatically

**Implementation:**
```kotlin
assetLoader = WebViewAssetLoader.Builder()
    .setDomain("appassets.androidplatform.net")
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

// In WebViewClient
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    return assetLoader.shouldInterceptRequest(request.url)
}
```

### 3. JavaScript Bridge Design

**Decision:** Match the visionOS bridge interface for SDK compatibility.

**Bridge Interface:**
```javascript
// Primary bridge for SDK communication
window.webspatialBridge.postMessage(requestID, command, message)

// Callback mechanism
window.__SpatialWebEvent({id: "rId_1", data: {success: true, data: {...}}})

// Version and platform detection
window.__WebSpatialData.getNativeVersion()
window.__WebSpatialData.getBackendName()
```

**Key Implementation Details:**
- Request IDs are strings (e.g., "rId_1") to match visionOS SDK expectations
- Callbacks use the same nested structure as visionOS
- User agent includes "WebSpatial/" marker for SDK detection
- Both sync and async command patterns supported

### 4. Asset Serving Path Structure

**Decision:** Serve assets from `assets/web/` with HTTPS URLs.

**URL Structure:**
```
https://appassets.androidplatform.net/assets/web/index.html
https://appassets.androidplatform.net/assets/web/assets/main-[hash].js
https://appassets.androidplatform.net/assets/web/assets/main-[hash].css
```

**File Structure:**
```
packages/androidXR/app/src/main/assets/
└── web/
    ├── index.html
    └── assets/
        ├── main-[hash].js
        ├── main-[hash].css
        └── ...
```

**Vite Configuration:**
```javascript
export default defineConfig({
  base: './',  // Relative paths for WebView compatibility
  build: {
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },
})
```

## Project Structure

```
packages/androidXR/
├── app/
│   ├── src/main/
│   │   ├── assets/web/           # Vite build output goes here
│   │   ├── java/.../
│   │   │   ├── MainActivity.kt   # Main activity with Spatial UI
│   │   │   ├── CommandManager.kt # Handles WebSpatial commands (30+ endpoints)
│   │   │   ├── SpatialElementState.kt  # Observable state for Compose
│   │   │   └── SpatialWebViewUI.kt     # Compose UI with gesture handling
│   │   ├── res/values/ids.xml    # Resource IDs for gesture detectors
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── webspatiallib/
│   ├── src/main/java/.../
│   │   ├── NativeWebView.kt      # WebView with bridge + AssetLoader
│   │   ├── SpatialWindowComponent.kt
│   │   ├── SpatialWindowContainer.kt
│   │   ├── SpatialObject.kt      # Base class + global registry
│   │   ├── SpatialEntity.kt      # Entity/Component system
│   │   ├── SpatialComponent.kt   # Base component class
│   │   ├── SpatialGeometry.kt    # Box, Plane, Sphere, Cone, Cylinder
│   │   ├── SpatialMaterial.kt    # UnlitMaterial, color parsing
│   │   ├── SpatialModelComponent.kt  # Model + ModelAsset components
│   │   ├── Spatialized2DElement.kt   # 2D, Static3D, Dynamic3D elements
│   │   ├── SpatialScene.kt       # Scene management
│   │   ├── WebMsg.kt             # Native → JS events
│   │   ├── GestureHandler.kt     # Gesture routing
│   │   ├── RotationGestureDetector.kt # Custom rotation detection
│   │   └── InputTargetComponent.kt    # Gesture input component
│   ├── src/test/java/.../        # Unit tests
│   │   ├── SpatialObjectTest.kt
│   │   ├── SpatialEntityTest.kt
│   │   ├── SpatialGeometryTest.kt
│   │   ├── SpatialMaterialTest.kt
│   │   ├── Spatialized2DElementTest.kt
│   │   ├── SpatialSceneTest.kt
│   │   └── GestureHandlerTest.kt
│   └── build.gradle.kts
└── ANDROIDXR.md                  # This file

apps/vite-xr-demo/
├── src/
│   ├── main.tsx                  # App entry point
│   ├── App.tsx                   # Main component with spatial demos
│   ├── index.css                 # Styles
│   └── vite-env.d.ts             # Type declarations
├── scripts/
│   └── build-android.js          # Deployment script
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## Development Workflow

### Building and Deploying

```bash
# From repo root
cd apps/vite-xr-demo

# Install dependencies (first time)
pnpm install

# Build the Vite app
npm run build

# Deploy to Android assets
npm run build:android

# Or do both in one step
npm run deploy:android
```

### Running on Android XR

1. Open Android Studio:
   ```bash
   npm run androidStudio  # From repo root
   ```

2. Clean and rebuild:
   - Build > Clean Project
   - Build > Rebuild Project

3. Run on Android XR Simulator:
   - Run > Run 'app'
   - Or use the green play button

### Debugging

**View Logs:**
```powershell
# PowerShell (Windows)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s WebSpatial:* WebSpatial-JS:*

# Bash (macOS/Linux)
adb logcat -s WebSpatial:* WebSpatial-JS:*
```

**Chrome DevTools:**
1. Enable USB debugging on device/emulator
2. Open `chrome://inspect` in Chrome
3. Find your WebView under "Remote Target"
4. Click "inspect" to open DevTools

**Key Log Tags:**
- `WebSpatial` - Native bridge operations
- `WebSpatial-JS` - JavaScript console output
- `AssetServer` - Asset serving (if using NanoHTTPD)

## WebSpatial SDK Integration

### Spatial Attributes

```tsx
// Make an element spatial
<div enable-xr>Content</div>

// Control depth (higher = further back)
<div enable-xr style={{ '--xr-back': 50 }}>
  50px behind panel surface
</div>

// Apply glass material
<div enable-xr style={{ '--xr-background-material': 'translucent' }}>
  Glass-like material
</div>
```

### Available Materials

| Material | Description |
|----------|-------------|
| `none` | No material effect |
| `transparent` | Fully transparent |
| `thin` | Light glass effect |
| `translucent` | Medium glass effect |
| `regular` | Standard glass effect |
| `thick` | Heavy glass effect |

### Platform Detection

```typescript
// Check if running in WebSpatial environment
const isWebSpatial = navigator.userAgent.includes('WebSpatial/')

// Get version
const version = navigator.userAgent.match(/WebSpatial\/([\d.]+)/)?.[1]

// Check bridge availability
const hasBridge = typeof window.webspatialBridge !== 'undefined'
```

## Dependencies

### Android App (app/build.gradle.kts)
- AndroidX Jetpack XR SDK
- Kotlin Coroutines
- Jetpack Compose
- kotlinx-serialization-json

### WebSpatial Lib (webspatiallib/build.gradle.kts)
- AndroidX WebKit (WebViewAssetLoader)
- kotlinx-serialization-json

### Vite Demo App (package.json)
- Vite 6.x
- React 18.x
- @webspatial/core-sdk
- @webspatial/react-sdk

## Command Implementation Status

The CommandManager implements the full WebSpatial API matching visionOS:

### Scene Management
- `UpdateSpatialSceneProperties` - Update scene corner radius, opacity, material
- `UpdateSceneConfig` - Update scene size, resizability, world scaling
- `GetSpatialSceneState` - Get current scene state (idle, pending, visible, fail)
- `FocusScene` - Focus a specific scene

### Spatialized 2D Elements
- `UpdateSpatialized2DElementProperties` - Update element position, size, material, gestures
- `UpdateSpatializedElementTransform` - Apply 4x4 transform matrix
- `AddSpatializedElementToSpatialScene` - Add element to scene
- `AddSpatializedElementToSpatialized2DElement` - Nest elements

### Static 3D Elements
- `CreateSpatializedStatic3DElement` - Create model from URL
- `UpdateSpatializedStatic3DElementProperties` - Update transform, visibility

### Dynamic 3D Elements
- `CreateSpatializedDynamic3DElement` - Create entity container
- `UpdateSpatializedDynamic3DElementProperties` - Update properties

### Entity/Component System
- `CreateSpatialEntity` - Create entity
- `UpdateEntityProperties` - Update entity transform
- `AddEntityToDynamic3D` - Add entity to Dynamic3D root
- `AddEntityToEntity` - Add entity as child of another
- `RemoveEntityFromParent` / `SetParentToEntity` - Entity hierarchy

### Geometry & Materials
- `CreateGeometry` - Create Box, Plane, Sphere, Cone, or Cylinder
- `CreateUnlitMaterial` - Create material with color/texture/opacity
- `CreateModelComponent` - Combine geometry with materials
- `AddComponentToEntity` - Add component to entity

### Model Assets
- `CreateModelAsset` - Load external glTF/GLB model
- `CreateSpatialModelEntity` - Create entity for model

### Lifecycle
- `Destroy` - Destroy any spatial object
- `Inspect` - Debug inspect object state

## Gesture System

The gesture system mirrors visionOS gesture handling:

### Supported Gestures
| Gesture | Events | Description |
|---------|--------|-------------|
| **Tap** | `spatialtap` | Single tap on element |
| **Drag** | `spatialdragstart`, `spatialdrag`, `spatialdragend` | Pan/move gesture |
| **Rotate** | `spatialrotatestart`, `spatialrotate`, `spatialrotateend` | Two-finger rotation |
| **Magnify** | `spatialmagnifystart`, `spatialmagnify`, `spatialmagnifyend` | Pinch-to-zoom |

### Event Data Structure
All events include 3D location information:
```typescript
interface SpatialGestureEvent {
  type: string;          // Event type (e.g., "spatialtap")
  objectId: string;      // Element ID
  detail: {
    location3D: { x: number, y: number, z: number };
    // Additional fields per gesture type
  }
}
```

### Enabling Gestures
Gestures are enabled per-element via `UpdateSpatialized2DElementProperties`:
```json
{
  "enableTapGesture": true,
  "enableDragGesture": true,
  "enableRotateGesture": true,
  "enableMagnifyGesture": true
}
```

## Known Limitations

1. **Android XR SDK Alpha**: The Android XR Jetpack SDK is in alpha; APIs may change
2. **Emulator Performance**: XR features work best on actual devices
3. **WebView Version**: Requires WebView 60+ (Android 7.1+, but SDK targets Android 14+)
4. **3D Models**: GLTF/GLB model loading via SceneCore is stubbed (loads asynchronously)
5. **Physical Materials**: Only UnlitMaterial is fully implemented

## Android XR Feature Parity Checklist

This section tracks feature parity between visionOS and Android XR implementations.

### Legend
- ✅ **Complete** - Feature works equivalently to visionOS
- 🟡 **Partial** - Feature works but with limitations
- 🚧 **In Progress** - Currently being implemented
- ❌ **Not Started** - Feature not yet implemented
- ⏸️ **Blocked** - Waiting on platform/SDK support

---

### Core Spatial Elements (enable-xr)

| Feature | Status | Notes |
|---------|--------|-------|
| Element creation (`CreateSpatialized2DElement`) | ✅ | Working |
| Element positioning (clientX, clientY) | ✅ | Working |
| Element sizing (width, height) | ✅ | Working |
| Z-depth positioning (`--xr-back`) | ✅ | Working - panels at different Z offsets |
| Transform matrix updates | ✅ | Working |
| Visibility tracking | ✅ | Working |
| Opacity | 🟡 | Needs verification |
| Corner radius (`border-radius`) | ❌ | Not yet implemented on panels |
| Z-index layering (`--xr-z-index`) | 🟡 | Basic support, needs testing |

### Content Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Bitmap capture (html2canvas) | ❌ | Infrastructure ready, html2canvas not installed |
| Bitmap transfer to native | ❌ | Bridge ready, needs bitmap data |
| Bitmap rendering on SpatialPanel | ❌ | Compose Image ready, needs bitmap |
| Content change detection | ❌ | MutationObserver ready, not wired |
| Dynamic content updates | ❌ | Needs bitmap recapture on changes |

### Background Materials (Glass Effects)

| Feature | Status | Notes |
|---------|--------|-------|
| `--xr-background-material: none` | ❌ | Not implemented |
| `--xr-background-material: transparent` | ❌ | Not implemented |
| `--xr-background-material: thin` | ❌ | Not implemented |
| `--xr-background-material: translucent` | ❌ | Not implemented |
| `--xr-background-material: regular` | ❌ | Not implemented |
| `--xr-background-material: thick` | ❌ | Not implemented |

### Gesture Handling

| Feature | Status | Notes |
|---------|--------|-------|
| Tap gesture detection | 🟡 | Native detection works, JS forwarding needed |
| Tap event forwarding to WebView | 🚧 | `simulateClickAt()` implemented, needs testing |
| Drag gesture (pan) | ❌ | GestureHandler ready, not wired to SDK |
| Drag start/move/end events | ❌ | Event structure defined |
| Rotate gesture (two-finger) | ❌ | RotationGestureDetector implemented |
| Rotate start/move/end events | ❌ | Event structure defined |
| Magnify gesture (pinch-to-zoom) | ❌ | Scale gesture detector ready |
| Magnify start/move/end events | ❌ | Event structure defined |
| 3D location in events | ❌ | location3D needs XR coordinate mapping |

### 3D Model Elements

| Feature | Status | Notes |
|---------|--------|-------|
| `CreateSpatializedStatic3DElement` | 🟡 | Command handled, rendering stubbed |
| Model loading (glTF/GLB) | ❌ | SceneCore integration needed |
| Model positioning | ❌ | Transform system ready |
| Model animations | ❌ | Not started |
| Model interactions | ❌ | Not started |

### Dynamic 3D Elements (Entity/Component)

| Feature | Status | Notes |
|---------|--------|-------|
| `CreateSpatializedDynamic3DElement` | 🟡 | Command handled, container ready |
| `CreateSpatialEntity` | ✅ | Working |
| Entity hierarchy (parent/child) | ✅ | Working |
| `CreateGeometry` (Box) | ✅ | Working |
| `CreateGeometry` (Plane) | ✅ | Working |
| `CreateGeometry` (Sphere) | ✅ | Working |
| `CreateGeometry` (Cone) | ✅ | Working |
| `CreateGeometry` (Cylinder) | ✅ | Working |
| `CreateUnlitMaterial` | ✅ | Working (color, opacity) |
| PhysicalMaterial (PBR) | ❌ | Not implemented |
| Geometry rendering | ❌ | SceneCore integration needed |

### Scene Management

| Feature | Status | Notes |
|---------|--------|-------|
| `UpdateSpatialSceneProperties` | ✅ | Command handled |
| Scene corner radius | ❌ | Not rendered |
| Scene opacity | ❌ | Not rendered |
| Scene material | ❌ | Not rendered |
| Scene resizing | ❌ | Not implemented |
| Multiple scenes | ❌ | Single scene only |

### Platform Bridge

| Feature | Status | Notes |
|---------|--------|-------|
| `webspatialBridge.postMessage` | ✅ | Working |
| Async callback (`__SpatialWebEvent`) | ✅ | Working |
| Platform detection (User-Agent) | ✅ | Working |
| Version reporting | ✅ | Working |
| Fake WindowProxy | ✅ | Fixed - skips style sync |

### Performance & Polish

| Feature | Status | Notes |
|---------|--------|-------|
| Reduce debug log spam | ❌ | Very verbose, needs log level control |
| Throttle property updates | ❌ | Updates every frame, needs batching |
| Efficient bitmap transfer | ❌ | Base64 is slow, consider alternatives |
| Memory management | ❌ | Element cleanup not verified |
| Error handling | 🟡 | Basic try/catch, needs improvement |

---

### Priority Implementation Order

Based on visual impact and user experience:

1. **P0 - Critical (Visual Basics)**
   - [ ] Bitmap capture and rendering (makes content visible)
   - [ ] Reduce log spam (performance/debugging)

2. **P1 - High (Interactivity)**
   - [ ] Tap gesture → WebView click forwarding
   - [ ] Background materials (glass effects)
   - [ ] Corner radius on panels

3. **P2 - Medium (3D Content)**
   - [ ] 3D model loading and rendering
   - [ ] Entity/Component geometry rendering

4. **P3 - Lower (Advanced)**
   - [ ] Drag/Rotate/Magnify gestures
   - [ ] Multiple scenes
   - [ ] Spatial audio

---

## Future Improvements

- [ ] Implement actual 3D model rendering via Android XR SceneCore
- [ ] Add PhysicalMaterial with PBR properties
- [ ] Support multiple window containers
- [ ] Implement spatial audio
- [ ] Add hand tracking integration
- [ ] Improve gesture coordinate mapping for XR space

## References

- [Android XR Developer Guide](https://developer.android.com/develop/xr)
- [WebViewAssetLoader Documentation](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader)
- [Vite Configuration](https://vite.dev/config/)
- [WebSpatial SDK Documentation](https://webspatial.dev)
