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
 * Note: html2canvas is an optional peer dependency. If not installed,
 * bitmap capture will be disabled and spatial elements will render
 * without content (placeholder only).
 */

// html2canvas loader state
let html2canvasModule: any = null
let html2canvasChecked = false
let html2canvasAvailable = false

/**
 * Dynamically loads html2canvas library using a runtime-constructed import.
 * This prevents bundlers from trying to resolve it at build time.
 */
async function loadHtml2Canvas(): Promise<any | null> {
  // Return cached module if already loaded
  if (html2canvasModule) {
    return html2canvasModule
  }

  // Return null if we've already checked and it's not available
  if (html2canvasChecked && !html2canvasAvailable) {
    return null
  }

  try {
    // Use Function constructor to create a truly dynamic import that bundlers can't analyze
    // This is equivalent to: import('html2canvas')
    const moduleName = 'html2canvas'
    const dynamicImport = new Function(
      'moduleName',
      'return import(moduleName)',
    )
    const module = await dynamicImport(moduleName)
    html2canvasModule = module.default || module
    html2canvasChecked = true
    html2canvasAvailable = true
    return html2canvasModule
  } catch (error) {
    html2canvasChecked = true
    html2canvasAvailable = false
    console.warn(
      '[WebSpatial] html2canvas is not installed. Bitmap capture for Android XR is disabled. ' +
        'To enable spatial element content rendering on Android, install html2canvas: npm install html2canvas',
    )
    return null
  }
}

/**
 * Check if we're running on Android XR platform.
 */
export function isAndroidPlatform(): boolean {
  if (typeof window === 'undefined') return false

  // Check for Android user agent and webspatialBridge
  const ua = window.navigator.userAgent
  const hasWebSpatialBridge =
    typeof (window as any).webspatialBridge !== 'undefined'

  return hasWebSpatialBridge && (ua.includes('Android') || ua.includes('Linux'))
}

/**
 * Captures an HTML element as a base64-encoded PNG bitmap.
 *
 * @param element The HTML element to capture
 * @param options Capture options
 * @returns Base64-encoded PNG data URL, or null if capture failed or not on Android
 */
export async function captureElementBitmap(
  element: HTMLElement,
  options?: {
    scale?: number
    backgroundColor?: string | null
  },
): Promise<string | null> {
  if (!isAndroidPlatform()) {
    return null // Only capture on Android
  }

  try {
    const html2canvas = await loadHtml2Canvas()

    if (!html2canvas) {
      // html2canvas not available, skip capture
      return null
    }

    const canvas = await html2canvas(element, {
      backgroundColor: options?.backgroundColor ?? null, // Transparent by default
      logging: false,
      scale: options?.scale ?? (window.devicePixelRatio || 1),
      useCORS: true,
      allowTaint: true,
    })

    // Convert to base64 PNG
    const dataUrl = canvas.toDataURL('image/png')

    // Return with the data URL prefix (native side expects this format)
    return dataUrl
  } catch (error) {
    console.error('[WebSpatial] Failed to capture element bitmap:', error)
    return null
  }
}

/**
 * Throttled bitmap capture for performance.
 * Prevents capturing too frequently when content is updating.
 */
export function createThrottledBitmapCapture(delayMs: number = 100) {
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

      // Only call callback if bitmap changed
      if (bitmap !== lastCapture) {
        lastCapture = bitmap
        callback(bitmap)
      }
    }, delayMs)
  }
}

/**
 * Sets up a MutationObserver to detect content changes and trigger recapture.
 *
 * @param element The element to observe
 * @param onContentChange Callback when content changes
 * @returns Cleanup function
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

  // Also observe resize
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
