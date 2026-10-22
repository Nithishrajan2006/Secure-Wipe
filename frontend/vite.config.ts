import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],

  // sockjs-client uses the Node.js `global` variable which does not exist
  // in browser/ESM environments. Defining it as globalThis fixes the
  // "ReferenceError: global is not defined" white-screen crash.
  define: {
    global: 'globalThis',
  },

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('error', (err, _req, res) => {
            if (res && 'writeHead' in res && !res.headersSent) {
              res.writeHead(503, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({
                status: 503,
                message: 'Backend server is starting or not reachable on port 8080.',
              }))
            }
          })
        },
      },
      '/ws': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query', 'zustand'],
          'chart-vendor': ['recharts'],
          'ui-vendor': ['framer-motion', '@headlessui/react', 'lucide-react'],
          'date-vendor': ['date-fns'],
          'stomp-vendor': ['@stomp/stompjs', 'sockjs-client'],
        },
      },
    },
  },
})
