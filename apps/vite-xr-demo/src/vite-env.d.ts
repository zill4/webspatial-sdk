/// <reference types="vite/client" />

// Extend CSSProperties to include WebSpatial CSS variables
import 'react'

declare module 'react' {
  interface CSSProperties {
    '--xr-back'?: number | string
    '--xr-background-material'?:
      | 'none'
      | 'transparent'
      | 'thin'
      | 'translucent'
      | 'regular'
      | 'thick'
      | string
  }
}

// Extend HTML attributes to include enable-xr
declare global {
  namespace JSX {
    interface IntrinsicAttributes {
      'enable-xr'?: boolean
    }
  }

  // WebSpatial global types
  interface Window {
    webspatialBridge?: {
      postMessage: (
        requestID: string,
        command: string,
        message: string,
      ) => string
    }
    __WebSpatialData?: {
      getNativeVersion: () => string
      getBackendName: () => string
      androidNativeMessage: (message: string) => void
    }
    __SpatialWebEvent?: (response: { id: string; data: any }) => void
    WebSpatailNativeVersion?: string
    WebSpatailEnabled?: boolean
  }
}

export {}
