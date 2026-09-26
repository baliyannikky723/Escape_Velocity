import React from 'react';
import { InvestigationMetrics } from '../../types';
import { CheckSquare, Wrench, ShieldAlert, RefreshCw, FileText, Timer } from 'lucide-react';

interface MetricsGridProps {
  metrics?: InvestigationMetrics | null;
  runtimeSeconds?: number;
}

export const MetricsGrid: React.FC<MetricsGridProps> = ({ metrics, runtimeSeconds = 0 }) => {
  const formatDuration = (secs: number) => {
    if (secs < 60) return `${secs}s`;
    const mins = Math.floor(secs / 60);
    const remainder = secs % 60;
    return `${mins}m ${remainder}s`;
  };

  return (
    <div className="metrics-row">
      <div className="metric-card">
        <div className="metric-header">
          <span>Tasks Executed</span>
          <CheckSquare size={16} style={{ color: 'var(--accent-blue)' }} />
        </div>
        <div className="metric-value">
          {metrics ? `${metrics.completedTasks} / ${metrics.totalTasks}` : '0 / 0'}
        </div>
        <div className="metric-subtext">
          {metrics && metrics.failedTasks > 0 ? `${metrics.failedTasks} failed` : 'Planned autonomously'}
        </div>
      </div>

      <div className="metric-card">
        <div className="metric-header">
          <span>Real Tool Invocations</span>
          <Wrench size={16} style={{ color: 'var(--accent-emerald)' }} />
        </div>
        <div className="metric-value">
          {metrics ? metrics.totalToolCalls : 0}
        </div>
        <div className="metric-subtext">
          GitHub REST API & Gateway
        </div>
      </div>

      <div className="metric-card">
        <div className="metric-header">
          <span>Critic Evaluations</span>
          <ShieldAlert size={16} style={{ color: 'var(--accent-purple)' }} />
        </div>
        <div className="metric-value">
          {metrics ? metrics.totalCriticEvaluations : 0}
        </div>
        <div className="metric-subtext">
          {metrics ? `${metrics.criticAcceptCount} Accepted | ${metrics.criticRejectCount} Rejected` : 'Anti-hallucination check'}
        </div>
      </div>

      <div className="metric-card">
        <div className="metric-header">
          <span>Recovery Attempts</span>
          <RefreshCw size={16} style={{ color: 'var(--accent-amber)' }} />
        </div>
        <div className="metric-value">
          {metrics ? metrics.totalRecoveryAttempts : 0}
        </div>
        <div className="metric-subtext">
          {metrics && metrics.successfulRecoveries > 0 ? `${metrics.successfulRecoveries} recovered via backoff` : 'Retry & fallback policy'}
        </div>
      </div>

      <div className="metric-card">
        <div className="metric-header">
          <span>Evidence Items</span>
          <FileText size={16} style={{ color: 'var(--accent-cyan)' }} />
        </div>
        <div className="metric-value">
          {metrics ? metrics.totalEvidenceCount : 0}
        </div>
        <div className="metric-subtext">
          Grounded Blackboard state
        </div>
      </div>

      <div className="metric-card">
        <div className="metric-header">
          <span>Elapsed Runtime</span>
          <Timer size={16} style={{ color: 'var(--text-secondary)' }} />
        </div>
        <div className="metric-value">
          {formatDuration(metrics ? metrics.runtimeSeconds : runtimeSeconds)}
        </div>
        <div className="metric-subtext">
          Bounded investigation budget
        </div>
      </div>
    </div>
  );
};
