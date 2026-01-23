import { PlatformAbility, CommandResult } from '../interface'
import {
  CommandResultFailure,
  CommandResultSuccess,
} from '../CommandResultUtils'
import { SpatialWebEvent } from '../../SpatialWebEvent'

interface JSBResponse {
  success: boolean
  data: any
}
type JSBError = {
  code: string
  message: string
}

let requestId = 0

const MAX_ID = 100000

function nextRequestId() {
  requestId = (requestId + 1) % MAX_ID
  return `rId_${requestId}`
}

// Generate UUID for element IDs
function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

/**
 * Creates a fake WindowProxy that satisfies the SDK interface for Android.
 * On visionOS, each spatialized element gets its own WKWebView.
 * On Android, we use a single WebView with bitmap capture, so we need a
 * comprehensive fake WindowProxy that:
 * 1. Has document.documentElement.style (for CSS property access)
 * 2. Has document.body.style (for style manipulation)
 * 3. Has document.head with appendChild (for viewport meta)
 * 4. Has document.onclick setter (for anchor tag hijacking)
 * 5. Supports all style properties the SDK accesses in setOpenWindowStyle()
 */
function createFakeWindowProxy(elementId: string): WindowProxy {
  // Create a style proxy that accepts any property without error
  const createStyleProxy = (): CSSStyleDeclaration => {
    const styleObj: Record<string, string> = {
      cssText: '',
      backgroundColor: '',
      margin: '',
      display: '',
      minWidth: '',
      minHeight: '',
      maxWidth: '',
      background: '',
      visibility: '',
      position: '',
      top: '',
      left: '',
      width: '',
      height: '',
      overflow: '',
      transform: '',
      opacity: '',
      borderRadius: '',
    }

    return new Proxy(styleObj, {
      get(target, prop) {
        if (prop === 'setProperty') {
          return (name: string, value: string) => {
            target[name] = value
          }
        }
        if (prop === 'getPropertyValue') {
          return (name: string) => target[name] || ''
        }
        if (prop === 'removeProperty') {
          return (name: string) => {
            const oldValue = target[name]
            delete target[name]
            return oldValue || ''
          }
        }
        return target[prop as string] ?? ''
      },
      set(target, prop, value) {
        target[prop as string] = value
        return true
      },
    }) as unknown as CSSStyleDeclaration
  }

  // Create a fake element that can be returned by createElement
  const createFakeElement = (tagName: string) => ({
    tagName: tagName.toUpperCase(),
    style: createStyleProxy(),
    setAttribute: () => {},
    getAttribute: () => null,
    appendChild: () => {},
    removeChild: () => {},
    innerHTML: '',
    textContent: '',
    className: '',
    id: '',
    name: '',
    content: '',
  })

  const fakeDocument = {
    documentElement: {
      style: createStyleProxy(),
      className: '',
    },
    head: {
      innerHTML: '',
      appendChild: () => {},
      removeChild: () => {},
      children: [] as Element[],
      querySelectorAll: () => [],
    },
    body: {
      innerHTML: '',
      style: createStyleProxy(),
      appendChild: () => {},
      removeChild: () => {},
      className: '',
    },
    title: '',
    onclick: null as ((e: MouseEvent) => void) | null,
    createElement: (tagName: string) => createFakeElement(tagName),
    createTextNode: (text: string) => ({ textContent: text }),
    getElementById: () => null,
    querySelector: (selector: string) => {
      // Return a fake meta element for viewport queries
      if (selector === 'meta[name="viewport"]') {
        return null // Let the SDK create a new one
      }
      return null
    },
    querySelectorAll: () => [],
    write: () => {},
    close: () => {},
  }

  const fakeWindow: Record<string, unknown> = {
    __SpatialId: elementId,
    document: fakeDocument,
    location: {
      href: 'about:blank',
    },
    navigator: {
      userAgent: window?.navigator?.userAgent ?? '',
    },
    addEventListener: () => {},
    removeEventListener: () => {},
    postMessage: () => {},
    close: () => {},
    focus: () => {},
    blur: () => {},
    open: () => fakeWindow,
    // For portal rendering (React needs these)
    parent: null,
    top: null,
    closed: false,
  }

  // Set parent and top to self to avoid null reference issues
  fakeWindow.parent = fakeWindow
  fakeWindow.top = fakeWindow

  return fakeWindow as unknown as WindowProxy
}

