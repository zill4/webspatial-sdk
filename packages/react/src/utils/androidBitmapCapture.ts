/**
 * Bitmap capture utility for Android XR platform.
 *
 * On visionOS, each spatialized element gets its own WKWebView that renders HTML.
 * On Android XR, we use a single WebView + bitmap capture approach:
 * 1. Render HTML in the main WebView
 * 2. Capture enable-xr element content as bitmaps
 * 3. Transfer bitmaps to native via the bridge
 * 4. Render bitmaps on SpatialPanels at specified Z-depths
 *
 * Performance: Uses snapdom (30-100x faster than html2canvas) when available,
 * with html2canvas as fallback.
 */

// Capture library loader state
let snapdomModule: any = null
let snapdomChecked = false
let snapdomAvailable = false

let html2canvasModule: any = null
let html2canvasChecked = false
let html2canvasAvailable = false

/**
 * Dynamically loads snapdom library (preferred, 30-100x faster).
 * First checks for global, then tries dynamic import.
 * Retries a few times in case of race conditions.
 */
async function loadSnapdom(): Promise<any | null> {
  if (snapdomModule) return snapdomModule
  if (snapdomChecked && !snapdomAvailable) return null

  // Debug: log window.snapdom state
  console.log('[WebSpatial] Checking for snapdom...', {
    windowExists: typeof window !== 'undefined',
    snapdomOnWindow: typeof (window as any)?.snapdom,
    html2canvasOnWindow: typeof (window as any)?.html2canvas,
  })

  // Check for global snapdom (retry up to 3 times with short delays)
  for (let attempt = 0; attempt < 3; attempt++) {
    if (typeof window !== 'undefined' && (window as any).snapdom) {
      snapdomModule = (window as any).snapdom
      snapdomChecked = true
      snapdomAvailable = true
      console.log('[WebSpatial] Using globally provided snapdom (fast mode)')
      return snapdomModule
    }
    // Wait a bit before retrying (in case of module loading race)
    if (attempt < 2) {
      console.log(`[WebSpatial] snapdom not on window, retry ${attempt + 1}/3...`)
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }

  try {
    // Try dynamic import
    console.log('[WebSpatial] Trying dynamic import of @zumer/snapdom...')
    const moduleName = '@zumer/snapdom'
    const dynamicImport = new Function('moduleName', 'return import(moduleName)')
    const module = await dynamicImport(moduleName)
    snapdomModule = module.snapdom || module.default || module
    snapdomChecked = true
    snapdomAvailable = true
    console.log('[WebSpatial] Loaded snapdom via dynamic import (fast mode)')
    return snapdomModule
  } catch (error) {
    snapdomChecked = true
    snapdomAvailable = false
    console.log('[WebSpatial] snapdom not available:', (error as Error).message)
    console.log('[WebSpatial] Falling back to html2canvas')
    return null
  }
}

/**
 * Dynamically loads html2canvas library (fallback).
 */
async function loadHtml2Canvas(): Promise<any | null> {
  if (html2canvasModule) return html2canvasModule
  if (html2canvasChecked && !html2canvasAvailable) return null

  // Check for global html2canvas
  if (typeof window !== 'undefined' && (window as any).html2canvas) {
    html2canvasModule = (window as any).html2canvas
    html2canvasChecked = true
    html2canvasAvailable = true
    console.log('[WebSpatial] Using globally provided html2canvas (fallback mode)')
    return html2canvasModule
  }

  try {
    const moduleName = 'html2canvas'
    const dynamicImport = new Function('moduleName', 'return import(moduleName)')
    const module = await dynamicImport(moduleName)
    html2canvasModule = module.default || module
    html2canvasChecked = true
    html2canvasAvailable = true
    console.log('[WebSpatial] Loaded html2canvas via dynamic import (fallback mode)')
    return html2canvasModule
  } catch (error) {
    html2canvasChecked = true
    html2canvasAvailable = false
    console.warn(
      '[WebSpatial] Neither snapdom nor html2canvas available. ' +
        'Bitmap capture for Android XR is disabled. ' +
        'Install @zumer/snapdom (recommended) or html2canvas.',
    )
    return null
  }
}

/**
 * Check if we're running on Android XR platform.
 */
export function isAndroidPlatform(): boolean {
  if (typeof window === 'undefined') return false

  const ua = window.navigator.userAgent
  const hasWebSpatialBridge =
    typeof (window as any).webspatialBridge !== 'undefined'

  return hasWebSpatialBridge && (ua.includes('Android') || ua.includes('Linux'))
}

type AndroidRenderMode = 'bitmap-capture' | 'live-window'

export function getAndroidRenderMode(): AndroidRenderMode | null {
  if (typeof window === 'undefined') {
    return null
  }

  const bridgeMode = (window as any).webspatialBridge?.getRenderMode?.()
  if (bridgeMode === 'live-window' || bridgeMode === 'bitmap-capture') {
    return bridgeMode as AndroidRenderMode
  }

  const configuredMode = (window as any).__WebSpatialAndroidConfig?.renderMode
  if (configuredMode === 'live-window' || configuredMode === 'bitmap-capture') {
    return configuredMode as AndroidRenderMode
  }

  if (!isAndroidPlatform()) {
    return null
  }

  return (
    'bitmap-capture'
  ) as AndroidRenderMode
}

export function supportsAndroidLiveWindowProxy(): boolean {
  return getAndroidRenderMode() === 'live-window'
}

export function usesAndroidBitmapCapture(): boolean {
  return isAndroidPlatform() && !supportsAndroidLiveWindowProxy()
}

/**
 * Default background color for captures (dark theme).
 * Used when element has transparent/no background to prevent black panels in XR.
 */
const DEFAULT_CAPTURE_BACKGROUND = '#1a1a2e'

/**
 * Check if element has a transparent or no background.
 * Returns true if we need to inject a temporary background for capture.
 */
function hasTransparentBackground(element: HTMLElement): boolean {
  const style = window.getComputedStyle(element)
  const bg = style.backgroundColor
  const bgImage = style.backgroundImage

  // Check for transparent backgrounds
  if (
    bg === 'transparent' ||
    bg === 'rgba(0, 0, 0, 0)' ||
    bg === '' ||
    bg === 'initial'
  ) {
    // Only consider it transparent if there's no background image either
    if (bgImage === 'none' || bgImage === '' || bgImage === 'initial') {
      return true
    }
  }

  return false
}

/**
 * Temporarily inject a background color for capture.
 * Returns a cleanup function to restore original styles.
 *
 * On Android XR, we inject background on the element AND all child elements
 * that have transparent backgrounds. This is critical because:
 * 1. Spatial mode CSS sets `background: none` on many elements (product cards, etc.)
 * 2. These transparent children would render as black holes in the capture
 * 3. We need to fill ALL transparent areas with a solid background
 */
function injectCaptureBackground(
  element: HTMLElement,
  backgroundColor: string = DEFAULT_CAPTURE_BACKGROUND,
): () => void {
  const restoreFunctions: (() => void)[] = []

  // Inject background on the element itself
  const originalBg = element.style.backgroundColor
  const wasTransparent = hasTransparentBackground(element)
  element.style.backgroundColor = backgroundColor
  restoreFunctions.push(() => {
    element.style.backgroundColor = originalBg
  })

  // Find and inject background on ALL child elements with transparent backgrounds
  // This is crucial for spatial mode where many elements have `background: none`
  const allDescendants = element.querySelectorAll('*')
  let injectedCount = 0

  allDescendants.forEach(el => {
    const htmlEl = el as HTMLElement
    if (hasTransparentBackground(htmlEl)) {
      const childOriginalBg = htmlEl.style.backgroundColor
      htmlEl.style.backgroundColor = backgroundColor
      injectedCount++
      restoreFunctions.push(() => {
        htmlEl.style.backgroundColor = childOriginalBg
      })
    }
  })

  console.log(
    `[WebSpatial] Injected background ${backgroundColor} for capture (parent transparent: ${wasTransparent}, ${injectedCount} children)`,
  )

  return () => {
    restoreFunctions.forEach(restore => restore())
  }
}

// Track if initial render delay has been applied this session
let initialRenderDelayApplied = false

/**
 * Wait for fonts and images to be ready.
 * Uses smart detection instead of fixed delay.
 * On first capture, waits longer to allow React to hydrate and data to load.
 */
async function waitForContent(
  element: HTMLElement,
  imageTimeoutMs: number = 2000,
): Promise<void> {
  // On first capture, wait longer to allow React hydration and data loading
  // This is critical for pages that load data via useEffect
  if (!initialRenderDelayApplied) {
    initialRenderDelayApplied = true
    console.log('[WebSpatial] Applying initial render delay (1500ms) for first capture')
    await new Promise(resolve => setTimeout(resolve, 1500))
  }

  // Wait for fonts (up to 500ms)
  try {
    await Promise.race([
      document.fonts.ready,
      new Promise(resolve => setTimeout(resolve, 500)),
    ])
  } catch {
    // fonts.ready not supported, continue
  }

  // Wait for images (up to imageTimeoutMs)
  // This is crucial for external images (like Unsplash) that may take longer to load
  const images = element.querySelectorAll('img')
  console.log(`[WebSpatial] Found ${images.length} images in element`)

  if (images.length > 0) {
    // Log status of each image
    Array.from(images).forEach((img, i) => {
      const src = img.src?.substring(0, 80) || 'no-src'
      console.log(
        `[WebSpatial] Image ${i}: complete=${img.complete}, naturalWidth=${img.naturalWidth}, src=${src}...`,
      )
    })

    const incompleteImages = Array.from(images).filter(img => !img.complete)
    if (incompleteImages.length > 0) {
      console.log(`[WebSpatial] Waiting for ${incompleteImages.length} images to load (timeout: ${imageTimeoutMs}ms)`)

      const imagePromises = incompleteImages.map(img => {
        return new Promise<void>(resolve => {
          const handler = () => resolve()
          img.addEventListener('load', handler, { once: true })
          img.addEventListener('error', handler, { once: true })
        })
      })

      await Promise.race([
        Promise.all(imagePromises),
        new Promise(resolve => setTimeout(resolve, imageTimeoutMs)),
      ])

      // Log how many images loaded
      const stillIncomplete = incompleteImages.filter(img => !img.complete).length
      console.log(`[WebSpatial] Image wait complete. ${stillIncomplete} images still loading.`)
    } else {
      console.log(`[WebSpatial] All ${images.length} images already complete`)
    }
  }

  // Additional wait for dynamically loaded content (like product cards)
  // Check if element has minimal content and wait more if needed
  const textContent = element.innerText?.trim() || ''
  if (textContent.length < 100) {
    console.log(`[WebSpatial] Element has minimal content (${textContent.length} chars), waiting 500ms more`)
    await new Promise(resolve => setTimeout(resolve, 500))
  }
}

/**
 * Maximum bitmap dimension in pixels.
 * Android XR SpatialPanel max is 2560x1800dp, we cap at 2048px for safety and performance.
 */
const MAX_BITMAP_DIMENSION = 2048

/**
 * Resize canvas if it exceeds max dimensions.
 * Maintains aspect ratio while capping both width and height.
 */
function resizeCanvasIfNeeded(canvas: HTMLCanvasElement): HTMLCanvasElement {
  const { width, height } = canvas
  if (width <= MAX_BITMAP_DIMENSION && height <= MAX_BITMAP_DIMENSION) {
    return canvas
  }

  // Calculate scale factor to fit within max dimensions
  const scaleFactor = Math.min(
    MAX_BITMAP_DIMENSION / width,
    MAX_BITMAP_DIMENSION / height,
  )

  const newWidth = Math.round(width * scaleFactor)
  const newHeight = Math.round(height * scaleFactor)

  console.log(
    `[WebSpatial] Resizing bitmap from ${width}x${height} to ${newWidth}x${newHeight}`,
  )

  // Create resized canvas
  const resizedCanvas = document.createElement('canvas')
  resizedCanvas.width = newWidth
  resizedCanvas.height = newHeight

  const ctx = resizedCanvas.getContext('2d')
  if (ctx) {
    // Use high quality scaling
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(canvas, 0, 0, newWidth, newHeight)
  }

  return resizedCanvas
}

/**
 * Capture using snapdom (30-100x faster than html2canvas).
 * Note: snapdom has issues with cross-origin images and certain CSS layouts,
 * so html2canvas is preferred as the primary capture method on Android.
 */
async function captureWithSnapdom(
  snapdom: any,
  element: HTMLElement,
  scale: number,
): Promise<string | null> {
  try {
    const cappedScale = Math.min(scale, 1.5)
    const rect = element.getBoundingClientRect()
    console.log(`[WebSpatial] snapdom capturing: rect=(${rect.x.toFixed(0)},${rect.y.toFixed(0)},${rect.width.toFixed(0)},${rect.height.toFixed(0)}), scale=${cappedScale}`)

    const result = await snapdom(element, {
      scale: cappedScale,
      embedFonts: false,
    })

    let canvas = await result.toCanvas()
    canvas = resizeCanvasIfNeeded(canvas)

    const dataUrl = canvas.toDataURL('image/webp', 0.85)
    return dataUrl
  } catch (error) {
    console.error('[WebSpatial] snapdom capture failed:', error)
    return null
  }
}

/**
 * Find elements with position:relative offsets that affect the captured element.
 * Returns a list of elements to reset and their original values.
 */
function findOffsetElements(element: HTMLElement): Array<{
  element: HTMLElement
  originalTop: string
  originalLeft: string
  topValue: number
  leftValue: number
}> {
  const offsetElements: Array<{
    element: HTMLElement
    originalTop: string
    originalLeft: string
    topValue: number
    leftValue: number
  }> = []

  // Check the element itself
  const style = window.getComputedStyle(element)
  if (style.position === 'relative') {
    const top = parseFloat(style.top) || 0
    const left = parseFloat(style.left) || 0
    if (top !== 0 || left !== 0) {
      offsetElements.push({
        element,
        originalTop: element.style.top,
        originalLeft: element.style.left,
        topValue: top,
        leftValue: left,
      })
    }
  }

  // Check parent elements that might have position:relative offsets
  let parent = element.parentElement
  let depth = 0
  const maxDepth = 5

  while (parent && depth < maxDepth) {
    const parentStyle = window.getComputedStyle(parent)
    if (parentStyle.position === 'relative') {
      const parentTop = parseFloat(parentStyle.top) || 0
      const parentLeft = parseFloat(parentStyle.left) || 0
      if (parentTop !== 0 || parentLeft !== 0) {
        console.log(
          `[WebSpatial] Found offset element: ${parent.tagName}.${parent.className?.split(' ')[0] || ''} ` +
            `top=${parentTop}px, left=${parentLeft}px`,
        )
        offsetElements.push({
          element: parent,
          originalTop: parent.style.top,
          originalLeft: parent.style.left,
          topValue: parentTop,
          leftValue: parentLeft,
        })
      }
    }
    parent = parent.parentElement
    depth++
  }

  return offsetElements
}

/**
 * Get total content offset from all offset elements.
 */
function getContentOffset(element: HTMLElement): { top: number; left: number } {
  const offsetElements = findOffsetElements(element)
  return {
    top: offsetElements.reduce((sum, el) => sum + el.topValue, 0),
    left: offsetElements.reduce((sum, el) => sum + el.leftValue, 0),
  }
}

/**
 * Temporarily make element visible for capture.
 * Spatialized elements have visibility: hidden by default.
 * Returns a cleanup function to restore original visibility.
 */
function makeElementVisible(element: HTMLElement): () => void {
  const restoreFunctions: (() => void)[] = []

  // Make the element itself visible
  const originalVisibility = element.style.visibility
  const computedVisibility = window.getComputedStyle(element).visibility
  if (computedVisibility === 'hidden') {
    element.style.visibility = 'visible'
    console.log(`[WebSpatial] Made element visible for capture (was: ${computedVisibility})`)
    restoreFunctions.push(() => {
      element.style.visibility = originalVisibility
    })
  }

  // Also make all child elements visible (they may be hidden too)
  const allDescendants = element.querySelectorAll('*')
  allDescendants.forEach(el => {
    const htmlEl = el as HTMLElement
    const childComputedVisibility = window.getComputedStyle(htmlEl).visibility
    if (childComputedVisibility === 'hidden') {
      const childOriginalVisibility = htmlEl.style.visibility
      htmlEl.style.visibility = 'visible'
      restoreFunctions.push(() => {
        htmlEl.style.visibility = childOriginalVisibility
      })
    }
  })

  if (restoreFunctions.length > 1) {
    console.log(`[WebSpatial] Made ${restoreFunctions.length} elements visible for capture`)
  }

  return () => {
    restoreFunctions.forEach(restore => restore())
  }
}

/**
 * Capture using html2canvas (fallback).
 * Optimized settings for faster capture on mobile.
 */
async function captureWithHtml2Canvas(
  html2canvas: any,
  element: HTMLElement,
  scale: number,
  backgroundColor: string | null,
): Promise<string | null> {
  try {
    const rect = element.getBoundingClientRect()
    const contentOffset = getContentOffset(element)

    console.log(`[WebSpatial] html2canvas capturing: rect=(${rect.x.toFixed(0)},${rect.y.toFixed(0)},${rect.width.toFixed(0)},${rect.height.toFixed(0)}), contentOffset=(${contentOffset.top},${contentOffset.left})`)

    // Calculate window scroll position
    const scrollX = window.scrollX || window.pageXOffset || 0
    const scrollY = window.scrollY || window.pageYOffset || 0

    // The element's absolute position in the document
    const absoluteX = rect.x + scrollX
    const absoluteY = rect.y + scrollY

    // Find all elements with position:relative offsets that affect the capture
    const offsetElements = findOffsetElements(element)

    // Temporarily reset ALL offset elements to capture content at correct position
    // This prevents the "black at top" issue caused by CSS like `.main-window { top: 70px }`
    if (offsetElements.length > 0) {
      console.log(
        `[WebSpatial] Temporarily resetting ${offsetElements.length} offset elements for capture`,
      )
      offsetElements.forEach(({ element: el, topValue, leftValue }) => {
        el.style.top = '0px'
        el.style.left = '0px'
        console.log(
          `[WebSpatial] Reset: ${el.tagName}.${el.className?.split(' ')[0] || ''} ` +
            `from top=${topValue}px, left=${leftValue}px to 0`,
        )
      })

      // Force browser reflow to ensure layout is updated before capture
      // Without this, html2canvas may clone the DOM before new positions are applied
      void element.offsetHeight
      await new Promise(resolve => setTimeout(resolve, 50))
      console.log('[WebSpatial] Reflow complete after offset reset')
    }

    // Create restore function that resets all elements
    const restorePositions = () => {
      offsetElements.forEach(({ element: el, originalTop, originalLeft }) => {
        el.style.top = originalTop
        el.style.left = originalLeft
      })
    }

    // Ensure element is scrolled to top if it has internal scroll
    if (element.scrollTop !== 0) {
      console.log(`[WebSpatial] Resetting element scroll from ${element.scrollTop} to 0`)
      element.scrollTop = 0
    }

    // CRITICAL: Make element visible for capture
    // Spatialized elements have visibility: hidden by default in StandardSpatializedContainer
    // We need to temporarily make them visible so html2canvas can capture actual content
    const restoreVisibility = makeElementVisible(element)

    // Ensure window is scrolled to show the element
    const viewportTop = scrollY
    const viewportBottom = scrollY + window.innerHeight
    const elementTop = rect.y + scrollY
    const elementBottom = elementTop + rect.height

    console.log(
      `[WebSpatial] Capture context: viewport=(${viewportTop}-${viewportBottom}), ` +
        `element=(${elementTop}-${elementBottom}), innerHeight=${window.innerHeight}`,
    )

    let canvas: HTMLCanvasElement
    try {
      // Calculate the capture position accounting for parent offsets
      // We reset the parent offsets above, so the element should now be at its natural position
      // Use the CURRENT rect after offset reset
      const currentRect = element.getBoundingClientRect()
      const captureX = currentRect.x + scrollX
      const captureY = currentRect.y + scrollY

      console.log(
        `[WebSpatial] Capture bounds: original=(${absoluteX},${absoluteY}), ` +
          `afterReset=(${captureX},${captureY}), size=(${currentRect.width}x${currentRect.height})`,
      )

      // Capture the element by cropping to its document position
      // html2canvas renders the full document, then crops to x/y/width/height
      canvas = await html2canvas(element, {
        backgroundColor,
        logging: true, // Enable logging to debug
        scale: Math.min(scale, 1.5),
        useCORS: true,
        allowTaint: true,
        imageTimeout: 5000,
        removeContainer: true,
        foreignObjectRendering: false,
        // Don't set scroll offset - let html2canvas use default (current scroll)
        // scrollX and scrollY would affect rendering position but we want document coordinates
        // Crop to element's document position
        // x/y define the top-left corner of the crop region in document coordinates
        x: captureX,
        y: captureY,
        width: currentRect.width,
        height: currentRect.height,
        // Use onclone to MOVE the element to (0,0) in the cloned document
        // This is the key fix for elements far down the page (like footer at Y=1142)
        // By moving to absolute position (0,0), html2canvas will render at the top-left
        onclone: (clonedDoc: Document, clonedElement: HTMLElement) => {
          // Log position for debugging
          const originalRect = clonedElement.getBoundingClientRect()
          console.log(`[WebSpatial] CLONE: Element position in clone (${originalRect.x.toFixed(0)},${originalRect.y.toFixed(0)})`)

          // CRITICAL: Make all elements in the cloned document VISIBLE
          // Spatialized elements have visibility: hidden by default
          // In the clone, we MUST make them visible so html2canvas renders content
          clonedElement.style.visibility = 'visible'
          const hiddenElements = clonedElement.querySelectorAll('*')
          let visibilityFixCount = 0
          hiddenElements.forEach(el => {
            const htmlEl = el as HTMLElement
            const style = clonedDoc.defaultView?.getComputedStyle(htmlEl)
            if (style && style.visibility === 'hidden') {
              htmlEl.style.visibility = 'visible'
              visibilityFixCount++
            }
          })
          if (visibilityFixCount > 0) {
            console.log(`[WebSpatial] CLONE: Made ${visibilityFixCount + 1} elements visible`)
          }

          // Find and reset all offset parents in the CLONED document
          let parent = clonedElement.parentElement
          let depth = 0
          const maxDepth = 10

          while (parent && depth < maxDepth) {
            const parentStyle = clonedDoc.defaultView?.getComputedStyle(parent)
            if (parentStyle && parentStyle.position === 'relative') {
              const parentTop = parseFloat(parentStyle.top) || 0
              const parentLeft = parseFloat(parentStyle.left) || 0
              if (parentTop !== 0 || parentLeft !== 0) {
                console.log(
                  `[WebSpatial] CLONE: Reset ${parent.tagName}.${parent.className?.split(' ')[0] || ''} ` +
                  `from top=${parentTop}px, left=${parentLeft}px to 0`,
                )
                parent.style.top = '0px'
                parent.style.left = '0px'
              }
            }
            parent = parent.parentElement
            depth++
          }

          // Force reflow in cloned document
          void clonedElement.offsetHeight

          const clonedRect = clonedElement.getBoundingClientRect()
          const clonedStyle = clonedDoc.defaultView?.getComputedStyle(clonedElement)
          console.log(
            `[WebSpatial] Cloned element (after visibility fix): rect=(${clonedRect.x.toFixed(0)},${clonedRect.y.toFixed(0)},${clonedRect.width.toFixed(0)},${clonedRect.height.toFixed(0)}), ` +
            `visibility=${clonedStyle?.visibility}`,
          )

          // Log first few children to verify content structure
          const numChildren = Math.min(5, clonedElement.children.length)
          for (let i = 0; i < numChildren; i++) {
            const child = clonedElement.children[i] as HTMLElement
            const childRect = child.getBoundingClientRect()
            const childStyle = clonedDoc.defaultView?.getComputedStyle(child)
            console.log(
              `[WebSpatial] Child ${i}: ${child.tagName}.${child.className?.split(' ')[0] || ''}, ` +
              `rect=(${childRect.x.toFixed(0)},${childRect.y.toFixed(0)},${childRect.width.toFixed(0)},${childRect.height.toFixed(0)}), ` +
              `vis=${childStyle?.visibility}, display=${childStyle?.display}`,
            )
          }

          // Find and log the product grid specifically
          const productGrid = clonedElement.querySelector('.auto-fill-grid') as HTMLElement
          if (productGrid) {
            const gridRect = productGrid.getBoundingClientRect()
            const gridStyle = clonedDoc.defaultView?.getComputedStyle(productGrid)
            console.log(
              `[WebSpatial] Product grid: rect=(${gridRect.x.toFixed(0)},${gridRect.y.toFixed(0)},${gridRect.width.toFixed(0)},${gridRect.height.toFixed(0)}), ` +
              `columns=${gridStyle?.gridTemplateColumns?.substring(0, 100)}`,
            )
            // Log first product card
            const firstCard = productGrid.children[0] as HTMLElement
            if (firstCard) {
              const cardRect = firstCard.getBoundingClientRect()
              console.log(`[WebSpatial] First product card: rect=(${cardRect.x.toFixed(0)},${cardRect.y.toFixed(0)},${cardRect.width.toFixed(0)},${cardRect.height.toFixed(0)})`)
            }
          }
        },
      })
    } finally {
      // Restore visibility and position offsets after capture
      restoreVisibility()
      restorePositions()
    }

    // Debug: Sample pixels to verify content and find where it actually is
    const ctx = canvas.getContext('2d')
    if (ctx) {
      // Sample a grid of positions across the entire canvas
      const xPositions = [50, Math.floor(canvas.width / 4), Math.floor(canvas.width / 2), Math.floor(canvas.width * 3 / 4)]
      const yPositions = [50, 100, 200, 400, 600, 800, 1000, 1200, 1400]
      const samples: { name: string; x: number; y: number }[] = []
      yPositions.forEach(y => {
        if (y < canvas.height) {
          xPositions.forEach(x => {
            if (x < canvas.width) {
              samples.push({ name: `(${x},${y})`, x, y })
            }
          })
        }
      })

      console.log(`[WebSpatial] Canvas size: ${canvas.width}x${canvas.height}, scale=${scale}`)
      let bgCount = 0
      let contentCount = 0
      let contentPixels: string[] = []
      samples.forEach(s => {
        const pixel = ctx.getImageData(s.x, s.y, 1, 1).data
        const isBackground = pixel[0] === 26 && pixel[1] === 26 && pixel[2] === 46 // #1a1a2e
        if (isBackground) {
          bgCount++
        } else {
          contentCount++
          contentPixels.push(`${s.name}=rgba(${pixel[0]},${pixel[1]},${pixel[2]})`)
        }
      })
      console.log(`[WebSpatial] Grid sample: ${bgCount} BG, ${contentCount} CONTENT`)
      if (contentPixels.length > 0) {
        console.log(`[WebSpatial] Content pixels: ${contentPixels.slice(0, 10).join(', ')}`)
      }

      // If no content found in grid, do a more thorough scan
      if (contentCount === 0) {
        console.log(`[WebSpatial] No content in grid sample - scanning center column...`)
        for (let y = 0; y < canvas.height; y += 30) {
          const pixel = ctx.getImageData(Math.floor(canvas.width / 2), y, 1, 1).data
          const isBackground = pixel[0] === 26 && pixel[1] === 26 && pixel[2] === 46
          if (!isBackground) {
            console.log(`[WebSpatial] First content at Y=${y}: rgba(${pixel[0]},${pixel[1]},${pixel[2]})`)
            break
          }
        }
      }
    }

    // Resize if needed to prevent oversized bitmaps
    canvas = resizeCanvasIfNeeded(canvas)

    // Use WebP for smaller size
    const dataUrl = canvas.toDataURL('image/webp', 0.85)
    return dataUrl
  } catch (error) {
    console.error('[WebSpatial] html2canvas capture failed:', error)
    return null
  }
}

/**
 * Captures an HTML element as a base64-encoded bitmap.
 * Uses snapdom (fast) when available, html2canvas as fallback.
 *
 * @param element The HTML element to capture
 * @param options Capture options
 * @returns Base64-encoded data URL, or null if capture failed
 */
export async function captureElementBitmap(
  element: HTMLElement,
  options?: {
    scale?: number
    backgroundColor?: string | null
    waitForImages?: boolean
  },
): Promise<string | null> {
  if (!usesAndroidBitmapCapture()) {
    return null
  }

  const scale = options?.scale ?? (window.devicePixelRatio || 1)
  const startTime = performance.now()

  // Wait for content to be ready (fonts, images)
  if (options?.waitForImages !== false) {
    await waitForContent(element, 500)
  }

  // Inject background if element has transparent/no background
  // This prevents black panels in XR when rendered on transparent SpatialPanels
  const restoreBackground = injectCaptureBackground(
    element,
    options?.backgroundColor || DEFAULT_CAPTURE_BACKGROUND,
  )

  let result: string | null = null

  try {
    // Try html2canvas FIRST for better cross-origin image handling
    // snapdom has issues rendering cross-origin images on some platforms
    const html2canvas = await loadHtml2Canvas()
    if (html2canvas) {
      console.log('[WebSpatial] Using html2canvas (primary)')
      result = await captureWithHtml2Canvas(
        html2canvas,
        element,
        scale,
        options?.backgroundColor ?? DEFAULT_CAPTURE_BACKGROUND,
      )
      if (result) {
        const elapsed = Math.round(performance.now() - startTime)
        console.log(`[WebSpatial] Capture complete (html2canvas, ${elapsed}ms)`)
        return result
      }
    }

    // Fall back to snapdom
    const snapdom = await loadSnapdom()
    if (snapdom) {
      console.log('[WebSpatial] Falling back to snapdom')
      result = await captureWithSnapdom(snapdom, element, scale)
      if (result) {
        const elapsed = Math.round(performance.now() - startTime)
        console.log(`[WebSpatial] Capture complete (snapdom, ${elapsed}ms)`)
        return result
      }
    }

    console.error('[WebSpatial] No capture library available')
    return null
  } finally {
    // Always restore original background
    restoreBackground()
  }
}

/**
 * Throttled bitmap capture for performance.
 */
export function createThrottledBitmapCapture(delayMs: number = 500) {
  let pending: ReturnType<typeof setTimeout> | null = null
  let lastCapture: string | null = null

  return async (
    element: HTMLElement,
    callback: (bitmap: string | null) => void,
  ) => {
    if (pending) {
      clearTimeout(pending)
    }

    pending = setTimeout(async () => {
      pending = null
      const bitmap = await captureElementBitmap(element)

      if (bitmap !== lastCapture) {
        lastCapture = bitmap
        callback(bitmap)
      }
    }, delayMs)
  }
}

/**
 * Sets up a MutationObserver to detect content changes.
 */
export function observeContentChanges(
  element: HTMLElement,
  onContentChange: () => void,
): () => void {
  const observer = new MutationObserver(_mutations => {
    onContentChange()
  })

  observer.observe(element, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['class', 'style'],
  })

  let resizeObserver: ResizeObserver | null = null
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(_entries => {
      onContentChange()
    })
    resizeObserver.observe(element)
  }

  return () => {
    observer.disconnect()
    resizeObserver?.disconnect()
  }
}
