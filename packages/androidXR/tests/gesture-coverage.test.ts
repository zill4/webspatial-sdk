/**
 * Gesture event coverage tests - verify all gesture events are implemented.
 */

import { describe, it, expect, beforeAll } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'
import {
  GESTURE_EVENTS,
  getAllGestureEventTypes,
  getGestureEventsByCategory,
} from './src/gesture-events.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// Paths to gesture-related files
const WEB_MSG_PATH = path.join(
  __dirname,
  '../webspatiallib/src/main/java/com/example/webspatiallib/WebMsg.kt',
)
const GESTURE_HANDLER_PATH = path.join(
  __dirname,
  '../webspatiallib/src/main/java/com/example/webspatiallib/GestureHandler.kt',
)
const SPATIALIZED_2D_ELEMENT_PATH = path.join(
  __dirname,
  '../webspatiallib/src/main/java/com/example/webspatiallib/Spatialized2DElement.kt',
)

describe('Gesture Event Coverage Tests', () => {
  let webMsgContent: string
  let gestureHandlerContent: string
  let spatialized2DContent: string

  beforeAll(() => {
    webMsgContent = fs.readFileSync(WEB_MSG_PATH, 'utf-8')
    gestureHandlerContent = fs.readFileSync(GESTURE_HANDLER_PATH, 'utf-8')
    spatialized2DContent = fs.readFileSync(SPATIALIZED_2D_ELEMENT_PATH, 'utf-8')
  })

  describe('WebMsg sends all gesture event types', () => {
    const eventTypes = getAllGestureEventTypes()

    for (const eventType of eventTypes) {
      it(`sends "${eventType}" event`, () => {
        // Check if the event type is referenced in WebMsg.kt
        expect(
          webMsgContent,
          `WebMsg.kt does not send "${eventType}" event`,
        ).toContain(eventType)
      })
    }
  })

  describe('GestureHandler handles all gesture categories', () => {
    const categories = ['tap', 'drag', 'rotate', 'magnify'] as const

    for (const category of categories) {
      it(`handles ${category} gestures`, () => {
        // Check for handler method
        const handlerPattern = new RegExp(
          `handle${category.charAt(0).toUpperCase() + category.slice(1)}`,
          'i',
        )
        expect(
          gestureHandlerContent,
          `GestureHandler.kt does not handle ${category} gestures`,
        ).toMatch(handlerPattern)
      })
    }
  })

  describe('Drag gesture lifecycle', () => {
    it('has handleDragStart method', () => {
      expect(gestureHandlerContent).toContain('handleDragStart')
    })

    it('has handleDrag method', () => {
      expect(gestureHandlerContent).toContain('fun handleDrag(')
    })

    it('has handleDragEnd method', () => {
      expect(gestureHandlerContent).toContain('handleDragEnd')
    })

    it('tracks drag state (isDragging)', () => {
      expect(gestureHandlerContent).toContain('isDragging')
    })

    it('tracks drag start location', () => {
      expect(gestureHandlerContent).toContain('dragStartLocation')
    })
  })

  describe('Rotate gesture lifecycle', () => {
    it('has handleRotateStart method', () => {
      expect(gestureHandlerContent).toContain('handleRotateStart')
    })

    it('has handleRotate method', () => {
      expect(gestureHandlerContent).toContain('fun handleRotate(')
    })

    it('has handleRotateEnd method', () => {
      expect(gestureHandlerContent).toContain('handleRotateEnd')
    })

    it('tracks rotation state (isRotating)', () => {
      expect(gestureHandlerContent).toContain('isRotating')
    })
  })

  describe('Magnify gesture lifecycle', () => {
    it('has handleMagnifyStart method', () => {
      expect(gestureHandlerContent).toContain('handleMagnifyStart')
    })

    it('has handleMagnify method', () => {
      expect(gestureHandlerContent).toContain('fun handleMagnify(')
    })

    it('has handleMagnifyEnd method', () => {
      expect(gestureHandlerContent).toContain('handleMagnifyEnd')
    })

    it('tracks magnify state (isMagnifying)', () => {
      expect(gestureHandlerContent).toContain('isMagnifying')
    })
  })

  describe('Spatialized2DElement gesture flags', () => {
    it('has enableTapGesture flag', () => {
      expect(spatialized2DContent).toContain('enableTapGesture')
    })

    it('has enableDragGesture flag', () => {
      expect(spatialized2DContent).toContain('enableDragGesture')
    })

    it('has enableRotateGesture flag', () => {
      expect(spatialized2DContent).toContain('enableRotateGesture')
    })

    it('has enableMagnifyGesture flag', () => {
      expect(spatialized2DContent).toContain('enableMagnifyGesture')
    })

    it('has updateGestureFlags method', () => {
      expect(spatialized2DContent).toContain('fun updateGestureFlags')
    })
  })

  describe('WebMsg event data structure', () => {
    it('sends objectId with events', () => {
      expect(webMsgContent).toContain('objectId')
    })

    it('sends type with events', () => {
      expect(webMsgContent).toContain('"type"')
    })

    it('sends detail with events', () => {
      expect(webMsgContent).toContain('detail')
    })

    it('sends location3D coordinates', () => {
      expect(webMsgContent).toContain('location3D')
      expect(webMsgContent).toContain('"x"')
      expect(webMsgContent).toContain('"y"')
      expect(webMsgContent).toContain('"z"')
    })
  })

  it('summary: all gesture events covered', () => {
    const eventTypes = getAllGestureEventTypes()
    const implementedEvents = eventTypes.filter(e => webMsgContent.includes(e))

    console.log('\n=== GESTURE EVENT COVERAGE SUMMARY ===')
    console.log(`Total gesture event types: ${eventTypes.length}`)
    console.log(`Implemented event types: ${implementedEvents.length}`)
    console.log(
      `Coverage: ${((implementedEvents.length / eventTypes.length) * 100).toFixed(1)}%`,
    )

    const missingEvents = eventTypes.filter(e => !webMsgContent.includes(e))
    if (missingEvents.length > 0) {
      console.log(`Missing events: ${missingEvents.join(', ')}`)
    }

    expect(
      implementedEvents.length,
      `Missing ${eventTypes.length - implementedEvents.length} gesture events`,
    ).toBe(eventTypes.length)
  })
})
