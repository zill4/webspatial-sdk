# Android XR Feature Parity - Ralph Loop Prompt

Use this prompt with the Ralph Loop plugin to implement all remaining Android XR features.

## Quick Start

```bash
/ralph-loop "$(cat packages/androidXR/RALPH_LOOP_PROMPT.md)" --completion-promise "ANDROID_XR_FEATURE_PARITY_COMPLETE" --max-iterations 100
```

---

## PROMPT START

You are implementing Android XR feature parity for the WebSpatial SDK. This is an iterative loop - you will be called repeatedly until all features are complete.

### Project Context

- **Codebase**: WebSpatial SDK monorepo at `C:\Users\justc\code\webspatial-sdk`
- **Target**: Android XR platform using Jetpack Compose XR (2026 APIs)
- **Goal**: Achieve feature parity with visionOS implementation using creative Android-native approaches
- **Branch**: `feat/android-xr`

### Key Directories

```
packages/androidXR/app/src/main/java/com/example/webspatialandroid/
  - MainActivity.kt          # Compose UI with Subspace/SpatialPanel
  - CommandManager.kt        # WebSpatial command handling
  - SpatialElementState.kt   # Observable state for Compose
  - SpatialWebViewUI.kt      # WebView with gesture handling

packages/androidXR/webspatiallib/src/main/java/com/example/webspatiallib/
  - NativeWebView.kt         # WebView with bridge + AssetLoader
  - Spatialized2DElement.kt  # 2D spatial elements
  - SpatialEntity.kt         # Entity/Component system
  - SpatialGeometry.kt       # Geometries (Box, Sphere, etc.)
  - SpatialMaterial.kt       # Materials
  - GestureHandler.kt        # Gesture routing

packages/react/src/
  - spatialized-container/   # React spatial containers
  - utils/androidBitmapCapture.ts  # Bitmap capture utility

apps/vite-xr-demo/           # Test application
```

### Your Task Each Iteration

1. **Read the progress file**: `packages/androidXR/FEATURE_PROGRESS.md`
2. **Find the next incomplete feature** (marked with `[ ]`)
3. **Implement that feature** using 2026 Android XR APIs
4. **Test the implementation** by building: `npm run buildPackages`
5. **Update the progress file** to mark the feature complete `[x]`
6. **If ALL features are complete**, output: `<promise>ANDROID_XR_FEATURE_PARITY_COMPLETE</promise>`

### Feature Progress Tracking

On your FIRST iteration, create `packages/androidXR/FEATURE_PROGRESS.md` with this content:

```markdown
# Android XR Feature Progress

## P0 - Critical (Content Visibility)

### Content Rendering Pipeline
- [ ] 1.1 Add html2canvas to vite-xr-demo dependencies
- [ ] 1.2 Verify bitmap capture works in androidBitmapCapture.ts
- [ ] 1.3 Wire bitmap data through UpdateSpatialized2DElementProperties
- [ ] 1.4 Decode Base64 bitmap in SpatialElementState.kt
- [ ] 1.5 Render bitmap as Image in SpatialPanel Compose UI
- [ ] 1.6 Add MutationObserver for content change detection
- [ ] 1.7 Throttle bitmap updates (100ms debounce)

### Performance & Polish
- [ ] 2.1 Remove debug console.logs from PortalInstanceContext.ts
- [ ] 2.2 Add log level control to CommandManager.kt
- [ ] 2.3 Batch property updates (coalesce rapid changes)

## P1 - High (Interactivity & Visual Polish)

### Tap Gesture Forwarding
- [ ] 3.1 Wire tap detection on SpatialPanel to simulateClickAt()
- [ ] 3.2 Translate panel coordinates to WebView coordinates
- [ ] 3.3 Test tap interactions work on spatial elements

### Background Materials (Glass Effects)
- [ ] 4.1 Parse --xr-background-material CSS property
- [ ] 4.2 Map material names to Android XR equivalents
- [ ] 4.3 Apply SpatialElevation or Material3 effects for glass look
- [ ] 4.4 Implement: none, transparent, thin, translucent, regular, thick

### Visual Properties
- [ ] 5.1 Implement corner radius on SpatialPanel
- [ ] 5.2 Verify opacity works correctly
- [ ] 5.3 Test z-index layering between panels

## P2 - Medium (3D Content)

### 3D Model Loading
- [ ] 6.1 Integrate SceneCore for model loading
- [ ] 6.2 Load glTF/GLB models from URL
- [ ] 6.3 Position models using transform matrix
- [ ] 6.4 Handle model loading errors gracefully

### Entity Geometry Rendering
- [ ] 7.1 Render Box geometry via SceneCore
- [ ] 7.2 Render Sphere geometry
- [ ] 7.3 Render Plane geometry
- [ ] 7.4 Render Cylinder geometry
- [ ] 7.5 Render Cone geometry
- [ ] 7.6 Apply UnlitMaterial to geometries

## P3 - Lower (Advanced Features)

### Advanced Gestures
- [ ] 8.1 Implement drag gesture with spatialdrag events
- [ ] 8.2 Implement rotate gesture with spatialrotate events
- [ ] 8.3 Implement magnify/pinch gesture with spatialmagnify events
- [ ] 8.4 Map gesture coordinates to 3D space (location3D)

### Scene Management
- [ ] 9.1 Implement scene corner radius
- [ ] 9.2 Implement scene opacity
- [ ] 9.3 Implement scene material
- [ ] 9.4 Support scene resizing
- [ ] 9.5 Support multiple scenes

### Advanced Materials
- [ ] 10.1 Implement PhysicalMaterial with PBR properties
- [ ] 10.2 Support texture mapping
- [ ] 10.3 Support metallic/roughness

## Verification Checklist
- [ ] All P0 features complete
- [ ] All P1 features complete
- [ ] All P2 features complete
- [ ] All P3 features complete
- [ ] npm run buildPackages succeeds
- [ ] npm run test succeeds
- [ ] vite-xr-demo deploys and runs on Android XR emulator
```

