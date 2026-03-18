/**
 * Global coordinator for bitmap captures on Android XR.
 *
 * Problem: Multiple PortalInstanceObjects can trigger captures for the same
 * element (e.g., all using spatialId "root_container"), resulting in duplicate
 * work and performance degradation.
 *
 * Solution: Track captures globally by element UUID, not by spatialId.
 * Only one capture per element is allowed. Captures are serialized (one at a time)
 * to prevent thread contention and ensure consistent performance.
 */

import { captureElementBitmap } from './androidBitmapCapture'

interface CaptureRequest {
  promise: Promise<string | null>
  timestamp: number
}

interface QueuedCapture {
  elementId: string
  dom: HTMLElement
  resolve: (value: string | null) => void
  reject: (error: Error) => void
}

class BitmapCaptureCoordinatorClass {
  // Track completed captures by element UUID
  private capturedElements = new Set<string>()

  // Track in-flight capture requests
  private pendingCaptures = new Map<string, CaptureRequest>()

  // Minimum time between recaptures of the same element (ms)
  private recaptureThrottleMs = 750

  // Capture queue for serialized processing (prevents thread contention)
  private captureQueue: QueuedCapture[] = []
  private isProcessingQueue = false

  /**
   * Request a bitmap capture for an element.
   * Returns null immediately if the element has already been captured.
   * Deduplicates concurrent requests for the same element.
   * Captures are serialized to prevent thread contention.
   *
   * @param elementId Unique element ID (UUID, not spatialId)
   * @param dom The DOM element to capture
   * @returns Promise resolving to bitmap data URL, or null if already captured
   */
  async requestCapture(
    elementId: string,
    dom: HTMLElement,
  ): Promise<string | null> {
    // Skip if already captured
    if (this.capturedElements.has(elementId)) {
      console.log(`[WebSpatial] Skipping capture for ${elementId} (already captured)`)
      return null
    }

    // Check for in-flight request
    const pending = this.pendingCaptures.get(elementId)
    if (pending) {
      console.log(`[WebSpatial] Joining existing capture for ${elementId}`)
      return pending.promise
    }

    // Mark as captured immediately to prevent race conditions
    this.capturedElements.add(elementId)

    // Create promise that will be resolved when capture completes
    const promise = new Promise<string | null>((resolve, reject) => {
      // Add to queue
      this.captureQueue.push({ elementId, dom, resolve, reject })
      console.log(`[WebSpatial] Queued capture for ${elementId} (queue size: ${this.captureQueue.length})`)
    })

    this.pendingCaptures.set(elementId, {
      promise,
      timestamp: Date.now(),
    })

    // Clean up after capture completes
    promise.finally(() => {
      this.pendingCaptures.delete(elementId)
    })

    // Start processing queue if not already running
    this.processQueue()

    return promise
  }

  /**
   * Process the capture queue one at a time.
   * This prevents thread contention and ensures consistent capture performance.
   */
  private async processQueue(): Promise<void> {
    if (this.isProcessingQueue) return
    this.isProcessingQueue = true

    while (this.captureQueue.length > 0) {
      const item = this.captureQueue.shift()!
      const { elementId, dom, resolve, reject } = item

      try {
        console.log(`[WebSpatial] Processing capture for ${elementId} (${this.captureQueue.length} remaining)`)
        const bitmap = await this.doCapture(elementId, dom)
        resolve(bitmap)
      } catch (error) {
        reject(error as Error)
      }

      // Small delay between captures to allow UI to breathe
      await new Promise(r => setTimeout(r, 50))
    }

    this.isProcessingQueue = false
  }

  /**
   * Force a recapture of an element (e.g., after content change).
   * Respects throttling to prevent excessive recaptures.
   */
  async requestRecapture(
    elementId: string,
    dom: HTMLElement,
  ): Promise<string | null> {
    // Check throttle
    const pending = this.pendingCaptures.get(elementId)
    if (pending && Date.now() - pending.timestamp < this.recaptureThrottleMs) {
      console.log(`[WebSpatial] Throttling recapture for ${elementId}`)
      return pending.promise
    }

    // Allow recapture
    this.capturedElements.delete(elementId)
    return this.requestCapture(elementId, dom)
  }

