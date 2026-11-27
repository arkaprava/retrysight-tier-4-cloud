/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const managerPort = process.env.MANAGER_PORT ?? '28477'
const managerOrigin =
  process.env.MANAGER_URL?.replace(/\/$/, '') ?? `http://localhost:${managerPort}`

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: Number(process.env.FRONTEND_PORT ?? 8080),
    proxy: {
      '/graphql': {
        target: managerOrigin,
        ws: true,
      },
      '/api': managerOrigin,
    },
  },
  preview: {
    port: Number(process.env.FRONTEND_PORT ?? 8080),
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'esbuild',
    // Production minification
    esbuild: {
      legalComments: 'none',
      minifyIdentifiers: true,
      minifySyntax: true,
      minifyWhitespace: true,
      treeShaking: true,
    },
    rollupOptions: {
      output: {
        // Avoid predictable chunk names for app code
        chunkFileNames: 'assets/[hash].js',
        entryFileNames: 'assets/[hash].js',
        assetFileNames: 'assets/[hash][extname]',
      },
    },
  },
})
