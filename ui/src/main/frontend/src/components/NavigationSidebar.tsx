import { NavLink } from 'react-router-dom'

interface NavigationSidebarProps {
  isExpanded: boolean
}

/**
 * Minimizable navigation sidebar with icon-only collapsed mode
 */
export function NavigationSidebar({ isExpanded }: NavigationSidebarProps) {
  return (
    <nav className={`nav-sidebar ${isExpanded ? 'expanded' : 'collapsed'}`}>
      <NavLink to="/workflows" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
        <span className="nav-icon">📝</span>
        {isExpanded && <span className="nav-text">Workflows</span>}
      </NavLink>

      <NavLink to="/executions" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
        <span className="nav-icon">▶️</span>
        {isExpanded && <span className="nav-text">Executions</span>}
      </NavLink>

      <NavLink to="/launch" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
        <span className="nav-icon">🚀</span>
        {isExpanded && <span className="nav-text">Launch</span>}
      </NavLink>

      <style>{`
        .nav-sidebar {
          display: flex;
          flex-direction: column;
          background-color: #1a1a1a;
          border-right: 1px solid #333;
          transition: width 0.3s ease-in-out;
          overflow: hidden;
        }

        .nav-sidebar.expanded {
          width: 250px;
        }

        .nav-sidebar.collapsed {
          width: 60px;
        }

        .nav-item {
          display: flex;
          align-items: center;
          gap: 1rem;
          padding: 1rem;
          color: #ccc;
          text-decoration: none;
          transition: all 0.2s;
          border-left: 3px solid transparent;
          white-space: nowrap;
        }

        .nav-item:hover {
          background-color: #2a2a2a;
          color: white;
        }

        .nav-item.active {
          background-color: #2a2a4a;
          border-left-color: #646cff;
          color: white;
        }

        .nav-icon {
          font-size: 1.2rem;
          min-width: 1.5rem;
          text-align: center;
        }

        .nav-text {
          font-size: 0.95rem;
          font-weight: 500;
        }

        .nav-sidebar.collapsed .nav-item {
          justify-content: center;
          padding: 1rem 0.5rem;
        }

        @media (prefers-color-scheme: light) {
          .nav-sidebar {
            background-color: #f9f9f9;
            border-right-color: #ddd;
          }

          .nav-item {
            color: #555;
          }

          .nav-item:hover {
            background-color: #f0f0f0;
            color: #000;
          }

          .nav-item.active {
            background-color: #e8e8ff;
            color: #000;
          }
        }

        @media (max-width: 768px) {
          .nav-sidebar.expanded {
            position: fixed;
            left: 0;
            top: 60px;
            bottom: 0;
            z-index: 99;
            box-shadow: 2px 0 8px rgba(0, 0, 0, 0.3);
          }

          .nav-sidebar.collapsed {
            width: 0;
            border-right: none;
          }
        }
      `}</style>
    </nav>
  )
}
