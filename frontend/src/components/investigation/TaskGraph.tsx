import React from 'react';
import { TaskGraph as TaskGraphType, GraphNode, InvestigationTask } from '../../types';
import { GitBranch, CheckCircle2, Clock, XCircle, ArrowDown, Bot, Wrench, ShieldAlert } from 'lucide-react';

interface TaskGraphProps {
  graph?: TaskGraphType | null;
  tasks: InvestigationTask[];
}

export const TaskGraph: React.FC<TaskGraphProps> = ({ graph, tasks }) => {
  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle2 size={16} style={{ color: 'var(--accent-emerald)' }} />;
      case 'RUNNING':
        return <Clock size={16} className="spin-animation" style={{ color: 'var(--accent-blue)' }} />;
      case 'FAILED':
      case 'REJECTED':
        return <XCircle size={16} style={{ color: 'var(--accent-rose)' }} />;
      default:
        return <Clock size={16} style={{ color: 'var(--text-muted)' }} />;
    }
  };

  const getNodeBadgeClass = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'badge-completed';
      case 'RUNNING': return 'badge-running';
      case 'FAILED':
      case 'REJECTED': return 'badge-failed';
      default: return 'badge-waiting';
    }
  };

  if (tasks.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: '36px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '10px',
        border: '1px dashed var(--border-color)',
        color: 'var(--text-secondary)'
      }}>
        <GitBranch size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
        <p style={{ fontSize: '0.85rem' }}>No tasks planned yet. Trigger an AI step to generate the investigation DAG.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: '16px'
      }}>
        {tasks.map((task, index) => {
          return (
            <div
              key={task.id}
              style={{
                backgroundColor: 'var(--bg-secondary)',
                border: `1px solid ${task.status === 'COMPLETED' ? 'rgba(16, 185, 129, 0.4)' : task.status === 'RUNNING' ? 'rgba(56, 189, 248, 0.5)' : 'var(--border-color)'}`,
                borderRadius: '12px',
                padding: '16px 18px',
                boxShadow: task.status === 'RUNNING' ? 'var(--shadow-glow-blue)' : 'var(--shadow-sm)',
                transition: 'all 0.2s ease',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span className="font-mono" style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  TASK #{index + 1}
                </span>
                <span className={`badge ${getNodeBadgeClass(task.status)}`}>
                  {getStatusIcon(task.status)} {task.status}
                </span>
              </div>

              <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '4px' }}>
                {task.taskType}
              </h4>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '12px', lineHeight: 1.4 }}>
                {task.title || task.description}
              </p>

              <div style={{
                borderTop: '1px solid var(--border-color)',
                paddingTop: '10px',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px',
                fontSize: '0.75rem',
                color: 'var(--text-muted)'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Bot size={13} style={{ color: 'var(--accent-indigo)' }} />
                  <span>Agent: <strong style={{ color: 'var(--text-secondary)' }}>{task.assignedAgentType || 'Deterministic'}</strong></span>
                </div>
                {task.parentTaskId && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <GitBranch size={13} style={{ color: 'var(--accent-blue)' }} />
                    <span className="font-mono">Parent: {task.parentTaskId.substring(0, 8)}...</span>
                  </div>
                )}
                {task.errorMessage && (
                  <div style={{ color: 'var(--accent-rose)', marginTop: '4px' }}>
                    Error: {task.errorMessage}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
