import React from 'react';
import { AgentActivity } from '../../types';
import { Bot, CheckCircle2, Clock, XCircle, Wrench } from 'lucide-react';

interface AgentActivityListProps {
  agents: AgentActivity[];
}

export const AgentActivityList: React.FC<AgentActivityListProps> = ({ agents }) => {
  const getAgentBadge = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span className="badge badge-completed">
            <CheckCircle2 size={12} /> COMPLETED
          </span>
        );
      case 'RUNNING':
        return (
          <span className="badge badge-running">
            <Clock size={12} className="spin-animation" /> RUNNING
          </span>
        );
      case 'FAILED':
        return (
          <span className="badge badge-failed">
            <XCircle size={12} /> FAILED
          </span>
        );
      default:
        return <span className="badge">{status}</span>;
    }
  };

  if (agents.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <Bot size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No specialized agent runs executed yet.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {agents.map((a, index) => {
        return (
          <div
            key={a.agentRunId || index}
            style={{
              backgroundColor: 'var(--bg-secondary)',
              border: '1px solid var(--border-color)',
              borderRadius: '10px',
              padding: '16px 20px',
              transition: 'all 0.2s',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  backgroundColor: 'rgba(99, 102, 241, 0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--accent-indigo)'
                }}>
                  <Bot size={16} />
                </div>
                <div>
                  <h4 style={{ fontSize: '0.92rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                    {a.agentType}
                  </h4>
                  <span className="font-mono" style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                    Task ID: {a.taskId?.substring(0, 8)}...
                  </span>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                {getAgentBadge(a.status)}
                <div style={{ textAlign: 'right' }}>
                  <div className="font-mono" style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                    {a.durationMs ? `${a.durationMs}ms` : '—'}
                  </div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                    {new Date(a.startedAt).toLocaleTimeString()}
                  </div>
                </div>
              </div>
            </div>

            {a.outputSummary && (
              <p style={{
                fontSize: '0.82rem',
                color: 'var(--text-secondary)',
                backgroundColor: 'var(--bg-primary)',
                padding: '10px 14px',
                borderRadius: '6px',
                marginTop: '10px',
                lineHeight: 1.4
              }}>
                {a.outputSummary}
              </p>
            )}

            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '16px',
              marginTop: '10px',
              fontSize: '0.75rem',
              color: 'var(--text-muted)'
            }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Wrench size={13} style={{ color: 'var(--accent-emerald)' }} />
                <span>Tool Invocations: <strong>{a.toolCallsCount || 0}</strong></span>
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
};
