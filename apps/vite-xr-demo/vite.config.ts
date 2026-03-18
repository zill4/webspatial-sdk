import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      // Use WebSpatial's custom JSX runtime to enable spatial features
      // This allows `enable-xr` attribute and `--xr-*` CSS variables to work
      jsxImportSource: '@webspatial/react-sdk',
    }),
  ],

  // Use relative paths for assets - required for Android WebView
  // This ensures all asset URLs are relative (./assets/...) instead of absolute (/)
  base: './',

  resolve: {
    alias: {
      // Resolve workspace packages to their built dist folders
      '@webspatial/core-sdk': path.resolve(
        __dirname,
        '../../packages/core/dist',
      ),
      '@webspatial/react-sdk/jsx-runtime': path.resolve(
        __dirname,
        '../../packages/react/dist/jsx/jsx-runtime.js',
      ),
      '@webspatial/react-sdk/jsx-dev-runtime': path.resolve(
        __dirname,
        '../../packages/react/dist/jsx/jsx-dev-runtime.js',
      ),
      '@webspatial/react-sdk': path.resolve(
        __dirname,
        '../../packages/react/dist/default',
      ),
    },
  },

  build: {
    // Output directory
    outDir: 'dist',

    // Generate source maps for debugging
    sourcemap: true,

    // Ensure assets use relative paths
    assetsDir: 'assets',

    rollupOptions: {
      output: {
        // Use consistent chunk names for better caching
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },

  // Development server configuration
  server: {
    port: 5174,
    open: true,
  },
})
