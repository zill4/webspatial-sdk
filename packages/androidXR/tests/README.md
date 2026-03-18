# Android XR Compatibility Test Suite

This test suite verifies that the Android XR implementation is compatible with the visionOS WebSpatial SDK.

## Running Tests

```bash
cd packages/androidXR/tests
pnpm install
pnpm test
```

## Test Coverage

### Command Coverage (53 tests)
Verifies all 28 WebSpatial commands from visionOS are implemented:

**Scene Management:**
- UpdateSpatialSceneProperties
- UpdateSceneConfig
- GetSpatialSceneState
- FocusScene

**2D Elements:**
- UpdateSpatialized2DElementProperties
- UpdateSpatializedElementTransform
- AddSpatializedElementToSpatialScene
- AddSpatializedElementToSpatialized2DElement

**Static 3D:**
- CreateSpatializedStatic3DElement
- UpdateSpatializedStatic3DElementProperties

**Dynamic 3D:**
- CreateSpatializedDynamic3DElement
- UpdateSpatializedDynamic3DElementProperties

**Entity System:**
- CreateSpatialEntity
- UpdateEntityProperties
- AddEntityToDynamic3D
- AddEntityToEntity
- RemoveEntityFromParent
- SetParentToEntity

**Geometry/Materials:**
- CreateGeometry
- CreateUnlitMaterial
- CreateModelComponent
- AddComponentToEntity

**Model Assets:**
- CreateModelAsset
- CreateSpatialModelEntity

**Lifecycle:**
- Destroy
- Inspect

**Legacy:**
- updateResource
- CheckWebViewCanCreate

### Gesture Coverage (37 tests)
Verifies all 10 gesture event types are implemented:

- spatialtap
- spatialdragstart, spatialdrag, spatialdragend
- spatialrotatestart, spatialrotate, spatialrotateend
- spatialmagnifystart, spatialmagnify, spatialmagnifyend

### Kotlin Structure (49 tests)
Verifies all required classes exist:

**Core:** SpatialObject, SpatialEntity, SpatialComponent, SpatialScene
**Elements:** Spatialized2DElement, SpatializedStatic3DElement, SpatializedDynamic3DElement
**Geometry:** BoxGeometry, PlaneGeometry, SphereGeometry, ConeGeometry, CylinderGeometry
**Materials:** SpatialMaterial, UnlitMaterial
**Components:** SpatialModelComponent, SpatialModelAsset, InputTargetComponent, CollisionComponent
**Utilities:** NativeWebView, WebMsg, GestureHandler, RotationGestureDetector

### Test Validity (14 tests)
Proves the tests actually test by:
- Verifying extraction logic works correctly
- Demonstrating tests would fail for missing implementations
- Checking handler bodies contain actual code
- Verifying file reading works as expected

## Results

```
Test Files  4 passed (4)
     Tests  153 passed (153)

Coverage:
- Command implementation: 100% (28/28 commands)
- Gesture events: 100% (10/10 event types)
- Kotlin classes: 100% (41+ classes verified)
```

## Test Files

| File | Tests | Description |
|------|-------|-------------|
| command-coverage.test.ts | 53 | Verifies all visionOS commands are implemented |
| gesture-coverage.test.ts | 37 | Verifies all gesture events are implemented |
| kotlin-structure.test.ts | 49 | Verifies all Kotlin classes exist |
| test-validity.test.ts | 14 | Proves tests actually test |
