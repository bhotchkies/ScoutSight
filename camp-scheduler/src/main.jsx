import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/main.css'
// Static import (no top-level await) — safe for IIFE bundle format.
// In production, window.SCOUT_SIGHT_DATA is always set by the Java-generated HTML before
// this script runs, so mockData is included in the bundle but never used.
import mockData from './dev/mockData.js'

if (typeof window.SCOUT_SIGHT_DATA === 'undefined') {
  window.SCOUT_SIGHT_DATA = mockData
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
