import React, {
  CSSProperties,
  ElementType,
  ForwardedRef,
  forwardRef,
  useContext,
  useEffect,
} from 'react'
import { SpatializedContainer } from './SpatializedContainer'
import { getSession } from '../utils'
import {
  SpatialCustomStyleVars,
  Spatialized2DElementContainerProps,
  SpatializedElementRef,
  SpatializedContentProps,
  SpatializedDivElementRef,
} from './types'
import {
  PortalInstanceContext,
  PortalInstanceObject,
} from './context/PortalInstanceContext'
import { Spatialized2DElement } from '@webspatial/core-sdk'
import { createPortal } from 'react-dom'
import { getInheritedStyleProps, parseCornerRadius } from './utils'
import {
  usesAndroidBitmapCapture,
} from '../utils/androidBitmapCapture'

function asyncLoadStyleToChildWindow(
  childWindow: WindowProxy,
  n: HTMLLinkElement,
) {
  return new Promise(resolve => {
    // Safari seems to have a bug where
    // ~1/50 loads, if the same url is loaded very quickly in a window and a child window,
    // the second load request never is fired resulting in css not to be applied.
    // Workaround this by making the css stylesheet request unique
    n.href += '?uniqueURL=' + Math.random()
    n.onerror = function (error) {
      console.error('Failed to load style link', (n as HTMLLinkElement).href)
      resolve(false)
    }
    n.onload = function () {
      resolve(true)
    }

    // need to wait for some time to make sure the style is loaded
    // otherwise, the style may not be applied
    setTimeout(() => {
      const childDocument = ensureWindowDocumentStructure(childWindow)
      if (!childDocument) {
        resolve(false)
        return
      }
      childDocument.head.appendChild(n)
    }, 50)
  })
}

