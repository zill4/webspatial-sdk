import React, { useState, useEffect } from 'react'

/**
 * WebSpatial Vite Demo for Android XR
 *
 * This demo showcases WebSpatial's spatial features using Vite + React,
 * which provides better WebView compatibility than Next.js.
 *
 * Key spatial features demonstrated:
 * - `enable-xr` attribute: Makes HTML elements spatial in XR environments
 * - `--xr-back` CSS variable: Controls depth offset (higher = further back)
 * - `--xr-background-material`: Glass-like material effects (thin, translucent, thick, etc.)
 */

// Environment detection
function detectEnvironment() {
  const ua = navigator.userAgent
  const isWebSpatial = ua.includes('WebSpatial/')
  const version = isWebSpatial ? ua.match(/WebSpatial\/([\d.]+)/)?.[1] : null

  return {
    isWebSpatial,
    version,
    userAgent: ua,
    hasBridge: typeof (window as any).webspatialBridge !== 'undefined',
    hasWebSpatialData: typeof (window as any).__WebSpatialData !== 'undefined',
  }
}

function DebugPanel() {
  const [env, setEnv] = useState<ReturnType<typeof detectEnvironment> | null>(
    null,
  )
  const [logs, setLogs] = useState<string[]>([])

  useEffect(() => {
    const detected = detectEnvironment()
    setEnv(detected)

    // Log environment info
    const logLines = [
      `WebSpatial: ${detected.isWebSpatial ? 'Yes' : 'No'}`,
      `Version: ${detected.version || 'N/A'}`,
      `Bridge: ${detected.hasBridge ? 'Available' : 'Not found'}`,
      `__WebSpatialData: ${detected.hasWebSpatialData ? 'Available' : 'Not found'}`,
    ]
    setLogs(logLines)

    console.log('[WebSpatial Debug]', detected)
  }, [])

  if (!env) return null

  return (
    <div
      style={{
        position: 'fixed',
        top: '10px',
        right: '10px',
        background: 'rgba(0, 0, 0, 0.8)',
        color: '#4ade80',
        padding: '12px',
        borderRadius: '8px',
        fontSize: '11px',
        fontFamily: 'monospace',
        maxWidth: '300px',
        zIndex: 9999,
      }}
    >
      <div
        style={{ fontWeight: 'bold', marginBottom: '8px', color: '#60a5fa' }}
      >
        Environment
      </div>
      {logs.map((log, i) => (
        <div key={i} style={{ marginBottom: '2px' }}>
          {log}
        </div>
      ))}
    </div>
  )
}

function SpatialCard({
  title,
  depth,
  material,
  color,
  onClick,
}: {
  title: string
  depth: number
  material: string
  color: string
  onClick?: () => void
}) {
  return (
    <div
      enable-xr
      onClick={onClick}
      style={{
        '--xr-back': depth,
        '--xr-background-material': material,
        backgroundColor: color,
        width: '280px',
        padding: '20px',
        borderRadius: '16px',
        cursor: onClick ? 'pointer' : 'default',
        transition: 'transform 0.2s',
      }}
    >
      <h3 style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '8px' }}>
        {title}
      </h3>
      <div style={{ fontSize: '12px', opacity: 0.8 }}>
        <div>Depth: {depth}px</div>
        <div>Material: {material}</div>
      </div>
    </div>
  )
}

