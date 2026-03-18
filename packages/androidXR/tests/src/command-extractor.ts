/**
 * Extract command handlers from CommandManager.kt to verify implementation.
 */

import * as fs from 'fs'
import * as path from 'path'

export interface ExtractedCommand {
  name: string
  handlerName: string
  lineNumber: number
}

/**
 * Extract all command handlers from CommandManager.kt.
 */
export function extractCommandsFromCommandManager(
  commandManagerPath: string,
): ExtractedCommand[] {
  const content = fs.readFileSync(commandManagerPath, 'utf-8')
  const lines = content.split('\n')
  const commands: ExtractedCommand[] = []

  // Pattern: "CommandName" -> handleCommandName(...)
  const commandPattern = /"([^"]+)"\s*->\s*(\w+)\s*\(/g

  let match
  while ((match = commandPattern.exec(content)) !== null) {
    const lineNumber = content.substring(0, match.index).split('\n').length
    commands.push({
      name: match[1],
      handlerName: match[2],
      lineNumber,
    })
  }

  return commands
}

/**
 * Extract handler function implementations.
 */
export function extractHandlerImplementations(
  commandManagerPath: string,
): Map<string, string> {
  const content = fs.readFileSync(commandManagerPath, 'utf-8')
  const handlers = new Map<string, string>()

  // Find all handler function declarations
  const handlerRegex = /private\s+fun\s+(handle\w+)\s*\([^)]+\)\s*\{/g

  let match
  while ((match = handlerRegex.exec(content)) !== null) {
    const handlerName = match[1]
    const startIndex = match.index + match[0].length

    // Extract the function body by counting braces
    let braceCount = 1
    let endIndex = startIndex

    for (let i = startIndex; i < content.length && braceCount > 0; i++) {
      if (content[i] === '{') braceCount++
      else if (content[i] === '}') braceCount--
      endIndex = i
    }

    const body = content.substring(startIndex, endIndex)
    handlers.set(handlerName, body)
  }

  return handlers
}

/**
 * Check if a handler completes the event (calls completeEvent).
 */
export function handlerCompletesEvent(handlerBody: string): boolean {
  return (
    handlerBody.includes('completeEvent(') ||
    handlerBody.includes('completeEventWithError(')
  )
}

/**
 * Check if a handler returns data (completeEvent with data parameter).
 */
export function handlerReturnsData(handlerBody: string): boolean {
  // Check for completeEvent with a second parameter (data)
  // Patterns:
  // - completeEvent(ci.requestID, """...""")
  // - completeEvent(ci.requestID, "...")
  // - completeEvent(ci.requestID, response)
  return (
    /completeEvent\s*\(\s*ci\.requestID\s*,\s*"""/.test(handlerBody) ||
    /completeEvent\s*\(\s*ci\.requestID\s*,\s*"/.test(handlerBody) ||
    /completeEvent\s*\(\s*ci\.requestID\s*,\s*\w+\s*\)/.test(handlerBody)
  )
}

/**
 * Verify handler implementations are complete.
 */
export interface HandlerValidation {
  name: string
  exists: boolean
  completesEvent: boolean
  returnsData: boolean
  issues: string[]
}

export function validateHandlers(
  commandManagerPath: string,
): HandlerValidation[] {
  const commands = extractCommandsFromCommandManager(commandManagerPath)
  const handlers = extractHandlerImplementations(commandManagerPath)
  const validations: HandlerValidation[] = []

  for (const command of commands) {
    const handlerBody = handlers.get(command.handlerName)
    const issues: string[] = []

    if (!handlerBody) {
      issues.push(`Handler ${command.handlerName} not found`)
    } else {
      if (!handlerCompletesEvent(handlerBody)) {
        issues.push('Handler does not call completeEvent')
      }
    }

    validations.push({
      name: command.name,
      exists: !!handlerBody,
      completesEvent: handlerBody ? handlerCompletesEvent(handlerBody) : false,
      returnsData: handlerBody ? handlerReturnsData(handlerBody) : false,
      issues,
    })
  }

  return validations
}
