export function ensureWindowDocumentStructure(openedWindow: WindowProxy) {
  try {
    const { document } = openedWindow
    let documentElement = document.documentElement
    if (!documentElement) {
      documentElement = document.createElement('html')
      document.appendChild(documentElement)
    }

    let head = document.head
    if (!head) {
      head = document.createElement('head') as HTMLHeadElement
      if (documentElement.firstChild) {
        documentElement.insertBefore(head, documentElement.firstChild)
      } else {
        documentElement.appendChild(head)
      }
    }

    let body = document.body
    if (!body) {
      body = document.createElement('body') as HTMLBodyElement
      documentElement.appendChild(body)
    }

    return {
      document,
      documentElement,
      head,
      body,
    }
  } catch (error) {
    console.warn(
      '[WebSpatial] Failed to ensure child window document structure',
      error,
    )
    return null
  }
}

export function asyncLoadStyleToChildWindow(
  childWindow: WindowProxy,
  link: HTMLLinkElement,
  isCurrent: () => boolean,
): Promise<boolean> {
  return new Promise(resolve => {
    const { href } = link
    const sep = href.includes('?') ? '&' : '?'
    link.href = `${href}${sep}uniqueURL=${Math.random()}`

    let finished = false
    const finish = (ok: boolean) => {
      if (finished) return
      finished = true
      resolve(ok)
    }

    // need to wait for some time to make sure the style is loaded
    // otherwise, the style may not be applied
    link.onerror = () => {
      finish(false)
    }
    link.onload = () => {
      if (!isCurrent()) {
        link.parentNode?.removeChild(link)
        finish(false)
        return
      }
      finish(true)
    }

    setTimeout(() => {
      if (!isCurrent()) {
        finish(false)
        return
      }
      const childDocument = ensureWindowDocumentStructure(childWindow)
      if (!childDocument) {
        finish(false)
        return
      }
      childDocument.head.appendChild(link)
    }, 50)
  })
}

const WEBSPATIAL_SYNC_ATTR = 'data-webspatial-sync'
const WEBSPATIAL_SYNC_KEY_ATTR = 'data-webspatial-sync-key'

export function setOpenWindowStyle(openedWindow: WindowProxy) {
  const childDocument = ensureWindowDocumentStructure(openedWindow)
  if (!childDocument) return

  childDocument.documentElement.style.cssText +=
    document.documentElement.style.cssText
  childDocument.documentElement.style.backgroundColor = 'transparent'
  childDocument.body.style.margin = '0px'

  // openedWindow body's width and height should be set to inline-block to make sure the width and height are correct
  childDocument.body.style.display = 'inline-block'
  childDocument.body.style.minWidth = 'auto'
  childDocument.body.style.minHeight = 'auto'
  childDocument.body.style.maxWidth = 'fit-content'
  childDocument.body.style.minWidth = 'fit-content'
  childDocument.body.style.background = 'transparent'
}

interface SyncController {
  version: number
}

const controllers = new WeakMap<WindowProxy, SyncController>()

function getController(childWindow: WindowProxy): SyncController {
  const prev = controllers.get(childWindow)
  if (prev) return prev
  const next: SyncController = { version: 0 }
  controllers.set(childWindow, next)
  return next
}

export async function syncParentHeadToChild(childWindow: WindowProxy) {
  const controller = getController(childWindow)
  const version = ++controller.version
  const styleLoadedPromises: Promise<boolean>[] = []
  const childDocument = ensureWindowDocumentStructure(childWindow)
  if (!childDocument) {
    return []
  }
  const { head } = childDocument

  const isCurrent = () => controller.version === version

  const parentStyles = Array.from(document.head.querySelectorAll('style'))
  const parentStylesheets = Array.from(
    document.head.querySelectorAll('link[rel="stylesheet"][href]'),
  ) as HTMLLinkElement[]

  const desiredStylesheetKeys = new Set<string>()
  for (const link of parentStylesheets) {
    if (link.href) desiredStylesheetKeys.add(link.href)
  }

  const existingSyncedLinks = Array.from(
    head.querySelectorAll(
      `link[rel="stylesheet"][${WEBSPATIAL_SYNC_ATTR}="1"]`,
    ),
  ) as HTMLLinkElement[]
  for (const link of existingSyncedLinks) {
    const key = link.getAttribute(WEBSPATIAL_SYNC_KEY_ATTR) ?? link.href
    if (!desiredStylesheetKeys.has(key)) link.parentNode?.removeChild(link)
  }

  const prevSyncedStyles = head.querySelectorAll(
    `style[${WEBSPATIAL_SYNC_ATTR}="1"]`,
  )
  prevSyncedStyles.forEach(n => n.parentNode?.removeChild(n))

  for (const styleEl of parentStyles) {
    const node = styleEl.cloneNode(true) as HTMLStyleElement
    node.setAttribute(WEBSPATIAL_SYNC_ATTR, '1')
    head.appendChild(node)
  }

  const currentKeys = new Set<string>()
  const currentSyncedLinks = Array.from(
    head.querySelectorAll(
      `link[rel="stylesheet"][${WEBSPATIAL_SYNC_ATTR}="1"]`,
    ),
  ) as HTMLLinkElement[]
  for (const link of currentSyncedLinks) {
    currentKeys.add(link.getAttribute(WEBSPATIAL_SYNC_KEY_ATTR) ?? link.href)
  }

  for (const link of parentStylesheets) {
    const key = link.href
    if (!key || currentKeys.has(key)) continue
    const node = link.cloneNode(true) as HTMLLinkElement
    node.setAttribute(WEBSPATIAL_SYNC_ATTR, '1')
    node.setAttribute(WEBSPATIAL_SYNC_KEY_ATTR, key)
    styleLoadedPromises.push(
      asyncLoadStyleToChildWindow(childWindow, node, isCurrent),
    )
  }

  // sync className
  childDocument.documentElement.className = document.documentElement.className

  return Promise.all(styleLoadedPromises)
}
