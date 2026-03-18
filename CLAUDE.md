# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Initial setup (required: pnpm)
npm install pnpm -g
npm run setup              # pnpm install + buildPackages

# Development
npm run dev                # Start test-server at http://localhost:5173
npm run watchNPM           # Watch mode for core + react packages
npm run xcode              # Open visionOS Xcode project
npm run androidStudio      # Open Android XR project
npm run adbDevPorts        # Forward ports for Android device debugging

# Building
npm run buildPackages      # Build all @webspatial/* packages

# Testing
npm run test               # Run all package tests + TypeScript check
npm run ciTest             # Run CI integration tests
pnpm -r --filter '@webspatial/core-sdk' run test  # Single package test

# Package-specific commands
cd packages/core && npm run test:w   # Watch mode for core tests
cd packages/core && npm run coverage # Coverage report
```

## Architecture Overview

### Monorepo Structure

This is a pnpm workspace monorepo using Changesets for synchronized versioning of all packages.

**Published Packages** (in `packages/`):
- **`@webspatial/core-sdk`** - Framework-agnostic JS API for spatial computing
- **`@webspatial/react-sdk`** - React bindings with custom JSX runtime for spatial elements
- **`@webspatial/builder`** - CLI tool that packages web apps for visionOS deployment
- **`@webspatial/platform-visionos`** - visionOS native shell (Swift/Xcode)
- **`@webspatial/platform-androidxrapp`** - Android XR native shell (private, not published)

**Development Apps** (in `apps/`):
- **`test-server`** - Development/demo React application with multiple test scenes

### Platform Abstraction

The SDK abstracts platform differences through the `PlatformAbility` interface:
- **VisionOS**: Communicates via `window.webkit.messageHandlers.bridge`
- **Android XR**: Uses `window.webspatialBridge.postMessage` with request ID polling

Platform detection: `navigator.userAgent.indexOf('WebSpatial/') > 0`

### React SDK Build Variants

The React SDK produces multiple builds via conditional exports:
- `@webspatial/react-sdk` - Default visionOS build
- `@webspatial/react-sdk/web` - Web-only build (no XR runtime)
- `@webspatial/react-sdk/jsx-runtime` - Custom JSX runtime for `<model>`, `<spatial>` elements

Platform-specific files use `.web.ts` / `.web.tsx` extensions.

### Key Entry Points

- **Spatial API**: `packages/core/src/Spatial.ts` → `SpatialSession` factory
- **Platform adapters**: `packages/core/src/platform-adapter/`
- **React containers**: `packages/react/src/spatialized-container/`
- **Custom JSX**: `packages/react/src/jsx/jsx-shared.ts`

### Core Concepts

- **`Spatial`** - Entry point (similar to `navigator.xr`)
- **`SpatialSession`** - Factory for spatial resources
- **`SpatialScene`** - Root container for spatial elements
- **`Spatialized2DElement`** - HTML content in 3D space
- **`SpatializedStatic3DElement`** - 3D model viewers
- **`SpatialEntity`** - 3D content primitives with geometry/materials

### Environment Variable

`XR_ENV` - Used by test-server to switch between web/AVP modes at build time.

## Testing

- **Unit tests**: Vitest with jsdom environment
- **CI tests**: Mocha/Chai in `tests/ci-test/`
- **Pre-commit hooks**: `pnpm lint-staged && pnpm test`

## Code Style

- Formatting: Prettier (JS/TS), SwiftFormat (Swift)
- Pre-commit validates file sizes and character sets via `tools/scripts/`
