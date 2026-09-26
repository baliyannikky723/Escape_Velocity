import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { TaskGraph } from '../components/investigation/TaskGraph';
import { CriticPanel } from '../components/critic/CriticPanel';
import { EvidenceBoard } from '../components/evidence/EvidenceBoard';
import { HitlActionModal } from '../components/hitl/HitlActionModal';
import { FinalReportView } from '../components/report/FinalReportView';
import { InvestigationTask, CriticEvaluation, EvidenceItem, InvestigationReport } from '../types';

describe('TaskGraph Component', () => {
  const mockTasks: InvestigationTask[] = [
    {
      id: 'task-1',
      investigationId: 'inv-1',
      taskType: 'INCIDENT_TRIAGE',
      title: 'Triage incident scope',
      status: 'COMPLETED',
      priority: 'HIGH',
      assignedAgentType: 'incident-triage-agent',
      retryCount: 0,
      createdAt: new Date().toISOString(),
    },
    {
      id: 'task-2',
      investigationId: 'inv-1',
      parentTaskId: 'task-1',
      taskType: 'CHANGE_ANALYSIS',
      title: 'Analyze recent repository commits',
      status: 'RUNNING',
      priority: 'HIGH',
      assignedAgentType: 'change-analysis-agent',
      retryCount: 0,
      createdAt: new Date().toISOString(),
    },
  ];

  it('renders task types and agent assignments', () => {
    render(<TaskGraph tasks={mockTasks} graph={null} />);

    expect(screen.getByText('INCIDENT_TRIAGE')).toBeInTheDocument();
    expect(screen.getByText('CHANGE_ANALYSIS')).toBeInTheDocument();
    expect(screen.getByText('incident-triage-agent')).toBeInTheDocument();
    expect(screen.getByText('change-analysis-agent')).toBeInTheDocument();
  });
});

describe('CriticPanel Component', () => {
  const mockEvaluations: CriticEvaluation[] = [
    {
      id: 'crit-1',
      investigationId: 'inv-1',
      taskId: 'task-2',
      decision: 'REJECT',
      reason: 'UNSUPPORTED_CAUSAL_CLAIM: Temporal correlation does not prove causality without runtime traces.',
      supportedClaims: ['Commit occurred 8 min before outage'],
      unsupportedClaims: ['Commit definitely caused outage'],
      missingEvidence: ['Application stack traces', 'PR review data'],
      recommendedFollowUp: ['INVESTIGATE_PULL_REQUESTS'],
      requiresHumanReview: false,
      createdAt: new Date().toISOString(),
    },
  ];

  it('renders REJECT decision, reason, and follow-up recommendation', () => {
    render(<CriticPanel evaluations={mockEvaluations} summary={null} />);

    expect(screen.getByText(/REJECTED \(UNGROUNDED\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Temporal correlation does not prove causality/i)).toBeInTheDocument();
    expect(screen.getByText(/INVESTIGATE_PULL_REQUESTS/i)).toBeInTheDocument();
  });
});

describe('EvidenceBoard Component', () => {
  const mockEvidence: EvidenceItem[] = [
    {
      id: 'ev-1',
      investigationId: 'inv-1',
      sourceType: 'GITHUB',
      claim: 'Commit abc123 pushed 8 minutes prior to incident',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'ev-2',
      investigationId: 'inv-1',
      sourceType: 'AGENT_INFERENCE',
      claim: 'Recent deployment hypothesis may explain checkout error surge',
      createdAt: new Date().toISOString(),
    },
  ];

  it('visually distinguishes VERIFIED FACT from HYPOTHESIS', () => {
    render(<EvidenceBoard evidence={mockEvidence} />);

    expect(screen.getByText('VERIFIED FACT')).toBeInTheDocument();
    expect(screen.getByText('HYPOTHESIS')).toBeInTheDocument();
    expect(screen.getByText(/Commit abc123 pushed/i)).toBeInTheDocument();
  });
});

describe('HitlActionModal Component', () => {
  it('renders human review actions and triggers submission', async () => {
    const handleAction = vi.fn().mockResolvedValue(undefined);
    render(
      <HitlActionModal
        isOpen={true}
        onClose={vi.fn()}
        investigationStatus="HUMAN_REVIEW_REQUIRED"
        failureReason="3 consecutive critic rejections"
        onSubmitAction={handleAction}
      />
    );

    expect(screen.getByText(/Human-in-the-Loop Review Required/i)).toBeInTheDocument();
    const continueBtn = screen.getByText(/Continue Autonomous Search/i);
    await fireEvent.click(continueBtn);

    expect(handleAction).toHaveBeenCalledWith('CONTINUE', '');
  });
});

describe('FinalReportView Component', () => {
  const mockReport: InvestigationReport = {
    investigationId: 'inv-1',
    incidentKey: 'INC-000001',
    incidentTitle: 'Checkout Outage',
    serviceName: 'checkout-service',
    environment: 'production',
    investigationStatus: 'COMPLETED',
    executiveSummary: 'Investigation confirmed deployment correlation and isolated root cause.',
    keyFacts: ['Deployment occurred at 14:02 UTC', 'Error rate spiked to 18% at 14:10 UTC'],
    supportedFindings: ['Payment worker thread pool exhausted'],
    hypotheses: [],
    unknowns: [],
    unresolved: [],
    totalTasksExecuted: 4,
    totalAgentRuns: 3,
    totalToolCalls: 2,
    totalEvidenceCount: 5,
    criticAcceptCount: 2,
    criticRejectCount: 1,
    recoveryCount: 1,
    stoppingReason: 'STOP_SUCCESS (Evidence verified)',
    generatedAt: new Date().toISOString(),
  };

  it('renders executive summary, facts, and execution stats', () => {
    render(<FinalReportView report={mockReport} />);

    expect(screen.getByText(/Final Autonomous Investigation Report/i)).toBeInTheDocument();
    expect(screen.getByText(/Deployment occurred at 14:02 UTC/i)).toBeInTheDocument();
    expect(screen.getByText(/STOP_SUCCESS \(Evidence verified\)/i)).toBeInTheDocument();
  });
});
