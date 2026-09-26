import React from 'react';
import { Investigation, Incident } from '../../types';
import { Play, CheckCircle2, Clock, ShieldAlert, AlertTriangle, FileBarChart, RefreshCw } from 'lucide-react';

interface InvestigationHeaderProps {
  investigation: Investigation;
  incident?: Incident | null;
  onStartNextStep: () => Promise<void>;
  isStepping: boolean;
  onOpenHitlModal: () => void;
  onViewReport: () => void;
  hasReport: boolean;
}

export const InvestigationHeader: React.FC<InvestigationHeaderProps> = ({
  investigation,
  incident,
  onStartNextStep,
  isStepping,
  onOpenHitlModal,
  onViewReport,
  hasReport,
}) => {
  const isHitlRequired =
    investigation.status === 'HUMAN_REVIEW_REQUIRED' ||
    investigation.status === 'HUMAN_APPROVAL_REQUIRED';

  const getStatusBadge = () => {
    switch (investigation.status) {
      case 'COMPLETED':
        return (
          <span className="badge badge-completed">
            <CheckCircle2 size={14} /> COMPLETED
          </span>
        );
      case 'RUNNING':
        return (
          <span className="badge badge-running">
            <Clock size={14} className="spin-animation" /> INVESTIGATING
          </span>
        );
      case 'WAITING':
        return (
          <span className="badge badge-waiting">
            <Clock size={14} /> AWAITING NEXT PHASE
          </span>
        );
      case 'HUMAN_REVIEW_REQUIRED':
      case 'HUMAN_APPROVAL_REQUIRED':
        return (
          <span className="badge badge-hitl">
            <ShieldAlert size={14} /> {investigation.status.replace(/_/g, ' ')}
          </span>
        );
      case 'FAILED':
      case 'BLOCKED':
        return (
          <span className="badge badge-failed">
            <AlertTriangle size={14} /> {investigation.status}
          </span>
        );
      default:
        return <span className="badge badge-running">{investigation.status}</span>;
    }
  };

  return (
    <div className="panel" style={{ padding: '20px 24px', marginBottom: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '6px' }}>
            <span className="font-mono" style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--accent-blue)' }}>
              {incident?.incidentKey || 'INC-000000'}
            </span>
            <span style={{ color: 'var(--border-color)' }}>|</span>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              {incident?.title || 'Autonomous Incident Investigation'}
            </h2>
            {getStatusBadge()}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Service: <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>{incident?.serviceName}</span>
            {' • '}Env: <span style={{ color: 'var(--text-secondary)' }}>{incident?.environment}</span>
            {' • '}Severity: <span style={{ color: 'var(--accent-rose)', fontWeight: 600 }}>{incident?.severity}</span>
            {' • '}Budget: <span>Max {investigation.maxTasks} Tasks ({investigation.maxRuntimeSeconds}s Limit)</span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {isHitlRequired && (
            <button
              onClick={onOpenHitlModal}
              className="btn btn-warning"
              style={{ padding: '8px 16px', animation: 'pulse-border 1.5s infinite' }}
            >
              <ShieldAlert size={16} /> Human Review Required
            </button>
          )}

          {investigation.status !== 'COMPLETED' && !isHitlRequired && (
            <button
              onClick={onStartNextStep}
              disabled={isStepping}
              className="btn btn-primary"
              style={{ padding: '8px 18px' }}
            >
              <Play size={15} />
              {isStepping ? 'Executing Multi-Agent Step...' : 'Execute AI Step / Replan'}
            </button>
          )}

          {hasReport && (
            <button
              onClick={onViewReport}
              className="btn btn-secondary"
              style={{ padding: '8px 16px', borderColor: 'var(--accent-emerald)', color: 'var(--accent-emerald)' }}
            >
              <FileBarChart size={16} /> View Final Synthesis Report
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
