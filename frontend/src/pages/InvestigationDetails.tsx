import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { investigationApi } from '../api/investigationApi';
import { incidentApi } from '../api/incidentApi';
import {
  Investigation,
  Incident,
  InvestigationTask,
  EvidenceItem,
  TimelineEvent,
  TaskGraph as TaskGraphType,
  AgentActivity,
  ToolExecution,
  RecoverySummary,
  CriticSummary,
  CriticEvaluation,
  InvestigationMetrics,
  InvestigationReport,
  HumanActionType,
} from '../types';
import { Header } from '../components/layout/Header';
import { InvestigationHeader } from '../components/investigation/InvestigationHeader';
import { MetricsGrid } from '../components/investigation/MetricsGrid';
import { TaskGraph } from '../components/investigation/TaskGraph';
import { InvestigationTimeline } from '../components/investigation/InvestigationTimeline';
import { CriticPanel } from '../components/critic/CriticPanel';
import { EvidenceBoard } from '../components/evidence/EvidenceBoard';
import { ToolExecutionsList } from '../components/tools/ToolExecutionsList';
import { AgentActivityList } from '../components/agents/AgentActivityList';
import { RecoveryAttemptsList } from '../components/recovery/RecoveryAttemptsList';
import { HitlActionModal } from '../components/hitl/HitlActionModal';
import { FinalReportView } from '../components/report/FinalReportView';
import { LoadingState, ErrorState } from '../components/layout/LoadingState';
import {
  GitBranch,
  Clock,
  Layers,
  ShieldAlert,
  Wrench,
  Bot,
  RefreshCw,
  FileBarChart,
  ShieldCheck,
  Play,
} from 'lucide-react';

