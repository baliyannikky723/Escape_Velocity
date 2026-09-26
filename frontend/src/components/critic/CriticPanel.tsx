import React from 'react';
import { CriticSummary, CriticEvaluation } from '../../types';
import { ShieldCheck, ShieldAlert, AlertCircle, ArrowRight, HelpCircle } from 'lucide-react';

interface CriticPanelProps {
  summary?: CriticSummary | null;
  evaluations?: CriticEvaluation[];
}

export const CriticPanel: React.FC<CriticPanelProps> = ({ summary, evaluations = [] }) => {
  const getDecisionBadge = (decision: string) => {
    switch (decision) {
      case 'ACCEPT':
        return (
          <span className="badge badge-completed">
            <ShieldCheck size={13} /> ACCEPTED
          </span>
        );
      case 'REJECT':
        return (
          <span className="badge badge-failed">
            <ShieldAlert size={13} /> REJECTED (UNGROUNDED)
          </span>
        );
      case 'INCONCLUSIVE':
        return (
          <span className="badge badge-waiting">
            <HelpCircle size={13} /> INCONCLUSIVE
          </span>
        );
      case 'HUMAN_APPROVAL_REQUIRED':
        return (
          <span className="badge badge-hitl">
            <AlertCircle size={13} /> HUMAN APPROVAL REQUIRED
          </span>
        );
      default:
        return <span className="badge">{decision}</span>;
    }
  };

  if (!summary && evaluations.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <ShieldCheck size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No critic evaluations yet. Critic triggers automatically after each agent findings submission.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {summary && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: '12px',
          marginBottom: '8px'
        }}>
          <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Total Evaluations</div>
            <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              {summary.totalEvaluations}
            </div>
          </div>

          <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)' }}>Accepted Findings</div>
            <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-emerald)' }}>
              {summary.acceptedCount}
            </div>
          </div>

          <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--accent-rose)' }}>Rejected Hypotheses</div>
            <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-rose)' }}>
              {summary.rejectedCount}
            </div>
          </div>

          <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--accent-purple)' }}>Replanning Triggered</div>
            <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: summary.replanTriggeredByCritic ? 'var(--accent-amber)' : 'var(--text-secondary)' }}>
              {summary.replanTriggeredByCritic ? 'YES' : 'NO'}
            </div>
          </div>
        </div>
      )}

      {evaluations.map((item) => {
        const isReject = item.decision === 'REJECT';
        const isHumanReview = item.decision === 'HUMAN_APPROVAL_REQUIRED';

        return (
          <div
            key={item.id}
            style={{
              backgroundColor: 'var(--bg-secondary)',
              border: `1px solid ${isReject ? 'var(--accent-rose)' : isHumanReview ? 'var(--accent-amber)' : 'var(--border-color)'}`,
              borderRadius: '10px',
              padding: '16px 20px',
              boxShadow: isReject ? '0 0 10px rgba(244, 63, 94, 0.15)' : 'none'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                {getDecisionBadge(item.decision)}
                <span className="font-mono" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Task ID: {item.taskId?.substring(0, 8)}...
                </span>
              </div>
              <span className="font-mono" style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                {new Date(item.createdAt).toLocaleTimeString()}
              </span>
            </div>

            <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '8px' }}>
              {item.reason}
            </div>

            {item.supportedClaims?.length > 0 && (
              <div style={{ marginBottom: '6px', fontSize: '0.78rem' }}>
                <span style={{ color: 'var(--accent-emerald)', fontWeight: 600 }}>Supported Claims:</span>
                <ul style={{ paddingLeft: '20px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {item.supportedClaims.map((c, i) => <li key={i}>{c}</li>)}
                </ul>
              </div>
            )}

            {item.unsupportedClaims?.length > 0 && (
              <div style={{ marginBottom: '6px', fontSize: '0.78rem' }}>
                <span style={{ color: 'var(--accent-rose)', fontWeight: 600 }}>Unsupported Causal Leaps:</span>
                <ul style={{ paddingLeft: '20px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {item.unsupportedClaims.map((c, i) => <li key={i}>{c}</li>)}
                </ul>
              </div>
            )}

            {item.missingEvidence?.length > 0 && (
              <div style={{ marginBottom: '6px', fontSize: '0.78rem' }}>
                <span style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>Missing Grounded Evidence:</span>
                <ul style={{ paddingLeft: '20px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {item.missingEvidence.map((m, i) => <li key={i}>{m}</li>)}
                </ul>
              </div>
            )}

            {item.recommendedFollowUp?.length > 0 && (
              <div style={{
                marginTop: '10px',
                paddingTop: '8px',
                borderTop: '1px dashed var(--border-color)',
                fontSize: '0.78rem',
                color: 'var(--accent-blue)',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}>
                <ArrowRight size={13} /> Recommended Replanning Target: <strong>{item.recommendedFollowUp.join(', ')}</strong>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
