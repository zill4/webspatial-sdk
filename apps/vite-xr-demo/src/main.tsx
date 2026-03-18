import React, { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

// Expose capture libraries globally for Android XR bitmap capture
// snapdom is 30-100x faster than html2canvas
import { snapdom } from '@zumer/snapdom'
import html2canvas from 'html2canvas'

// Expose to window for the SDK to use
;(window as any).snapdom = snapdom
;(window as any).html2canvas = html2canvas

console.log('[WebSpatial] Capture libraries exposed:', {
  snapdom: typeof snapdom,
  html2canvas: typeof html2canvas,
  windowSnapdom: typeof (window as any).snapdom,
})

// Log startup for debugging
console.log('[WebSpatial Vite Demo] Starting application...')

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

console.log('[WebSpatial Vite Demo] Application mounted')