export const InvestigationDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'graph' | 'evidence' | 'critic' | 'tools' | 'agents' | 'recovery' | 'report'>('graph');
  const [investigation, setInvestigation] = useState<Investigation | null>(null);
  const [incident, setIncident] = useState<Incident | null>(null);
  const [tasks, setTasks] = useState<InvestigationTask[]>([]);
  const [evidence, setEvidence] = useState<EvidenceItem[]>([]);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [graph, setGraph] = useState<TaskGraphType | null>(null);
  const [agents, setAgents] = useState<AgentActivity[]>([]);
  const [tools, setTools] = useState<ToolExecution[]>([]);
  const [recoverySummary, setRecoverySummary] = useState<RecoverySummary | null>(null);
  const [criticSummary, setCriticSummary] = useState<CriticSummary | null>(null);
  const [criticEvaluations, setCriticEvaluations] = useState<CriticEvaluation[]>([]);
  const [metrics, setMetrics] = useState<InvestigationMetrics | null>(null);
  const [report, setReport] = useState<InvestigationReport | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isStepping, setIsStepping] = useState(false);
  const [isHitlModalOpen, setIsHitlModalOpen] = useState(false);

  const fetchAllInvestigationData = useCallback(async (isInitial = false) => {
    if (!id) return;
    try {
      if (isInitial) setLoading(true);
      setError(null);

      const inv = await investigationApi.getInvestigation(id);
      setInvestigation(inv);

      // Fetch related incident
      if (inv.incidentId) {
        try {
          const inc = await incidentApi.getIncidentById(inv.incidentId);
          setIncident(inc);
        } catch {}
      }

      // Fetch tasks, evidence, timeline, graph, agents, tools, recovery, critic, metrics in parallel
      const [
        tasksRes,
        evidenceRes,
        timelineRes,
        graphRes,
        agentsRes,
        toolsRes,
        recoveryRes,
        criticSummaryRes,
        criticEvaluationsRes,
        metricsRes,
      ] = await Promise.all([
        investigationApi.getTasks(id).catch(() => []),
        investigationApi.getEvidence(id).catch(() => []),
        investigationApi.getTimeline(id).catch(() => []),
        investigationApi.getGraph(id).catch(() => null),
        investigationApi.getAgents(id).catch(() => []),
        investigationApi.getTools(id).catch(() => []),
        investigationApi.getRecoverySummary(id).catch(() => null),
        investigationApi.getCriticSummary(id).catch(() => null),
        investigationApi.getCriticEvaluations(id).catch(() => []),
        investigationApi.getMetrics(id).catch(() => null),
      ]);

      setTasks(tasksRes);
      setEvidence(evidenceRes);
      setTimeline(timelineRes);
      setGraph(graphRes);
      setAgents(agentsRes);
      setTools(toolsRes);
      setRecoverySummary(recoveryRes);
      setCriticSummary(criticSummaryRes);
      setCriticEvaluations(criticEvaluationsRes);
      setMetrics(metricsRes);

      // If completed or stopped, fetch report
      if (inv.status === 'COMPLETED' || inv.status === 'WAITING' || inv.status === 'BLOCKED') {
        try {
          const reportRes = await investigationApi.getReport(id);
          setReport(reportRes);
        } catch {}
      }
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to load investigation data');
    } finally {
      if (isInitial) setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchAllInvestigationData(true);

    // Periodic polling (every 3 seconds) until completed or unmounted
    const timer = setInterval(() => {
      if (
        investigation &&
        (investigation.status === 'COMPLETED' ||
          investigation.status === 'FAILED' ||
          investigation.status === 'BLOCKED' ||
          investigation.status === 'HUMAN_REVIEW_REQUIRED' ||
          investigation.status === 'HUMAN_APPROVAL_REQUIRED')
      ) {
        return;
      }
      fetchAllInvestigationData(false);
    }, 3000);

    return () => clearInterval(timer);
  }, [fetchAllInvestigationData, investigation?.status]);

  const handleStartNextStep = async () => {
    if (!id) return;
    setIsStepping(true);
    try {
      await investigationApi.startInvestigation(id);
      await fetchAllInvestigationData(false);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to execute step');
    } finally {
      setIsStepping(false);
    }
  };

  const handleSubmitHumanAction = async (action: HumanActionType, notes: string) => {
    if (!id) return;
    await investigationApi.submitHumanAction(id, { action, notes });
    await fetchAllInvestigationData(false);
  };

  if (loading) {
    return (
      <div className="app-container">
        <Header />
        <main className="main-content">
          <LoadingState message="Loading multi-agent investigation state..." />
        </main>
      </div>
    );
  }

  if (error || !investigation) {
    return (
      <div className="app-container">
        <Header />
        <main className="main-content">
          <ErrorState
            title="Investigation Error"
            message={error || 'Investigation not found'}
            onRetry={() => fetchAllInvestigationData(true)}
          />
        </main>
      </div>
    );
  }

  return (
    <div className="app-container">
      <Header onRefresh={() => fetchAllInvestigationData(false)} />

      <main className="main-content">
        {/* Top Header Card */}
        <InvestigationHeader
          investigation={investigation}
          incident={incident}
          onStartNextStep={handleStartNextStep}
          isStepping={isStepping}
          onOpenHitlModal={() => setIsHitlModalOpen(true)}
          onViewReport={() => setActiveTab('report')}
          hasReport={Boolean(report)}
        />

        {/* Global Metrics Bar */}
        <MetricsGrid metrics={metrics} />

        {/* Navigation Tabs */}
        <div style={{
          display: 'flex',
          gap: '8px',
          borderBottom: '1px solid var(--border-color)',
          paddingBottom: '12px',
          marginBottom: '20px',
          overflowX: 'auto'
        }}>
          <button
            onClick={() => setActiveTab('graph')}
            className={`btn ${activeTab === 'graph' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <GitBranch size={14} /> Task DAG & Event Stream
          </button>

          <button
            onClick={() => setActiveTab('evidence')}
            className={`btn ${activeTab === 'evidence' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <Layers size={14} /> Blackboard Evidence ({evidence.length})
          </button>

          <button
            onClick={() => setActiveTab('critic')}
            className={`btn ${activeTab === 'critic' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <ShieldCheck size={14} /> Critic Verdicts ({criticEvaluations.length})
          </button>

          <button
            onClick={() => setActiveTab('tools')}
            className={`btn ${activeTab === 'tools' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <Wrench size={14} /> Real Tool Calls ({tools.length})
          </button>

          <button
            onClick={() => setActiveTab('agents')}
            className={`btn ${activeTab === 'agents' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <Bot size={14} /> Specialized Agents ({agents.length})
          </button>

          <button
            onClick={() => setActiveTab('recovery')}
            className={`btn ${activeTab === 'recovery' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
          >
            <RefreshCw size={14} /> Recovery & Resilience ({recoverySummary?.totalAttempts || 0})
          </button>

          {report && (
            <button
              onClick={() => setActiveTab('report')}
              className={`btn ${activeTab === 'report' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ fontSize: '0.8rem', padding: '6px 14px', borderColor: 'var(--accent-emerald)', color: 'var(--accent-emerald)' }}
            >
              <FileBarChart size={14} /> Final Report
            </button>
          )}
        </div>

        {/* Tab Content Display */}
        {activeTab === 'graph' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.4fr) minmax(0, 1fr)', gap: '24px' }}>
            <div className="panel">
              <div className="panel-header">
                <div className="panel-title">
                  <GitBranch size={18} style={{ color: 'var(--accent-blue)' }} />
                  <span>Dynamic Task Execution Graph (DAG)</span>
                </div>
              </div>
              <TaskGraph graph={graph} tasks={tasks} />
            </div>

            <div className="panel">
              <div className="panel-header">
                <div className="panel-title">
                  <Clock size={18} style={{ color: 'var(--accent-purple)' }} />
                  <span>Autonomous Event Stream</span>
                </div>
              </div>
              <InvestigationTimeline events={timeline} />
            </div>
          </div>
        )}

        {activeTab === 'evidence' && (
          <div className="panel">
            <div className="panel-header">
              <div className="panel-title">
                <Layers size={18} style={{ color: 'var(--accent-cyan)' }} />
                <span>Shared Blackboard & Grounded Evidence</span>
              </div>
            </div>
            <EvidenceBoard evidence={evidence} />
          </div>
        )}

        {activeTab === 'critic' && (
          <div className="panel">
            <div className="panel-header">
              <div className="panel-title">
                <ShieldCheck size={18} style={{ color: 'var(--accent-purple)' }} />
                <span>LLM Critic Validation & Anti-Hallucination Guardrails</span>
              </div>
            </div>
            <CriticPanel summary={criticSummary} evaluations={criticEvaluations} />
          </div>
        )}

        {activeTab === 'tools' && (
          <div className="panel">
            <div className="panel-header">
              <div className="panel-title">
                <Wrench size={18} style={{ color: 'var(--accent-emerald)' }} />
                <span>Tool Gateway & Real GitHub REST API Invocations</span>
              </div>
            </div>
            <ToolExecutionsList tools={tools} />
          </div>
        )}

        {activeTab === 'agents' && (
          <div className="panel">
            <div className="panel-header">
              <div className="panel-title">
                <Bot size={18} style={{ color: 'var(--accent-indigo)' }} />
                <span>Specialized Multi-Agent Execution Registry</span>
              </div>
            </div>
            <AgentActivityList agents={agents} />
          </div>
        )}

        {activeTab === 'recovery' && (
          <div className="panel">
            <div className="panel-header">
              <div className="panel-title">
                <RefreshCw size={18} style={{ color: 'var(--accent-amber)' }} />
                <span>Recovery & Resilience Engine (Retry / Backoff / Replan)</span>
              </div>
            </div>
            <RecoveryAttemptsList recovery={recoverySummary} />
          </div>
        )}

        {activeTab === 'report' && report && (
          <FinalReportView report={report} onClose={() => setActiveTab('graph')} />
        )}
      </main>

      <HitlActionModal
        isOpen={isHitlModalOpen}
        onClose={() => setIsHitlModalOpen(false)}
        investigationStatus={investigation.status}
        failureReason={investigation.failureReason}
        onSubmitAction={handleSubmitHumanAction}
      />
    </div>
  );
};