  /**
   * Perform the actual capture.
   */
  private async doCapture(
    elementId: string,
    dom: HTMLElement,
  ): Promise<string | null> {
    console.log(`[WebSpatial] Starting capture for ${elementId}`)

    // Diagnostic: Log element structure and CSS before capture
    this.logElementDiagnostics(elementId, dom)

    try {
      const bitmap = await captureElementBitmap(dom)

      if (bitmap) {
        const sizeKB = Math.round(bitmap.length / 1024)
        console.log(`[WebSpatial] Capture complete for ${elementId} (${sizeKB}KB)`)
      } else {
        console.log(`[WebSpatial] Capture returned null for ${elementId}`)
      }

      return bitmap
    } catch (error) {
      console.error(`[WebSpatial] Capture failed for ${elementId}:`, error)
      // Remove from captured set so it can be retried
      this.capturedElements.delete(elementId)
      return null
    }
  }

  /**
   * Clear the capture state for an element (e.g., when destroyed).
   */
  clearElement(elementId: string): void {
    this.capturedElements.delete(elementId)
    this.pendingCaptures.delete(elementId)
  }

  /**
   * Clear all capture state (e.g., on page navigation).
   */
  clearAll(): void {
    this.capturedElements.clear()
    this.pendingCaptures.clear()
  }

  /**
   * Check if an element has been captured.
   */
  hasCaptured(elementId: string): boolean {
    return this.capturedElements.has(elementId)
  }

  /**
   * Log detailed diagnostics about an element before capture.
   * This helps debug issues like content being shifted or hidden.
   */
  private logElementDiagnostics(elementId: string, dom: HTMLElement): void {
    const rect = dom.getBoundingClientRect()
    const style = window.getComputedStyle(dom)

    console.log(`[WebSpatial] === CAPTURE DIAGNOSTICS: ${elementId} ===`)
    console.log(`[WebSpatial] Element: ${dom.tagName}.${dom.className}`)
    console.log(`[WebSpatial] BoundingRect: (${rect.x.toFixed(0)}, ${rect.y.toFixed(0)}, ${rect.width.toFixed(0)}, ${rect.height.toFixed(0)})`)
    console.log(`[WebSpatial] Position: ${style.position}, Top: ${style.top}, Left: ${style.left}`)
    console.log(`[WebSpatial] Padding: ${style.paddingTop} / ${style.paddingRight} / ${style.paddingBottom} / ${style.paddingLeft}`)
    console.log(`[WebSpatial] Margin: ${style.marginTop} / ${style.marginRight} / ${style.marginBottom} / ${style.marginLeft}`)
    console.log(`[WebSpatial] Visibility: ${style.visibility}, Display: ${style.display}`)
    console.log(`[WebSpatial] Overflow: ${style.overflow}, OverflowY: ${style.overflowY}`)

    // Check parent styles that might affect layout
    const parent = dom.parentElement
    if (parent) {
      const parentStyle = window.getComputedStyle(parent)
      const parentRect = parent.getBoundingClientRect()
      console.log(`[WebSpatial] Parent: ${parent.tagName}.${parent.className}`)
      console.log(`[WebSpatial] Parent BoundingRect: (${parentRect.x.toFixed(0)}, ${parentRect.y.toFixed(0)}, ${parentRect.width.toFixed(0)}, ${parentRect.height.toFixed(0)})`)
      console.log(`[WebSpatial] Parent Position: ${parentStyle.position}, Top: ${parentStyle.top}`)
    }

    // Log first few children to understand content layout
    const children = dom.children
    console.log(`[WebSpatial] Children count: ${children.length}`)
    for (let i = 0; i < Math.min(5, children.length); i++) {
      const child = children[i] as HTMLElement
      const childRect = child.getBoundingClientRect()
      const childStyle = window.getComputedStyle(child)
      console.log(`[WebSpatial] Child ${i}: ${child.tagName}.${child.className?.substring(0, 30)}...`)
      console.log(`[WebSpatial]   Rect: (${childRect.x.toFixed(0)}, ${childRect.y.toFixed(0)}, ${childRect.width.toFixed(0)}, ${childRect.height.toFixed(0)})`)
      console.log(`[WebSpatial]   Position: ${childStyle.position}, Visibility: ${childStyle.visibility}`)
    }
    console.log(`[WebSpatial] === END DIAGNOSTICS ===`)
  }
}

// Global singleton instance
export const BitmapCaptureCoordinator = new BitmapCaptureCoordinatorClass()
