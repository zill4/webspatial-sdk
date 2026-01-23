/**
 * Test validity tests - prove that our tests are actually testing.
 * These tests verify that our test framework would catch real issues.
 */

import { describe, it, expect, beforeAll } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'
import {
  extractCommandsFromCommandManager,
  extractHandlerImplementations,
  handlerCompletesEvent,
  handlerReturnsData,
} from './src/command-extractor.js'
import { parseKotlinFile, KotlinClass } from './src/kotlin-parser.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const COMMAND_MANAGER_PATH = path.join(
  __dirname,
  '../app/src/main/java/com/example/webspatialandroid/CommandManager.kt',
)

describe('Test Validity - Proving tests actually test', () => {
  describe('Command extractor actually extracts commands', () => {
    it('extracts at least 25 commands from CommandManager', () => {
      const commands = extractCommandsFromCommandManager(COMMAND_MANAGER_PATH)

      // This test proves extraction is working - we expect many commands
      console.log(
        `Extracted ${commands.length} commands: ${commands.map(c => c.name).join(', ')}`,
      )

      expect(commands.length).toBeGreaterThan(25)
    })

    it('extracts specific known commands', () => {
      const commands = extractCommandsFromCommandManager(COMMAND_MANAGER_PATH)
      const commandNames = commands.map(c => c.name)

      // These are commands we KNOW exist - tests prove extraction works
      expect(commandNames).toContain('UpdateSpatialSceneProperties')
      expect(commandNames).toContain('CreateSpatialEntity')
      expect(commandNames).toContain('CreateGeometry')
      expect(commandNames).toContain('Destroy')
    })

    it('would fail for non-existent commands', () => {
      const commands = extractCommandsFromCommandManager(COMMAND_MANAGER_PATH)
      const commandNames = commands.map(c => c.name)

      // This proves our test would fail if command didn't exist
      expect(commandNames).not.toContain('ThisCommandDoesNotExist')
      expect(commandNames).not.toContain('FakeCommand123')
    })
  })

  describe('Handler extractor actually extracts handler bodies', () => {
    let handlers: Map<string, string>

    beforeAll(() => {
      handlers = extractHandlerImplementations(COMMAND_MANAGER_PATH)
    })

    it('extracts at least 25 handler implementations', () => {
      console.log(`Extracted ${handlers.size} handlers`)
      expect(handlers.size).toBeGreaterThan(25)
    })

    it('handler bodies contain actual code', () => {
      const body = handlers.get('handleCreateSpatialEntity')

      expect(body).toBeDefined()
      expect(body!.length).toBeGreaterThan(50) // Non-trivial code
      expect(body).toContain('SpatialEntity') // Uses the class
      expect(body).toContain('completeEvent') // Completes the request
    })

    it('handlerCompletesEvent detects completeEvent calls', () => {
      // Test with actual handler body
      const body = handlers.get('handleCreateSpatialEntity')
      expect(handlerCompletesEvent(body!)).toBe(true)

      // Test would fail for code without completeEvent
      expect(handlerCompletesEvent('val x = 1')).toBe(false)
      expect(handlerCompletesEvent('fun foo() {}')).toBe(false)
    })

    it('handlerReturnsData detects data return', () => {
      // Test with handler that returns data
      const entityBody = handlers.get('handleCreateSpatialEntity')
      expect(handlerReturnsData(entityBody!)).toBe(true)

      // Test with handler that doesn't return data
      const scenePropsBody = handlers.get('handleUpdateSpatialSceneProperties')
      expect(handlerReturnsData(scenePropsBody!)).toBe(false)
    })
  })

  describe('Kotlin parser actually parses classes', () => {
    it('parses SpatialObject.kt and finds SpatialObject class', () => {
      const filePath = path.join(
        __dirname,
        '../webspatiallib/src/main/java/com/example/webspatiallib/SpatialObject.kt',
      )
      const parsed = parseKotlinFile(filePath)

      expect(parsed.classes.length).toBeGreaterThan(0)

      const spatialObject = parsed.classes.find(c => c.name === 'SpatialObject')
      expect(spatialObject).toBeDefined()
    })

    it('parses SpatialGeometry.kt and finds all geometry types', () => {
      const filePath = path.join(
        __dirname,
        '../webspatiallib/src/main/java/com/example/webspatiallib/SpatialGeometry.kt',
      )
      const parsed = parseKotlinFile(filePath)

      const classNames = parsed.classes.map(c => c.name)

      expect(classNames).toContain('SpatialGeometry')
      expect(classNames).toContain('BoxGeometry')
      expect(classNames).toContain('SphereGeometry')
      expect(classNames).toContain('PlaneGeometry')
    })

    it('would fail for non-existent classes', () => {
      const filePath = path.join(
        __dirname,
        '../webspatiallib/src/main/java/com/example/webspatiallib/SpatialObject.kt',
      )
      const parsed = parseKotlinFile(filePath)

      const nonExistent = parsed.classes.find(
        c => c.name === 'NonExistentClass',
      )
      expect(nonExistent).toBeUndefined()
    })
  })

  describe('File reading actually reads files', () => {
    it('CommandManager.kt exists and has content', () => {
      const content = fs.readFileSync(COMMAND_MANAGER_PATH, 'utf-8')

      expect(content.length).toBeGreaterThan(10000) // Substantial file
      expect(content).toContain('class CommandManager')
      expect(content).toContain('processCommand')
    })

    it('would fail for non-existent file', () => {
      expect(() => {
        fs.readFileSync('/nonexistent/path/file.kt', 'utf-8')
      }).toThrow()
    })
  })

  describe('Coverage verification', () => {
    it('all visionOS commands are actually implemented (not just listed)', () => {
      const commands = extractCommandsFromCommandManager(COMMAND_MANAGER_PATH)
      const handlers = extractHandlerImplementations(COMMAND_MANAGER_PATH)

      // Filter out geometry type case branches (box, plane, sphere, etc.)
      // which are internal switch cases, not external commands
      const geometryTypes = ['box', 'plane', 'sphere', 'cone', 'cylinder']
      const actualCommands = commands.filter(
        cmd => !geometryTypes.includes(cmd.name),
      )

      // For each command, verify its handler exists and has non-trivial code
      for (const cmd of actualCommands) {
        const handler = handlers.get(cmd.handlerName)
        expect(
          handler,
          `Handler ${cmd.handlerName} for command ${cmd.name} not found`,
        ).toBeDefined()
        expect(
          handler!.length,
          `Handler ${cmd.handlerName} for command ${cmd.name} is too short`,
        ).toBeGreaterThan(20)
      }
    })

    it('gesture event types actually appear in WebMsg.kt', () => {
      const webMsgPath = path.join(
        __dirname,
        '../webspatiallib/src/main/java/com/example/webspatiallib/WebMsg.kt',
      )
      const content = fs.readFileSync(webMsgPath, 'utf-8')

      // All these event types must be present
      const eventTypes = [
        'spatialtap',
        'spatialdragstart',
        'spatialdrag',
        'spatialdragend',
        'spatialrotatestart',
        'spatialrotate',
        'spatialrotateend',
        'spatialmagnifystart',
        'spatialmagnify',
        'spatialmagnifyend',
      ]

      for (const eventType of eventTypes) {
        expect(
          content,
          `Event type ${eventType} not found in WebMsg.kt`,
        ).toContain(eventType)
      }
    })
  })
})
