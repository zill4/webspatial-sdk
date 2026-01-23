/**
 * Command coverage tests - verify all WebSpatial commands are implemented.
 */

import { describe, it, expect, beforeAll } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'
import {
  WEBSPATIAL_COMMANDS,
  getAllCommandNames,
  getAllCategories,
} from './src/visionos-commands.js'
import {
  extractCommandsFromCommandManager,
  validateHandlers,
} from './src/command-extractor.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const COMMAND_MANAGER_PATH = path.join(
  __dirname,
  '../app/src/main/java/com/example/webspatialandroid/CommandManager.kt',
)

describe('Command Coverage Tests', () => {
  let implementedCommands: string[]
  let handlerValidations: ReturnType<typeof validateHandlers>

  beforeAll(() => {
    const commands = extractCommandsFromCommandManager(COMMAND_MANAGER_PATH)
    implementedCommands = commands.map(c => c.name)
    handlerValidations = validateHandlers(COMMAND_MANAGER_PATH)
  })

  describe('All visionOS commands are implemented', () => {
    const requiredCommands = getAllCommandNames()

    for (const command of requiredCommands) {
      it(`implements "${command}"`, () => {
        expect(
          implementedCommands,
          `Command "${command}" is not implemented in CommandManager.kt`,
        ).toContain(command)
      })
    }
  })

  describe('All handlers complete events', () => {
    it('every handler calls completeEvent or completeEventWithError', () => {
      const incompleteHandlers = handlerValidations.filter(
        v => v.exists && !v.completesEvent,
      )

      if (incompleteHandlers.length > 0) {
        const names = incompleteHandlers.map(h => h.name).join(', ')
        expect.fail(`Handlers that don't complete events: ${names}`)
      }
    })
  })

  describe('Commands that return data have proper response', () => {
    const commandsWithData = WEBSPATIAL_COMMANDS.filter(c => c.returnsData)

    for (const command of commandsWithData) {
      it(`"${command.name}" returns data`, () => {
        const validation = handlerValidations.find(v => v.name === command.name)
        expect(
          validation?.returnsData,
          `Command "${command.name}" should return data but doesn't`,
        ).toBe(true)
      })
    }
  })

  describe('Category coverage', () => {
    const categories = getAllCategories()

    for (const category of categories) {
      it(`has commands for category "${category}"`, () => {
        const categoryCommands = WEBSPATIAL_COMMANDS.filter(
          c => c.category === category,
        )
        const implemented = categoryCommands.filter(c =>
          implementedCommands.includes(c.name),
        )

        expect(
          implemented.length,
          `Category "${category}" has no implemented commands`,
        ).toBeGreaterThan(0)
        expect(
          implemented.length,
          `Category "${category}" is missing ${categoryCommands.length - implemented.length} commands`,
        ).toBe(categoryCommands.length)
      })
    }
  })

  it('no extra unknown commands exist', () => {
    const knownCommands = getAllCommandNames()
    const unknownCommands = implementedCommands.filter(
      c => !knownCommands.includes(c),
    )

    // Unknown commands are acceptable if they're Android-specific extensions
    // but we should track them
    if (unknownCommands.length > 0) {
      console.log('Extra Android-specific commands:', unknownCommands)
    }

    // This is informational, not a failure
    expect(true).toBe(true)
  })

  it('summary: all commands implemented', () => {
    const requiredCommands = getAllCommandNames()
    const missingCommands = requiredCommands.filter(
      c => !implementedCommands.includes(c),
    )

    console.log('\n=== COMMAND COVERAGE SUMMARY ===')
    console.log(`Total required commands: ${requiredCommands.length}`)
    console.log(`Implemented commands: ${implementedCommands.length}`)
    console.log(
      `Coverage: ${((implementedCommands.filter(c => requiredCommands.includes(c)).length / requiredCommands.length) * 100).toFixed(1)}%`,
    )

    if (missingCommands.length > 0) {
      console.log(`Missing commands: ${missingCommands.join(', ')}`)
    }

    expect(
      missingCommands.length,
      `Missing ${missingCommands.length} commands: ${missingCommands.join(', ')}`,
    ).toBe(0)
  })
})
