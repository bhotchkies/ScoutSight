import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    // Writes main.js directly into Java resources — bundled and ready to commit
    outDir: '../src/main/resources/static/camp_scheduler',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        // IIFE: no import/export, no type="module" needed, works with file:// URLs.
        // CSS is automatically inlined into main.js via a runtime style-injector.
        // No separate CSS file is produced.
        format: 'iife',
        name: 'CampScheduler',
        entryFileNames: 'main.js'
      }
    }
  }
})
