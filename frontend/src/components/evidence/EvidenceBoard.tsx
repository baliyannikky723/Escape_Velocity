import React from 'react';
import { EvidenceItem } from '../../types';
import { FileCheck, HelpCircle, CheckCircle, AlertTriangle, Layers } from 'lucide-react';

interface EvidenceBoardProps {
  evidence: EvidenceItem[];
}

export const EvidenceBoard: React.FC<EvidenceBoardProps> = ({ evidence }) => {
  const getEvidenceBadge = (claim: string, sourceType: string) => {
    const claimLower = claim.toLowerCase();
    if (claimLower.includes('hypothesis') || claimLower.includes('possible') || claimLower.includes('may be') || claimLower.includes('suspected')) {
      return (
        <span className="badge badge-hypothesis">
          <AlertTriangle size={12} /> HYPOTHESIS
        </span>
      );
    }
    if (claimLower.includes('unknown') || claimLower.includes('unverified') || claimLower.includes('missing')) {
      return (
        <span className="badge badge-unknown">
          <HelpCircle size={12} /> UNKNOWN / UNRESOLVED
        </span>
      );
    }
    return (
      <span className="badge badge-fact">
        <CheckCircle size={12} /> VERIFIED FACT
      </span>
    );
  };

  if (evidence.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <Layers size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No evidence items recorded on Blackboard yet.</p>
      </div>
    );
  }

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
      gap: '16px'
    }}>
      {evidence.map((item, index) => {
        return (
          <div
            key={item.id || index}
            style={{
              backgroundColor: 'var(--bg-secondary)',
              border: '1px solid var(--border-color)',
              borderRadius: '12px',
              padding: '16px 18px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              transition: 'all 0.2s',
            }}
          >
            <div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
                {getEvidenceBadge(item.claim, item.sourceType)}
                <span className="font-mono" style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  Source: {item.sourceType}
                </span>
              </div>

              <div style={{ fontSize: '0.9rem', fontWeight: 500, color: 'var(--text-primary)', marginBottom: '10px', lineHeight: 1.4 }}>
                {item.claim}
              </div>

              {item.payload && (
                <div style={{
                  backgroundColor: 'var(--bg-primary)',
                  borderRadius: '6px',
                  padding: '8px 10px',
                  fontSize: '0.75rem',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--accent-cyan)',
                  marginBottom: '8px',
                  maxHeight: '120px',
                  overflowY: 'auto'
                }}>
                  <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                    {JSON.stringify(item.payload, null, 2)}
                  </pre>
                </div>
              )}
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: '0.72rem',
              color: 'var(--text-muted)',
              borderTop: '1px solid var(--border-color)',
              paddingTop: '8px',
              marginTop: '8px'
            }}>
              <span className="font-mono">
                {item.taskId ? `Task: ${item.taskId.substring(0, 8)}...` : 'System Triage'}
              </span>
              <span>{new Date(item.createdAt).toLocaleTimeString()}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
};
