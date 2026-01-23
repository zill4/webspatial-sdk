import React, { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

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
