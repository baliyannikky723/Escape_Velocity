import React from 'react';
import { RecoverySummary } from '../../types';
import { RefreshCw, CheckCircle2, XCircle, ArrowRight, ShieldAlert, Cpu } from 'lucide-react';

interface RecoveryAttemptsListProps {
  recovery?: RecoverySummary | null;
}

export const RecoveryAttemptsList: React.FC<RecoveryAttemptsListProps> = ({ recovery }) => {
  if (!recovery || recovery.totalAttempts === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <RefreshCw size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No failure recovery events triggered. System operational without upstream tool faults.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
        gap: '12px'
      }}>
        <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Total Recovery Invocations</div>
          <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-amber)' }}>
            {recovery.totalAttempts}
          </div>
        </div>

        <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)' }}>Successful Recoveries</div>
          <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-emerald)' }}>
            {recovery.successfulRecoveries}
          </div>
        </div>

        <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--accent-blue)' }}>Retries with Backoff</div>
          <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-blue)' }}>
            {recovery.retryCount}
          </div>
        </div>

        <div style={{ backgroundColor: 'var(--bg-secondary)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--accent-purple)' }}>Replans Triggered</div>
          <div className="font-mono" style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-purple)' }}>
            {recovery.replanCount}
          </div>
        </div>
      </div>

      {recovery.recentAttempts && recovery.recentAttempts.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {recovery.recentAttempts.map((att, index) => {
            const isSuccess = att.status === 'SUCCESS' || att.status === 'RECOVERED';
            return (
              <div
                key={att.id || index}
                style={{
                  backgroundColor: 'var(--bg-secondary)',
                  border: `1px solid ${isSuccess ? 'rgba(16, 185, 129, 0.4)' : 'rgba(244, 63, 94, 0.4)'}`,
                  borderRadius: '8px',
                  padding: '12px 16px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  {isSuccess ? (
                    <CheckCircle2 size={16} style={{ color: 'var(--accent-emerald)' }} />
                  ) : (
                    <XCircle size={16} style={{ color: 'var(--accent-rose)' }} />
                  )}
                  <div>
                    <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                      Attempt #{att.attemptNumber} • Strategy: {att.strategy}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Target Tool: <span className="font-mono">{att.toolName}</span> | Error: <span style={{ color: 'var(--accent-rose)' }}>{att.errorType}</span>
                    </div>
                  </div>
                </div>

                <span className={`badge ${isSuccess ? 'badge-completed' : 'badge-failed'}`}>
                  {att.status}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