### Implementation Guidelines

#### Creative Android XR Approaches (2026 APIs)

Since 1:1 visionOS parity isn't possible, use these creative approaches:

1. **Background Materials (Glass Effects)**
   - visionOS uses `UIBlurEffect` with vibrancy
   - Android XR: Use `SpatialElevation` + `Modifier.background()` with semi-transparent colors
   - Or use Compose Material3 `Surface` with `tonalElevation`
   - Consider `Modifier.blur()` if available in Compose XR 2026

2. **Bitmap Content on Panels**
   - visionOS uses WKWebView per element
   - Android XR: Capture HTML with html2canvas → Base64 PNG → decode to Bitmap → render with `Image()` composable
   - Use `BitmapFactory.decodeByteArray()` for decoding

3. **3D Model Rendering**
   - visionOS uses RealityKit
   - Android XR: Use SceneCore from `androidx.xr.scenecore`
   - Load models with `Session.createGltfEntity()` or equivalent 2026 API

4. **Gesture Mapping**
   - visionOS has built-in spatial gestures
   - Android XR: Use `Modifier.pointerInput()` with `detectTapGestures`, `detectDragGestures`
   - For rotation: Use custom `RotationGestureDetector` (already implemented)
   - Map 2D touch coordinates to 3D using panel position + depth

5. **Corner Radius**
   - visionOS: `cornerRadius` on views
   - Android XR: Use `Modifier.clip(RoundedCornerShape(radius.dp))` on SpatialPanel content

#### Build & Test Commands

```bash
# Build all packages
npm run buildPackages

# Run tests
npm run test

# Deploy to Android
cd apps/vite-xr-demo && npm run deploy:android

# View Android logs
adb logcat -s WebSpatial:* WebSpatial-JS:*
```

#### Code Quality Rules

1. **No breaking changes** to existing working features
2. **Add tests** for new functionality in `webspatiallib/src/test/`
3. **Handle errors gracefully** - don't crash on malformed data
4. **Use Kotlin idioms** - null safety, coroutines, flow
5. **Match visionOS API surface** where possible for SDK compatibility

### Iteration Logic

```
IF FEATURE_PROGRESS.md doesn't exist:
  CREATE it with the template above
  MARK this iteration complete

ELSE:
  READ FEATURE_PROGRESS.md
  FIND first item marked [ ]
  IMPLEMENT that feature
  BUILD and verify: npm run buildPackages
  IF build succeeds:
    UPDATE progress file: [ ] → [x]
  ELSE:
    FIX build errors before continuing

  IF all items are [x]:
    OUTPUT: <promise>ANDROID_XR_FEATURE_PARITY_COMPLETE</promise>
```

### Stuck? Recovery Strategies

If stuck on a feature for 3+ attempts:
1. Mark it as `[~]` (partial/blocked)
2. Add a note explaining the blocker
3. Move to next feature
4. Return to blocked items after completing others

If build keeps failing:
1. Check error messages carefully
2. Read relevant source files
3. Check Android XR documentation: https://developer.android.com/develop/xr
4. Simplify implementation - stub if needed, note in progress file

### Success Criteria

All features implemented means:
- Every `[ ]` in FEATURE_PROGRESS.md is now `[x]`
- `npm run buildPackages` succeeds without errors
- `npm run test` passes
- The vite-xr-demo can be deployed and shows spatial content

When ALL criteria are met, output exactly:

<promise>ANDROID_XR_FEATURE_PARITY_COMPLETE</promise>

## PROMPT END
