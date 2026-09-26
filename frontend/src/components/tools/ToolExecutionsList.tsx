import React from 'react';
import { ToolExecution } from '../../types';
import { Wrench, CheckCircle2, AlertTriangle, Clock, ExternalLink } from 'lucide-react';

interface ToolExecutionsListProps {
  tools: ToolExecution[];
}

export const ToolExecutionsList: React.FC<ToolExecutionsListProps> = ({ tools }) => {
  const getStatusBadge = (status: string, httpStatus?: number) => {
    if (status === 'SUCCESS' || httpStatus === 200) {
      return (
        <span className="badge badge-completed">
          <CheckCircle2 size={12} /> {httpStatus ? `HTTP ${httpStatus} OK` : 'SUCCESS'}
        </span>
      );
    }
    if (httpStatus === 503) {
      return (
        <span className="badge badge-failed">
          <AlertTriangle size={12} /> HTTP 503 SERVICE UNAVAILABLE
        </span>
      );
    }
    return (
      <span className="badge badge-failed">
        <AlertTriangle size={12} /> {httpStatus ? `HTTP ${httpStatus}` : status}
      </span>
    );
  };

  if (tools.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <Wrench size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No external tool calls executed yet. Tool Gateway engages when agents require real telemetry or repository inspections.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {tools.map((t, index) => {
        const isError = t.status === 'FAILED' || (t.httpStatus && t.httpStatus >= 400);

        return (
          <div
            key={t.toolCallId || index}
            style={{
              backgroundColor: 'var(--bg-secondary)',
              border: `1px solid ${isError ? 'rgba(244, 63, 94, 0.4)' : 'var(--border-color)'}`,
              borderRadius: '10px',
              padding: '14px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '12px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                backgroundColor: isError ? 'rgba(244, 63, 94, 0.15)' : 'rgba(56, 189, 248, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: isError ? 'var(--accent-rose)' : 'var(--accent-blue)'
              }}>
                <Wrench size={18} />
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="font-mono" style={{ fontSize: '0.92rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                    {t.toolName}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    (Real GitHub REST API)
                  </span>
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                  Call ID: <span className="font-mono">{t.toolCallId?.substring(0, 8)}...</span>
                  {t.errorClassification && (
                    <span style={{ color: 'var(--accent-rose)', marginLeft: '8px' }}>
                      • Classification: {t.errorClassification}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              {getStatusBadge(t.status, t.httpStatus)}
              <div style={{ textAlign: 'right' }}>
                <div className="font-mono" style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                  {t.durationMs ? `${t.durationMs}ms` : '—'}
                </div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                  {new Date(t.startedAt).toLocaleTimeString()}
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};
