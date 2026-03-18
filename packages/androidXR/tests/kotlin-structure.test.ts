/**
 * Kotlin structure tests - verify all required classes and methods exist.
 */

import { describe, it, expect, beforeAll } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'
import {
  parseKotlinFile,
  findKotlinFiles,
  KotlinFile,
  KotlinClass,
} from './src/kotlin-parser.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const WEBSPATIALLIB_PATH = path.join(
  __dirname,
  '../webspatiallib/src/main/java/com/example/webspatiallib',
)

describe('Kotlin Structure Tests', () => {
  let kotlinFiles: KotlinFile[]
  let allClasses: Map<string, KotlinClass>

  beforeAll(() => {
    const files = findKotlinFiles(WEBSPATIALLIB_PATH)
    kotlinFiles = files.map(f => parseKotlinFile(f))
    allClasses = new Map()
    for (const file of kotlinFiles) {
      for (const cls of file.classes) {
        allClasses.set(cls.name, cls)
      }
    }
  })

  describe('Core Spatial Classes Exist', () => {
    const requiredClasses = [
      'SpatialObject',
      'SpatialEntity',
      'SpatialComponent',
      'SpatialScene',
      'Spatialized2DElement',
      'SpatializedStatic3DElement',
      'SpatializedDynamic3DElement',
    ]

    for (const className of requiredClasses) {
      it(`has class "${className}"`, () => {
        expect(
          allClasses.has(className),
          `Class "${className}" not found`,
        ).toBe(true)
      })
    }
  })

  describe('Geometry Classes Exist', () => {
    const geometryClasses = [
      'SpatialGeometry',
      'BoxGeometry',
      'PlaneGeometry',
      'SphereGeometry',
      'ConeGeometry',
      'CylinderGeometry',
    ]

    for (const className of geometryClasses) {
      it(`has class "${className}"`, () => {
        expect(
          allClasses.has(className),
          `Geometry class "${className}" not found`,
        ).toBe(true)
      })
    }
  })

  describe('Material Classes Exist', () => {
    const materialClasses = ['SpatialMaterial', 'UnlitMaterial']

    for (const className of materialClasses) {
      it(`has class "${className}"`, () => {
        expect(
          allClasses.has(className),
          `Material class "${className}" not found`,
        ).toBe(true)
      })
    }
  })

  describe('Component Classes Exist', () => {
    const componentClasses = [
      'SpatialModelComponent',
      'SpatialModelAsset',
      'InputTargetComponent',
      'CollisionComponent',
    ]

    for (const className of componentClasses) {
      it(`has class "${className}"`, () => {
        expect(
          allClasses.has(className),
          `Component class "${className}" not found`,
        ).toBe(true)
      })
    }
  })

  describe('Utility Classes Exist', () => {
    const utilityClasses = [
      'NativeWebView',
      'WebMsg',
      'GestureHandler',
      'RotationGestureDetector',
      'GestureUtils',
    ]

    for (const className of utilityClasses) {
      it(`has class/object "${className}"`, () => {
        expect(
          allClasses.has(className),
          `Utility class "${className}" not found`,
        ).toBe(true)
      })
    }
  })

  describe('SpatialObject inheritance', () => {
    it('SpatialEntity extends SpatialObject', () => {
      const spatialEntity = allClasses.get('SpatialEntity')
      expect(spatialEntity?.superclass).toContain('SpatialObject')
    })

    it('SpatialComponent extends SpatialObject', () => {
      const spatialComponent = allClasses.get('SpatialComponent')
      expect(spatialComponent?.superclass).toContain('SpatialObject')
    })

    it('SpatialScene extends SpatialObject', () => {
      const spatialScene = allClasses.get('SpatialScene')
      expect(spatialScene?.superclass).toContain('SpatialObject')
    })

    it('Spatialized2DElement extends SpatialObject', () => {
      const element = allClasses.get('Spatialized2DElement')
      expect(element?.superclass).toContain('SpatialObject')
    })
  })

  describe('SpatialGeometry is sealed class', () => {
    it('SpatialGeometry is sealed', () => {
      const geometry = allClasses.get('SpatialGeometry')
      expect(geometry?.isSealed).toBe(true)
    })
  })

  describe('WebMsg has required methods', () => {
    let webMsgContent: string

    beforeAll(() => {
      const webMsgPath = path.join(WEBSPATIALLIB_PATH, 'WebMsg.kt')
      webMsgContent = fs.readFileSync(webMsgPath, 'utf-8')
    })

    it('has send method', () => {
      expect(webMsgContent).toContain('fun send(')
    })

    it('has sendRaw method', () => {
      expect(webMsgContent).toContain('fun sendRaw(')
    })

    it('has sendTapEvent method', () => {
      expect(webMsgContent).toContain('fun sendTapEvent(')
    })

    it('has sendDragStartEvent method', () => {
      expect(webMsgContent).toContain('fun sendDragStartEvent(')
    })

    it('has sendDragEvent method', () => {
      expect(webMsgContent).toContain('fun sendDragEvent(')
    })

    it('has sendDragEndEvent method', () => {
      expect(webMsgContent).toContain('fun sendDragEndEvent(')
    })

    it('has sendRotateEvent method', () => {
      expect(webMsgContent).toContain('fun sendRotateEvent(')
    })

    it('has sendMagnifyEvent method', () => {
      expect(webMsgContent).toContain('fun sendMagnifyEvent(')
    })

    it('has sendObjectDestroy method', () => {
      expect(webMsgContent).toContain('fun sendObjectDestroy(')
    })

    it('has sendModelLoaded method', () => {
      expect(webMsgContent).toContain('fun sendModelLoaded(')
    })
  })

  describe('NativeWebView JavaScript interface', () => {
    let nativeWebViewContent: string

    beforeAll(() => {
      const nativeWebViewPath = path.join(
        WEBSPATIALLIB_PATH,
        'NativeWebView.kt',
      )
      nativeWebViewContent = fs.readFileSync(nativeWebViewPath, 'utf-8')
    })

    it('has __WebSpatialData interface', () => {
      expect(nativeWebViewContent).toContain('__WebSpatialData')
    })

    it('has webspatialBridge interface', () => {
      expect(nativeWebViewContent).toContain('webspatialBridge')
    })

    it('has postMessage method', () => {
      expect(nativeWebViewContent).toContain('fun postMessage(')
    })

    it('has getNativeVersion method', () => {
      expect(nativeWebViewContent).toContain('fun getNativeVersion(')
    })

    it('has getBackendName method', () => {
      expect(nativeWebViewContent).toContain('fun getBackendName(')
    })

    it('has completeEvent method', () => {
      expect(nativeWebViewContent).toContain('fun completeEvent(')
    })

    it('has completeEventWithError method', () => {
      expect(nativeWebViewContent).toContain('fun completeEventWithError(')
    })

    it('uses WebViewAssetLoader', () => {
      expect(nativeWebViewContent).toContain('WebViewAssetLoader')
    })

    it('has user agent with WebSpatial marker', () => {
      expect(nativeWebViewContent).toContain('WebSpatial/')
    })
  })

  it('summary: all structure checks pass', () => {
    const totalClasses = allClasses.size
    const totalFiles = kotlinFiles.length

    console.log('\n=== KOTLIN STRUCTURE SUMMARY ===')
    console.log(`Total Kotlin files: ${totalFiles}`)
    console.log(`Total classes/objects: ${totalClasses}`)

    const classList = Array.from(allClasses.keys()).sort()
    console.log(`Classes: ${classList.join(', ')}`)

    expect(totalClasses).toBeGreaterThan(15) // Should have many classes
    expect(totalFiles).toBeGreaterThan(10) // Should have many files
  })
})
