import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { NavigationSidebar } from './NavigationSidebar'

const SIDEBAR_STORAGE_KEY = 'maestro-sidebar-expanded'

/**
 * Main application layout with header, sidebar, and content area
 */
export function AppLayout() {
  const [sidebarExpanded, setSidebarExpanded] = useState(() => {
    const stored = localStorage.getItem(SIDEBAR_STORAGE_KEY)
    return stored !== null ? stored === 'true' : true
  })

  useEffect(() => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarExpanded))
  }, [sidebarExpanded])

  const toggleSidebar = () => {
    setSidebarExpanded(!sidebarExpanded)
  }

  return (
    <div className="app-layout">
      <header className="app-header">
        <button className="sidebar-toggle" onClick={toggleSidebar} aria-label="Toggle sidebar">
          ☰
        </button>
        <h1>🎭 Maestro</h1>
      </header>

      <div className="app-body">
        <NavigationSidebar isExpanded={sidebarExpanded} />
        <main className="app-main">
          <Outlet />
        </main>
      </div>

      <style>{`
        .app-layout {
          min-height: 100vh;
          display: flex;
          flex-direction: column;
        }

        .app-header {
          display: flex;
          align-items: center;
          gap: 1rem;
          padding: 0 1.5rem;
          height: 60px;
          background-color: #1a1a1a;
          border-bottom: 1px solid #333;
          position: sticky;
          top: 0;
          z-index: 100;
        }

        .app-header h1 {
          margin: 0;
          font-size: 1.5rem;
          font-weight: 600;
        }

        .sidebar-toggle {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 40px;
          height: 40px;
          background-color: transparent;
          border: 1px solid #333;
          border-radius: 4px;
          color: inherit;
          font-size: 1.2rem;
          cursor: pointer;
          transition: all 0.2s;
        }

        .sidebar-toggle:hover {
          background-color: #2a2a2a;
          border-color: #646cff;
        }

        .app-body {
          display: flex;
          flex: 1;
          min-height: 0;
        }

        .app-main {
          flex: 1;
          padding: 2rem;
          overflow-y: auto;
          min-height: 0;
        }

        @media (prefers-color-scheme: light) {
          .app-header {
            background-color: white;
            border-bottom-color: #ddd;
          }

          .sidebar-toggle {
            border-color: #ddd;
          }

          .sidebar-toggle:hover {
            background-color: #f5f5f5;
          }
        }

        @media (max-width: 768px) {
          .app-main {
            padding: 1rem;
          }
        }
      `}</style>
    </div>
  )
}
