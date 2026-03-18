/**
 * Simple Kotlin parser for validating Android XR code structure.
 * This parses Kotlin files to extract classes, functions, and their signatures.
 */

import * as fs from 'fs'
import * as path from 'path'

export interface KotlinClass {
  name: string
  superclass?: string
  interfaces: string[]
  methods: KotlinMethod[]
  properties: KotlinProperty[]
  isObject: boolean
  isSealed: boolean
  isData: boolean
}

export interface KotlinMethod {
  name: string
  parameters: string[]
  returnType?: string
  isOverride: boolean
}

export interface KotlinProperty {
  name: string
  type: string
  isMutable: boolean
  hasDefault: boolean
}

export interface KotlinFile {
  path: string
  packageName: string
  imports: string[]
  classes: KotlinClass[]
}

/**
 * Parse a Kotlin file and extract its structure.
 */
export function parseKotlinFile(filePath: string): KotlinFile {
  const content = fs.readFileSync(filePath, 'utf-8')
  const lines = content.split('\n')

  const result: KotlinFile = {
    path: filePath,
    packageName: '',
    imports: [],
    classes: [],
  }

  // Extract package
  const packageMatch = content.match(/^package\s+([\w.]+)/m)
  if (packageMatch) {
    result.packageName = packageMatch[1]
  }

  // Extract imports
  const importRegex = /^import\s+([\w.]+)/gm
  let importMatch
  while ((importMatch = importRegex.exec(content)) !== null) {
    result.imports.push(importMatch[1])
  }

  // Extract classes/objects
  const classRegex =
    /(?:(sealed|data)\s+)?(class|object)\s+(\w+)(?:\s*\([^)]*\))?(?:\s*:\s*([^\{]+))?/g
  let classMatch
  while ((classMatch = classRegex.exec(content)) !== null) {
    const modifier = classMatch[1]
    const type = classMatch[2]
    const className = classMatch[3]
    const inheritance = classMatch[4] || ''

    // Parse inheritance
    const inheritanceParts = inheritance.split(',').map(s => s.trim())
    let superclass: string | undefined
    const interfaces: string[] = []

    for (const part of inheritanceParts) {
      const cleanPart = part.replace(/\([^)]*\)/, '').trim()
      if (cleanPart) {
        if (!superclass) {
          superclass = cleanPart
        } else {
          interfaces.push(cleanPart)
        }
      }
    }

    const kotlinClass: KotlinClass = {
      name: className,
      superclass,
      interfaces,
      methods: [],
      properties: [],
      isObject: type === 'object',
      isSealed: modifier === 'sealed',
      isData: modifier === 'data',
    }

    // Find class body and extract methods/properties
    const classStart = classMatch.index + classMatch[0].length
    const classBody = extractClassBody(content, classStart)

    if (classBody) {
      kotlinClass.methods = extractMethods(classBody)
      kotlinClass.properties = extractProperties(classBody)
    }

    result.classes.push(kotlinClass)
  }

  return result
}

/**
 * Extract class body between braces.
 */
function extractClassBody(content: string, startIndex: number): string | null {
  let braceCount = 0
  let bodyStart = -1

  for (let i = startIndex; i < content.length; i++) {
    if (content[i] === '{') {
      if (bodyStart === -1) bodyStart = i + 1
      braceCount++
    } else if (content[i] === '}') {
      braceCount--
      if (braceCount === 0) {
        return content.substring(bodyStart, i)
      }
    }
  }

  return null
}

/**
 * Extract methods from class body.
 */
function extractMethods(classBody: string): KotlinMethod[] {
  const methods: KotlinMethod[] = []
  const methodRegex =
    /(override\s+)?fun\s+(\w+)\s*\(([^)]*)\)(?:\s*:\s*(\w+[?]?))?/g

  let match
  while ((match = methodRegex.exec(classBody)) !== null) {
    methods.push({
      name: match[2],
      parameters: match[3]
        ? match[3]
            .split(',')
            .map(p => p.trim())
            .filter(p => p)
        : [],
      returnType: match[4],
      isOverride: !!match[1],
    })
  }

  return methods
}

/**
 * Extract properties from class body.
 */
function extractProperties(classBody: string): KotlinProperty[] {
  const properties: KotlinProperty[] = []
  const propRegex = /(var|val)\s+(\w+)\s*:\s*([^=\n]+)(?:\s*=)?/g

  let match
  while ((match = propRegex.exec(classBody)) !== null) {
    properties.push({
      name: match[2],
      type: match[3].trim(),
      isMutable: match[1] === 'var',
      hasDefault: classBody.substring(match.index).includes('='),
    })
  }

  return properties
}

/**
 * Find all Kotlin files in a directory recursively.
 */
export function findKotlinFiles(dir: string): string[] {
  const files: string[] = []

  function walk(currentDir: string) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true })

    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name)
      if (entry.isDirectory()) {
        walk(fullPath)
      } else if (entry.name.endsWith('.kt')) {
        files.push(fullPath)
      }
    }
  }

  walk(dir)
  return files
}

/**
 * Parse all Kotlin files in a directory.
 */
export function parseKotlinDirectory(dir: string): KotlinFile[] {
  const files = findKotlinFiles(dir)
  return files.map(f => parseKotlinFile(f))
}