function ensureWindowDocumentStructure(openedWindow: WindowProxy) {
  try {
    const document = openedWindow.document
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

function setOpenWindowStyle(openedWindow: WindowProxy) {
  const childDocument = ensureWindowDocumentStructure(openedWindow)
  if (!childDocument) {
    return
  }

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

async function syncParentHeadToChild(childWindow: WindowProxy) {
  const childDocument = ensureWindowDocumentStructure(childWindow)
  if (!childDocument) {
    return []
  }

  const styleLoadedPromises = []

  for (let i = 0; i < document.head.children.length; i++) {
    let n = document.head.children[i].cloneNode(true)
    if (
      n.nodeName == 'LINK' &&
      (n as HTMLLinkElement).rel == 'stylesheet' &&
      (n as HTMLLinkElement).href
    ) {
      const promise = asyncLoadStyleToChildWindow(
        childWindow,
        n as HTMLLinkElement,
      )
      styleLoadedPromises.push(promise)
    } else {
      childDocument.head.appendChild(n)
    }
  }

  // sync className
  childDocument.documentElement.className =
    document.documentElement.className

  return Promise.all(styleLoadedPromises)
}

function getJSXPortalInstance<P extends ElementType>(
  inProps: Omit<
    SpatializedContentProps<SpatializedElementRef, P>,
    'spatializedElement'
  >,
  portalInstanceObject: PortalInstanceObject,
) {
  const { component: El, style: inStyle = {}, ...props } = inProps
  const extraStyle: CSSProperties = {
    visibility: 'visible',
    position: 'relative',
    top: '0px',
    left: '0px',
    margin: '0px',
    marginLeft: '0px',
    marginRight: '0px',
    marginTop: '0px',
    marginBottom: '0px',
    borderRadius: '0px',
    // overflow: '',
    transform: 'none',
  }

  const computedStyle = portalInstanceObject.computedStyle!
  const inheritedPortalStyle: CSSProperties =
    getInheritedStyleProps(computedStyle)

  const style = {
    ...inStyle,
    ...inheritedPortalStyle,
    ...extraStyle,
  }

  return <El style={style} {...props} />
}

function useSyncHeaderStyle(windowProxy: WindowProxy, enabled: boolean = true) {
  useEffect(() => {
    // Skip on Android - fake WindowProxy doesn't have real DOM
    if (!enabled) return

    // sync parent head to child when document header style changed
    const headObserver = new MutationObserver(_ => {
      syncParentHeadToChild(windowProxy)
    })

    headObserver.observe(document.head, { childList: true, subtree: true })
    return () => {
      headObserver.disconnect()
    }
  }, [enabled])
}

function useSyncDocumentTitle(
  windowProxy: WindowProxy,
  spatializedElement: Spatialized2DElement,
  name: string,
) {
  useEffect(() => {
    windowProxy.document.title = name
    spatializedElement.updateProperties({
      name,
    })
  }, [name])
}

function SpatializedContent<P extends ElementType>(
  props: SpatializedContentProps<SpatializedElementRef, P>,
) {
  const { spatializedElement, ...restProps } = props
  const spatialized2DElement = spatializedElement as Spatialized2DElement
  const windowProxy = spatialized2DElement.windowProxy

  // Bitmap mode uses a fake WindowProxy and captures from the root WebView instead.
  const isAndroidBitmapMode = usesAndroidBitmapCapture()

  // Live window mode needs styles synced just like visionOS.
  useSyncHeaderStyle(windowProxy, !isAndroidBitmapMode)

  const name: string = (restProps as any)['data-name'] || ''
  useSyncDocumentTitle(windowProxy, spatialized2DElement, name)

  const portalInstanceObject: PortalInstanceObject = useContext(
    PortalInstanceContext,
  )!

  if (isAndroidBitmapMode) {
    return null
  }

  const childDocument = ensureWindowDocumentStructure(windowProxy)
  if (!childDocument?.body) {
    return null
  }

  const JSXPortalInstance = getJSXPortalInstance(
    restProps,
    portalInstanceObject,
  )

  return createPortal(JSXPortalInstance, childDocument.body)
}

function getExtraSpatializedElementProperties(
  computedStyle: CSSStyleDeclaration,
) {
  // get extra spatialized element properties for Spatialized2DElement
  const overflow = computedStyle.getPropertyValue('overflow')
  const scrollPageEnabled = ['visible', 'hidden', 'clip'].indexOf(overflow) >= 0
  const material = computedStyle.getPropertyValue(
    SpatialCustomStyleVars.backgroundMaterial,
  )

  const properties: Record<string, any> = {}
  properties.scrollPageEnabled = scrollPageEnabled
  properties.cornerRadius = parseCornerRadius(computedStyle)
  if (material) {
    properties.material = material
  }

  // may need add scrollEdgeInsetsMarginRight in future
  return properties
}

async function createSpatializedElement() {
  const spatializedElement = await getSession()!.createSpatialized2DElement()
  const windowProxy = spatializedElement.windowProxy

  // Bitmap mode renders from the root WebView instead of per-element child windows.
  if (usesAndroidBitmapCapture()) {
    console.log(
      '[WebSpatial] Android: Skipping WindowProxy setup, using bitmap capture',
    )
    return spatializedElement
  }

  // VisionOS: Each element gets a real WKWebView, set up its styles
  setOpenWindowStyle(windowProxy)
  await syncParentHeadToChild(windowProxy)

  const childDocument = ensureWindowDocumentStructure(windowProxy)
  if (!childDocument) {
    return spatializedElement
  }

  const viewport = childDocument.document.querySelector(
    'meta[name="viewport"]',
  )
  if (viewport) {
    viewport?.setAttribute(
      'content',
      ` initial-scale=1.0, maximum-scale=1.0, user-scalable=no`,
    )
  } else {
    const meta = childDocument.document.createElement('meta')
    meta.name = 'viewport'
    meta.content = 'initial-scale=1.0, maximum-scale=1.0, user-scalable=no'
    childDocument.head.appendChild(meta)
  }

  return spatializedElement
}

function Spatialized2DElementContainerBase<P extends ElementType>(
  props: Spatialized2DElementContainerProps<P>,
  ref: ForwardedRef<SpatializedDivElementRef>,
) {
  return (
    <SpatializedContainer<SpatializedElementRef>
      ref={ref as any}
      createSpatializedElement={createSpatializedElement}
      getExtraSpatializedElementProperties={
        getExtraSpatializedElementProperties
      }
      spatializedContent={SpatializedContent}
      {...props}
    />
  )
}

export const Spatialized2DElementContainer = forwardRef(
  Spatialized2DElementContainerBase,
) as <P extends ElementType>(
  props: Spatialized2DElementContainerProps<P> & {
    ref: ForwardedRef<SpatializedElementRef>
  },
) => React.ReactElement | null
