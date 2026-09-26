import React from 'react';
import { InvestigationReport } from '../../types';
import { FileBarChart, CheckCircle2, AlertTriangle, HelpCircle, Layers, Wrench, Bot, ShieldCheck, RefreshCw } from 'lucide-react';

interface FinalReportViewProps {
  report: InvestigationReport;
  onClose?: () => void;
}

export const FinalReportView: React.FC<FinalReportViewProps> = ({ report, onClose }) => {
  return (
    <div className="panel" style={{ border: '1px solid var(--accent-emerald)', padding: '28px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border-color)', paddingBottom: '16px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '38px',
            height: '38px',
            borderRadius: '10px',
            backgroundColor: 'rgba(16, 185, 129, 0.15)',
            color: 'var(--accent-emerald)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <FileBarChart size={22} />
          </div>
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              Final Autonomous Investigation Report
            </h3>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              Incident: <span className="font-mono" style={{ color: 'var(--accent-blue)' }}>{report.incidentKey}</span> ({report.incidentTitle})
            </p>
          </div>
        </div>

        {onClose && (
          <button onClick={onClose} className="btn btn-secondary" style={{ fontSize: '0.8rem' }}>
            Back to Investigation
          </button>
        )}
      </div>

      {/* Executive Summary */}
      <div style={{
        backgroundColor: 'var(--bg-secondary)',
        border: '1px solid var(--border-color)',
        borderRadius: '10px',
        padding: '18px 20px',
        marginBottom: '24px'
      }}>
        <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--accent-blue)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '8px' }}>
          Executive Summary
        </h4>
        <p style={{ fontSize: '0.92rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
          {report.executiveSummary || 'Autonomous investigation concluded with full evidence verification.'}
        </p>
        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '10px' }}>
          Stopping Reason: <strong style={{ color: 'var(--accent-emerald)' }}>{report.stoppingReason}</strong>
        </div>
      </div>

      {/* Breakdown: Key Facts & Supported Findings */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '20px', marginBottom: '24px' }}>
        {/* Verified Facts */}
        <div style={{
          backgroundColor: 'var(--bg-secondary)',
          border: '1px solid rgba(16, 185, 129, 0.3)',
          borderRadius: '10px',
          padding: '18px 20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <CheckCircle2 size={18} style={{ color: 'var(--accent-emerald)' }} />
            <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--accent-emerald)' }}>
              Verified Facts ({report.keyFacts?.length || 0})
            </h4>
          </div>
          {report.keyFacts && report.keyFacts.length > 0 ? (
            <ul style={{ paddingLeft: '18px', fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              {report.keyFacts.map((f, i) => <li key={i} style={{ marginBottom: '6px' }}>{f}</li>)}
            </ul>
          ) : (
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No confirmed facts isolated.</p>
          )}
        </div>

        {/* Supported Findings */}
        <div style={{
          backgroundColor: 'var(--bg-secondary)',
          border: '1px solid rgba(56, 189, 248, 0.3)',
          borderRadius: '10px',
          padding: '18px 20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <Layers size={18} style={{ color: 'var(--accent-cyan)' }} />
            <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--accent-cyan)' }}>
              Supported Findings ({report.supportedFindings?.length || 0})
            </h4>
          </div>
          {report.supportedFindings && report.supportedFindings.length > 0 ? (
            <ul style={{ paddingLeft: '18px', fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              {report.supportedFindings.map((sf, i) => <li key={i} style={{ marginBottom: '6px' }}>{sf}</li>)}
            </ul>
          ) : (
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No additional findings recorded.</p>
          )}
        </div>
      </div>

      {/* Breakdown: Hypotheses & Unknowns */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '20px', marginBottom: '24px' }}>
        {/* Hypotheses */}
        <div style={{
          backgroundColor: 'var(--bg-secondary)',
          border: '1px solid rgba(245, 158, 11, 0.3)',
          borderRadius: '10px',
          padding: '18px 20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <AlertTriangle size={18} style={{ color: 'var(--accent-amber)' }} />
            <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--accent-amber)' }}>
              Hypotheses (Unproven) ({report.hypotheses?.length || 0})
            </h4>
          </div>
          {report.hypotheses && report.hypotheses.length > 0 ? (
            <ul style={{ paddingLeft: '18px', fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              {report.hypotheses.map((h, i) => <li key={i} style={{ marginBottom: '6px' }}>{h}</li>)}
            </ul>
          ) : (
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No unverified hypotheses left pending.</p>
          )}
        </div>

        {/* Unknown / Unresolved */}
        <div style={{
          backgroundColor: 'var(--bg-secondary)',
          border: '1px solid var(--border-color)',
          borderRadius: '10px',
          padding: '18px 20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <HelpCircle size={18} style={{ color: 'var(--text-muted)' }} />
            <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Unknown / Unresolved ({report.unknowns?.length || 0})
            </h4>
          </div>
          {report.unknowns && report.unknowns.length > 0 ? (
            <ul style={{ paddingLeft: '18px', fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
              {report.unknowns.map((u, i) => <li key={i} style={{ marginBottom: '6px' }}>{u}</li>)}
            </ul>
          ) : (
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No unresolved gaps identified.</p>
          )}
        </div>
      </div>

      {/* Investigation Execution Stats */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-around',
        flexWrap: 'wrap',
        gap: '16px',
        padding: '16px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        fontSize: '0.82rem',
        color: 'var(--text-secondary)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Bot size={15} style={{ color: 'var(--accent-indigo)' }} />
          <span>Agents: <strong>{report.totalAgentRuns}</strong></span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Wrench size={15} style={{ color: 'var(--accent-emerald)' }} />
          <span>Tool Calls: <strong>{report.totalToolCalls}</strong></span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <ShieldCheck size={15} style={{ color: 'var(--accent-purple)' }} />
          <span>Critic Accepted: <strong>{report.criticAcceptCount}</strong> | Rejected: <strong style={{ color: 'var(--accent-rose)' }}>{report.criticRejectCount}</strong></span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <RefreshCw size={15} style={{ color: 'var(--accent-amber)' }} />
          <span>Recoveries: <strong>{report.recoveryCount}</strong></span>
        </div>
      </div>
    </div>
  );
};
