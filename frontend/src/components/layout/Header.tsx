import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ShieldAlert, Activity, GitBranch, RefreshCw, AlertTriangle } from 'lucide-react';

interface HeaderProps {
  activeInvestigationsCount?: number;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  activeInvestigationsCount = 0,
  onRefresh,
  isRefreshing = false,
}) => {
  const location = useLocation();

  return (
    <header className="header-bar">
      <div style={{ display: 'flex', alignItems: 'center', gap: '32px' }}>
        <Link to="/" className="brand-logo">
          <div className="brand-icon">
            <ShieldAlert size={22} color="#ffffff" />
          </div>
          <div className="brand-text">
            <h1>IncidentMind</h1>
            <p>Autonomous Incident Response Platform</p>
          </div>
        </Link>

        <nav style={{ display: 'flex', gap: '8px' }}>
          <Link
            to="/"
            className={`btn ${location.pathname === '/' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <Activity size={14} /> Incidents & Operations
          </Link>
        </nav>
      </div>

      <div className="header-status">
        <div className="status-pill">
          <span className="dot-pulse green"></span>
          <span>Control Plane: Active</span>
        </div>

        {activeInvestigationsCount > 0 ? (
          <div className="status-pill" style={{ borderColor: 'var(--accent-blue)', color: 'var(--accent-blue)' }}>
            <span className="dot-pulse blue"></span>
            <span>{activeInvestigationsCount} Active Investigation{activeInvestigationsCount > 1 ? 's' : ''}</span>
          </div>
        ) : null}

        {onRefresh && (
          <button
            onClick={onRefresh}
            className="btn btn-secondary"
            title="Refresh dashboard data"
            style={{ padding: '6px 12px' }}
            disabled={isRefreshing}
          >
            <RefreshCw size={14} className={isRefreshing ? 'spin-animation' : ''} />
            {isRefreshing ? 'Syncing...' : 'Sync'}
          </button>
        )}
      </div>
    </header>
  );
};