function App() {
  const [tapCount, setTapCount] = useState(0)
  const [activeCard, setActiveCard] = useState<string | null>(null)

  // Log when app mounts
  useEffect(() => {
    console.log('[WebSpatial Vite Demo] App component mounted')
    console.log(
      '[WebSpatial Vite Demo] Window dimensions:',
      window.innerWidth,
      'x',
      window.innerHeight,
    )
  }, [])

  const handleCardTap = (cardName: string) => {
    setTapCount(prev => prev + 1)
    setActiveCard(cardName)
    console.log(`[WebSpatial] Card tapped: ${cardName}`)
  }

  return (
    <div style={{ padding: '24px', minHeight: '100vh' }}>
      <DebugPanel />

      <header style={{ marginBottom: '32px' }}>
        <h1
          style={{ fontSize: '28px', fontWeight: 'bold', marginBottom: '8px' }}
        >
          WebSpatial Vite Demo
        </h1>
        <p style={{ color: '#9ca3af', fontSize: '14px' }}>
          Android XR spatial web experience powered by Vite + React
        </p>
      </header>

      {/* Interaction counter */}
      <div
        style={{
          marginBottom: '24px',
          padding: '16px',
          background: '#16213e',
          borderRadius: '12px',
        }}
      >
        <div style={{ fontSize: '14px', color: '#9ca3af' }}>Interactions</div>
        <div style={{ fontSize: '24px', fontWeight: 'bold' }}>{tapCount}</div>
        {activeCard && (
          <div style={{ fontSize: '12px', color: '#60a5fa', marginTop: '4px' }}>
            Last: {activeCard}
          </div>
        )}
      </div>

      {/* Spatial Cards Section */}
      <section style={{ marginBottom: '32px' }}>
        <h2
          style={{ fontSize: '18px', fontWeight: '600', marginBottom: '16px' }}
        >
          Spatial Cards (Different Depths)
        </h2>
        <p style={{ fontSize: '13px', color: '#9ca3af', marginBottom: '16px' }}>
          Each card has a different <code>--xr-back</code> value, creating depth
          in XR.
        </p>

        <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
          <SpatialCard
            title="Near Card"
            depth={30}
            material="translucent"
            color="#0f3460"
            onClick={() => handleCardTap('Near Card')}
          />
          <SpatialCard
            title="Mid Card"
            depth={80}
            material="thin"
            color="#e94560"
            onClick={() => handleCardTap('Mid Card')}
          />
          <SpatialCard
            title="Far Card"
            depth={150}
            material="thick"
            color="#533483"
            onClick={() => handleCardTap('Far Card')}
          />
        </div>
      </section>

      {/* Material Types Section */}
      <section style={{ marginBottom: '32px' }}>
        <h2
          style={{ fontSize: '18px', fontWeight: '600', marginBottom: '16px' }}
        >
          Background Materials
        </h2>
        <p style={{ fontSize: '13px', color: '#9ca3af', marginBottom: '16px' }}>
          Different <code>--xr-background-material</code> values create
          glass-like effects.
        </p>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          {[
            'none',
            'transparent',
            'thin',
            'translucent',
            'regular',
            'thick',
          ].map((material, index) => (
            <div
              key={material}
              enable-xr
              style={{
                '--xr-back': 50 + index * 20,
                '--xr-background-material': material,
                backgroundColor: 'rgba(255, 255, 255, 0.1)',
                padding: '16px 24px',
                borderRadius: '12px',
                fontSize: '13px',
                textAlign: 'center',
              }}
            >
              {material}
            </div>
          ))}
        </div>
      </section>

      {/* Nested Spatial Elements */}
      <section style={{ marginBottom: '32px' }}>
        <h2
          style={{ fontSize: '18px', fontWeight: '600', marginBottom: '16px' }}
        >
          Nested Spatial Elements
        </h2>

        <div
          enable-xr
          style={{
            '--xr-back': 40,
            '--xr-background-material': 'translucent',
            backgroundColor: '#16213e',
            padding: '24px',
            borderRadius: '16px',
            width: '400px',
          }}
        >
          <h3 style={{ marginBottom: '12px' }}>Outer Container (40px back)</h3>

          <div
            enable-xr
            style={{
              '--xr-back': 80,
              '--xr-background-material': 'thin',
              backgroundColor: '#0f3460',
              padding: '16px',
              borderRadius: '12px',
            }}
          >
            <h4 style={{ marginBottom: '8px' }}>Inner Element (80px back)</h4>
            <p style={{ fontSize: '12px', color: '#9ca3af' }}>
              Nested spatial elements create layered depth effects
            </p>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section
        style={{
          padding: '20px',
          background: '#16213e',
          borderRadius: '12px',
          maxWidth: '600px',
        }}
      >
        <h3
          style={{ fontSize: '16px', fontWeight: '600', marginBottom: '12px' }}
        >
          How WebSpatial Works
        </h3>
        <ul
          style={{ fontSize: '13px', lineHeight: '1.8', paddingLeft: '20px' }}
        >
          <li>
            <code>enable-xr</code> attribute makes elements spatial on XR
            devices
          </li>
          <li>
            <code>--xr-back</code> CSS variable controls depth in 3D space
          </li>
          <li>
            <code>--xr-background-material</code> sets glass-like material
            effects
          </li>
          <li>On non-XR devices, elements render as normal 2D HTML</li>
          <li>
            <strong>Vite + React</strong> provides better WebView compatibility
            than Next.js
          </li>
        </ul>
      </section>
    </div>
  )
}

export default App
