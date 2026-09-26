import React from 'react';
import { TimelineEvent } from '../../types';
import {
  Brain,
  Bot,
  Wrench,
  FileCheck,
  ShieldAlert,
  ShieldCheck,
  RefreshCw,
  Clock,
  Sparkles,
  GitBranch,
} from 'lucide-react';

interface InvestigationTimelineProps {
  events: TimelineEvent[];
}

export const InvestigationTimeline: React.FC<InvestigationTimelineProps> = ({ events }) => {
  const getEventIcon = (eventType: string) => {
    switch (eventType) {
      case 'LLM_PLANNER_STARTED':
      case 'LLM_PLANNER_COMPLETED':
      case 'PLAN_RECONCILIATION':
        return <Brain size={12} color="var(--accent-blue)" />;
      case 'AGENT_RUN_STARTED':
      case 'AGENT_RUN_COMPLETED':
        return <Bot size={12} color="var(--accent-indigo)" />;
      case 'TOOL_INVOCATION_STARTED':
      case 'TOOL_INVOCATION_COMPLETED':
        return <Wrench size={12} color="var(--accent-emerald)" />;
      case 'EVIDENCE_RECORDED':
        return <FileCheck size={12} color="var(--accent-cyan)" />;
      case 'LLM_CRITIC_STARTED':
      case 'LLM_CRITIC_COMPLETED':
        return <ShieldCheck size={12} color="var(--accent-purple)" />;
      case 'LLM_CRITIC_REJECTED':
        return <ShieldAlert size={12} color="var(--accent-rose)" />;
      case 'RECOVERY_ATTEMPT_STARTED':
      case 'RECOVERY_ATTEMPT_COMPLETED':
      case 'LLM_FALLBACK_ACTIVATED':
        return <RefreshCw size={12} color="var(--accent-amber)" />;
      case 'HUMAN_REVIEW_REQUIRED':
      case 'HUMAN_ACTION_RECEIVED':
        return <ShieldAlert size={12} color="var(--accent-amber)" />;
      default:
        return <Clock size={12} color="var(--text-muted)" />;
    }
  };

  const formatEventType = (type: string) => {
    return type.replace(/_/g, ' ');
  };

  if (events.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <Clock size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No events recorded yet. Start the investigation to stream autonomous actions.</p>
      </div>
    );
  }

  return (
    <div className="timeline-stream">
      {events.map((event, index) => {
        const isCriticReject = event.eventType === 'LLM_CRITIC_REJECTED';
        const isFallback = event.eventType === 'LLM_FALLBACK_ACTIVATED';
        const isHitl = event.eventType === 'HUMAN_REVIEW_REQUIRED';

        return (
          <div key={event.eventId || index} className="timeline-item">
            <div className="timeline-marker">
              {getEventIcon(event.eventType)}
            </div>

            <div
              className="timeline-card"
              style={{
                borderColor: isCriticReject
                  ? 'var(--accent-rose)'
                  : isHitl
                  ? 'var(--accent-amber)'
                  : isFallback
                  ? 'var(--accent-amber)'
                  : 'var(--border-color)',
                backgroundColor: isCriticReject
                  ? 'rgba(244, 63, 94, 0.05)'
                  : isHitl
                  ? 'rgba(245, 158, 11, 0.05)'
                  : 'var(--bg-secondary)'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  color: isCriticReject ? 'var(--accent-rose)' : 'var(--accent-blue)',
                  letterSpacing: '0.04em'
                }}>
                  {formatEventType(event.eventType)}
                </span>
                <span className="font-mono" style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                  {new Date(event.occurredAt).toLocaleTimeString()}
                </span>
              </div>

              <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '4px' }}>
                {event.title}
              </div>

              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                {event.description}
              </p>

              {event.actorType && (
                <div style={{
                  marginTop: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  fontSize: '0.72rem',
                  color: 'var(--text-muted)'
                }}>
                  <span>Actor: <strong style={{ color: 'var(--text-secondary)' }}>{event.actorType}</strong> ({event.actorId})</span>
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
