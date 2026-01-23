#!/usr/bin/env node

/**
 * Build script for deploying Vite app to Android XR
 *
 * This script copies the built Vite output to the Android app's assets folder,
 * making it available to be served via WebViewAssetLoader.
 *
 * Usage:
 *   npm run build          # Build the Vite app
 *   npm run build:android  # Copy to Android assets
 *   npm run deploy:android # Build + copy in one step
 */

import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// Paths
const PROJECT_ROOT = path.resolve(__dirname, '..')
const DIST_DIR = path.join(PROJECT_ROOT, 'dist')
const ANDROID_ASSETS_DIR = path.resolve(
  PROJECT_ROOT,
  '../../packages/androidXR/app/src/main/assets/web',
)

// Colors for console output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
}

function log(message, color = colors.reset) {
  console.log(`${color}${message}${colors.reset}`)
}

function logStep(step, message) {
  log(`\n${colors.bright}[${step}]${colors.reset} ${message}`)
}

function logSuccess(message) {
  log(`  ${colors.green}✓${colors.reset} ${message}`)
}

function logError(message) {
  log(`  ${colors.red}✗${colors.reset} ${message}`)
}

function logInfo(message) {
  log(`  ${colors.cyan}→${colors.reset} ${message}`)
}

/**
 * Recursively copy a directory
 */
function copyDirSync(src, dest) {
  // Create destination directory
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true })
  }

  const entries = fs.readdirSync(src, { withFileTypes: true })
  let fileCount = 0

  for (const entry of entries) {
    const srcPath = path.join(src, entry.name)
    const destPath = path.join(dest, entry.name)

    if (entry.isDirectory()) {
      fileCount += copyDirSync(srcPath, destPath)
    } else {
      fs.copyFileSync(srcPath, destPath)
      fileCount++
    }
  }

  return fileCount
}

/**
 * Remove directory recursively
 */
function removeDirSync(dir) {
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true, force: true })
  }
}

/**
 * Get directory size
 */
function getDirSize(dir) {
  let size = 0
  const entries = fs.readdirSync(dir, { withFileTypes: true })

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      size += getDirSize(fullPath)
    } else {
      size += fs.statSync(fullPath).size
    }
  }

  return size
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

async function main() {
  log('\n' + '='.repeat(60))
  log(
    `${colors.bright}${colors.blue}  WebSpatial Vite Demo - Android XR Deployment${colors.reset}`,
  )
  log('='.repeat(60))

  // Step 1: Verify build exists
  logStep('1/4', 'Checking Vite build output...')

  if (!fs.existsSync(DIST_DIR)) {
    logError(`Build directory not found: ${DIST_DIR}`)
    logInfo('Run "npm run build" first to create the production build')
    process.exit(1)
  }

  const indexPath = path.join(DIST_DIR, 'index.html')
  if (!fs.existsSync(indexPath)) {
    logError('index.html not found in build output')
    process.exit(1)
  }

  const buildSize = getDirSize(DIST_DIR)
  logSuccess(`Build found (${formatSize(buildSize)})`)

  // Step 2: Clear existing assets
  logStep('2/4', 'Clearing existing Android assets...')

  if (fs.existsSync(ANDROID_ASSETS_DIR)) {
    removeDirSync(ANDROID_ASSETS_DIR)
    logSuccess('Cleared existing assets')
  } else {
    logInfo('No existing assets to clear')
  }

  // Step 3: Copy build to Android assets
  logStep('3/4', 'Copying build to Android assets...')

  const fileCount = copyDirSync(DIST_DIR, ANDROID_ASSETS_DIR)
  logSuccess(`Copied ${fileCount} files to Android assets`)
  logInfo(`Destination: ${ANDROID_ASSETS_DIR}`)

  // Step 4: Verify deployment
  logStep('4/4', 'Verifying deployment...')

  const deployedIndex = path.join(ANDROID_ASSETS_DIR, 'index.html')
  if (fs.existsSync(deployedIndex)) {
    logSuccess('index.html verified in Android assets')
  } else {
    logError('Deployment verification failed')
    process.exit(1)
  }

  // Summary
  log('\n' + '='.repeat(60))
  log(`${colors.bright}${colors.green}  Deployment Complete!${colors.reset}`)
  log('='.repeat(60))

  log(`
${colors.cyan}Next Steps:${colors.reset}

  1. Open Android Studio:
     ${colors.yellow}cd packages/androidXR && studio .${colors.reset}
     (or use: npm run androidStudio from repo root)

  2. Clean and rebuild the Android project:
     ${colors.yellow}Build > Clean Project${colors.reset}
     ${colors.yellow}Build > Rebuild Project${colors.reset}

  3. Run on Android XR Simulator:
     ${colors.yellow}Run > Run 'app'${colors.reset}

${colors.cyan}The app will load from:${colors.reset}
  https://appassets.androidplatform.net/assets/web/index.html

${colors.cyan}WebViewAssetLoader serves assets via HTTPS, which:${colors.reset}
  - Enables secure context features
  - Supports ES modules properly
  - Works with modern JavaScript frameworks
`)
}

main().catch(error => {
  logError(`Deployment failed: ${error.message}`)
  process.exit(1)
})
