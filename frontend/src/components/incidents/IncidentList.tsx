import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Incident, Investigation } from '../../types';
import { AlertCircle, ArrowRight, Play, CheckCircle2, Clock, ShieldAlert } from 'lucide-react';

interface IncidentListProps {
  incidents: Incident[];
  investigationsMap: Record<string, Investigation | null>;
  onStartInvestigation: (incidentId: string) => Promise<void>;
  isStartingId: string | null;
}

export const IncidentList: React.FC<IncidentListProps> = ({
  incidents,
  investigationsMap,
  onStartInvestigation,
  isStartingId,
}) => {
  const navigate = useNavigate();

  const getSeverityBadgeClass = (severity: string) => {
    switch (severity) {
      case 'P1': return 'badge-p1';
      case 'P2': return 'badge-p2';
      case 'P3': return 'badge-p3';
      case 'P4': return 'badge-p4';
      default: return 'badge-p4';
    }
  };

  const getInvestigationStatusBadge = (inv?: Investigation | null) => {
    if (!inv) {
      return (
        <span className="badge" style={{ backgroundColor: 'rgba(100, 116, 139, 0.15)', color: 'var(--text-muted)' }}>
          NO INVESTIGATION
        </span>
      );
    }

    switch (inv.status) {
      case 'COMPLETED':
        return (
          <span className="badge badge-completed">
            <CheckCircle2 size={12} /> COMPLETED
          </span>
        );
      case 'RUNNING':
        return (
          <span className="badge badge-running">
            <Clock size={12} className="spin-animation" /> INVESTIGATING
          </span>
        );
      case 'WAITING':
        return (
          <span className="badge badge-waiting">
            <Clock size={12} /> WAITING
          </span>
        );
      case 'HUMAN_REVIEW_REQUIRED':
      case 'HUMAN_APPROVAL_REQUIRED':
        return (
          <span className="badge badge-hitl">
            <ShieldAlert size={12} /> HUMAN REVIEW
          </span>
        );
      case 'FAILED':
      case 'BLOCKED':
        return (
          <span className="badge badge-failed">
            <AlertCircle size={12} /> {inv.status}
          </span>
        );
      default:
        return <span className="badge badge-running">{inv.status}</span>;
    }
  };

  if (incidents.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '60px 20px',
        backgroundColor: 'var(--bg-card)',
        borderRadius: '12px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <AlertCircle size={40} style={{ color: 'var(--text-muted)', marginBottom: '12px' }} />
        <h4 style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '4px' }}>
          No Active Incidents
        </h4>
        <p style={{ fontSize: '0.85rem' }}>Create an engineering incident above to start autonomous investigation.</p>
      </div>
    );
  }

  return (
    <div className="data-table-container">
      <table className="data-table">
        <thead>
          <tr>
            <th>Key</th>
            <th>Title & Service</th>
            <th>Severity</th>
            <th>Incident Status</th>
            <th>AI Investigation</th>
            <th>Triggered</th>
            <th style={{ textAlign: 'right' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((incident) => {
            const investigation = investigationsMap[incident.id];
            const hasInvestigation = Boolean(investigation);

            return (
              <tr key={incident.id}>
                <td>
                  <span className="font-mono" style={{ fontWeight: 600, color: 'var(--accent-blue)' }}>
                    {incident.incidentKey}
                  </span>
                </td>
                <td>
                  <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: '2px' }}>
                    {incident.title}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    Service: <span className="font-mono">{incident.serviceName}</span> | Env: <span>{incident.environment}</span>
                  </div>
                </td>
                <td>
                  <span className={`badge ${getSeverityBadgeClass(incident.severity)}`}>
                    {incident.severity}
                  </span>
                </td>
                <td>
                  <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                    {incident.status}
                  </span>
                </td>
                <td>
                  {getInvestigationStatusBadge(investigation)}
                </td>
                <td style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                  {new Date(incident.createdAt).toLocaleTimeString()}
                </td>
                <td style={{ textAlign: 'right' }}>
                  {hasInvestigation ? (
                    <button
                      onClick={() => navigate(`/investigations/${investigation?.id}`)}
                      className="btn btn-secondary"
                      style={{ fontSize: '0.78rem', padding: '6px 12px' }}
                    >
                      View Investigation <ArrowRight size={14} />
                    </button>
                  ) : (
                    <button
                      onClick={() => onStartInvestigation(incident.id)}
                      disabled={isStartingId === incident.id}
                      className="btn btn-primary"
                      style={{ fontSize: '0.78rem', padding: '6px 12px' }}
                    >
                      <Play size={14} />
                      {isStartingId === incident.id ? 'Launching...' : 'Start Investigation'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