export class AndroidPlatform implements PlatformAbility {
  async callJSB(cmd: string, msg: string): Promise<CommandResult> {
    // android JS Bridge interface only support sync invoking
    // in order to implement promise API, register every request by requestId and remove when resolve/reject.
    return new Promise((resolve, reject) => {
      try {
        const rId = nextRequestId()

        SpatialWebEvent.addEventReceiver(rId, (result: JSBResponse) => {
          SpatialWebEvent.removeEventReceiver(rId)
          if (result.success) {
            resolve(CommandResultSuccess(result.data))
          } else {
            const { code, message } = result.data as JSBError
            resolve(CommandResultFailure(code, message))
          }
        })

        const ans = window.webspatialBridge.postMessage(rId, cmd, msg)
        if (ans !== '') {
          SpatialWebEvent.removeEventReceiver(rId)
          // sync call
          const result = JSON.parse(ans) as JSBResponse
          if (result.success) {
            resolve(CommandResultSuccess(result.data))
          } else {
            const { code, message } = result.data as JSBError
            resolve(CommandResultFailure(code, message))
          }
        }
      } catch (error: unknown) {
        console.error(
          `AndroidPlatform cmd: ${cmd}, msg: ${msg} error: ${error}`,
        )
        const { code, message } = error as JSBError
        resolve(CommandResultFailure(code, message))
      }
    })
  }

  async callWebSpatialProtocol(
    command: string,
    query?: string,
    target?: string,
    features?: string,
  ): Promise<CommandResult> {
    // Android approach: Use direct JSB commands instead of window.open
    // This bypasses the polling mechanism and fake window complexity

    // Map webspatial:// commands to JSB commands
    const jsbCommand = this.mapProtocolToJSBCommand(command)
    if (!jsbCommand) {
      console.warn(`[AndroidPlatform] Unknown protocol command: ${command}`)
      return CommandResultFailure(
        'UnknownCommand',
        `Unknown command: ${command}`,
      )
    }

    // Generate element ID upfront
    const elementId = uuid()

    // Parse query params if any
    const params: Record<string, any> = { id: elementId }
    if (query) {
      const searchParams = new URLSearchParams(query)
      searchParams.forEach((value, key) => {
        try {
          // Try to parse as JSON for complex values
          params[key] = JSON.parse(decodeURIComponent(value))
        } catch {
          params[key] = decodeURIComponent(value)
        }
      })
    }

    // Call native directly via JSB
    const result = await this.callJSB(jsbCommand, JSON.stringify(params))

    if (!result.success) {
      return result
    }

    // Get the element ID from native response (may differ from our generated one)
    const nativeId = result.data?.id || elementId

    // Create a fake WindowProxy that satisfies SDK interface
    const windowProxy = createFakeWindowProxy(nativeId)

    return CommandResultSuccess({ windowProxy, id: nativeId })
  }

  /**
   * Maps webspatial:// protocol commands to JSB command names.
   */
  private mapProtocolToJSBCommand(command: string): string | null {
    const commandMap: Record<string, string> = {
      createSpatialized2DElement: 'CreateSpatialized2DElement',
      createSpatializedStatic3DElement: 'CreateSpatializedStatic3DElement',
      createSpatializedDynamic3DElement: 'CreateSpatializedDynamic3DElement',
      createSpatialScene: 'CreateSpatialScene',
    }
    return commandMap[command] || null
  }

  callWebSpatialProtocolSync(
    command: string,
    query?: string,
    target?: string,
    features?: string,
  ): CommandResult {
    // For sync calls, generate ID and create fake windowProxy immediately
    // Note: The native element creation happens async via the bridge callback
    const elementId = uuid()

    // For sync, we still use the intercepted window.open to trigger native creation
    // But return immediately with a fake windowProxy
    const windowProxy = createFakeWindowProxy(elementId)

    // Trigger native creation via window.open (will be intercepted by our JS hook)
    try {
      window.open(
        `webspatial://${command}?id=${elementId}&${query || ''}`,
        target,
        features,
      )
    } catch (e) {
      console.warn('[AndroidPlatform] window.open failed:', e)
    }

    return CommandResultSuccess({ windowProxy, id: elementId })
  }
}
