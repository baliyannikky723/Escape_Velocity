// Incident Types
export type IncidentSeverity = 'P1' | 'P2' | 'P3' | 'P4';
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED' | 'CLOSED';

export interface Incident {
  id: string;
  incidentKey: string;
  title: string;
  description: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  serviceName: string;
  environment: string;
  startedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateIncidentPayload {
  title: string;
  description: string;
  severity: IncidentSeverity;
  serviceName: string;
  environment: string;
}

// Investigation Types
export type InvestigationStatus = 'CREATED' | 'RUNNING' | 'WAITING' | 'COMPLETED' | 'FAILED' | 'BLOCKED' | 'HUMAN_REVIEW_REQUIRED' | 'HUMAN_APPROVAL_REQUIRED';

export interface Investigation {
  id: string;
  incidentId: string;
  status: InvestigationStatus;
  maxTasks: number;
  maxRetriesPerTask: number;
  maxRuntimeSeconds: number;
  startedAt?: string;
  completedAt?: string;
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInvestigationPayload {
  maxTasks?: number;
  maxRetriesPerTask?: number;
  maxRuntimeSeconds?: number;
}

// Task Types
export type TaskStatus = 'PENDING' | 'READY' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED' | 'BLOCKED';
export type TaskPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface InvestigationTask {
  id: string;
  investigationId: string;
  parentTaskId?: string;
  taskType: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  assignedAgentType?: string;
  retryCount: number;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
}

// Evidence Types
export type EvidenceType = 'FACT' | 'SUPPORTED_FINDING' | 'HYPOTHESIS' | 'UNKNOWN' | 'UNRESOLVED' | 'CORRELATION';

export interface EvidenceItem {
  id: string;
  investigationId: string;
  taskId?: string;
  sourceType: string;
  claim: string;
  confidenceScore?: number;
  payload?: Record<string, any>;
  createdAt: string;
}

// Timeline Event
export interface TimelineEvent {
  eventId: string;
  investigationId: string;
  eventType: string;
  actorType: string;
  actorId: string;
  title: string;
  description: string;
  occurredAt: string;
  data?: Record<string, any>;
}

// Task Graph / DAG
export interface GraphNode {
  id: string;
  label: string;
  taskType: string;
  status: TaskStatus;
  priority: TaskPriority;
  assignedAgent?: string;
  durationMs?: number;
  criticVerdict?: string;
}

export interface GraphEdge {
  from: string;
  to: string;
  label?: string;
}

export interface TaskGraph {
  investigationId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

// Agent Activity
export interface AgentActivity {
  agentRunId: string;
  investigationId: string;
  taskId: string;
  agentType: string;
  status: string;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
  outputSummary?: string;
  toolCallsCount: number;
}

// Tool Execution
export interface ToolExecution {
  toolCallId: string;
  investigationId: string;
  taskId?: string;
  toolName: string;
  status: string;
  httpStatus?: number;
  durationMs?: number;
  startedAt: string;
  completedAt?: string;
  errorClassification?: string;
  requestPayload?: string;
  responsePayload?: string;
}

// Recovery Summary
export interface RecoverySummary {
  totalAttempts: number;
  successfulRecoveries: number;
  exhaustedRecoveries: number;
  retryCount: number;
  fallbackCount: number;
  replanCount: number;
  recentAttempts?: Array<{
    id: string;
    taskId: string;
    toolName: string;
    attemptNumber: number;
    errorType: string;
    strategy: string;
    status: string;
    occurredAt: string;
  }>;
}

// Critic Summary
export interface CriticSummary {
  totalEvaluations: number;
  acceptedCount: number;
  rejectedCount: number;
  inconclusiveCount: number;
  humanApprovalCount: number;
  rejectedTaskIds: string[];
  rejectionReasons: string[];
  replanTriggeredByCritic: boolean;
}

export interface CriticEvaluation {
  id: string;
  investigationId: string;
  taskId: string;
  decision: 'ACCEPT' | 'REJECT' | 'INCONCLUSIVE' | 'HUMAN_APPROVAL_REQUIRED';
  reason: string;
  supportedClaims: string[];
  unsupportedClaims: string[];
  missingEvidence: string[];
  recommendedFollowUp: string[];
  requiresHumanReview: boolean;
  proposedAction?: string;
  createdAt: string;
}

// Investigation Metrics
export interface InvestigationMetrics {
  investigationId: string;
  totalTasks: number;
  completedTasks: number;
  failedTasks: number;
  skippedTasks: number;
  totalAgentRuns: number;
  totalToolCalls: number;
  failedToolCalls: number;
  totalEvidenceCount: number;
  totalCriticEvaluations: number;
  criticAcceptCount: number;
  criticRejectCount: number;
  totalRecoveryAttempts: number;
  successfulRecoveries: number;
  runtimeSeconds: number;
}

// Final Investigation Report
export interface InvestigationReport {
  investigationId: string;
  incidentKey: string;
  incidentTitle: string;
  serviceName: string;
  environment: string;
  investigationStatus: InvestigationStatus;
  executiveSummary: string;
  keyFacts: string[];
  supportedFindings: string[];
  hypotheses: string[];
  unknowns: string[];
  unresolved: string[];
  totalTasksExecuted: number;
  totalAgentRuns: number;
  totalToolCalls: number;
  totalEvidenceCount: number;
  criticAcceptCount: number;
  criticRejectCount: number;
  recoveryCount: number;
  stoppingReason: string;
  generatedAt: string;
}

// Human Action
export type HumanActionType = 'CONTINUE' | 'APPROVE_ACTION' | 'REJECT_ACTION' | 'MODIFY_PLAN' | 'STOP';

export interface HumanActionRequest {
  action: HumanActionType;
  notes?: string;
  approvedAction?: string;
  modifiedDirections?: string[];
}

export interface HumanActionResponse {
  investigationId: string;
  action: string;
  status: string;
  message: string;
  processedAt?: string;
}
