import { apiClient } from './client';
import {
  Investigation,
  CreateInvestigationPayload,
  InvestigationTask,
  EvidenceItem,
  TimelineEvent,
  TaskGraph,
  AgentActivity,
  ToolExecution,
  RecoverySummary,
  CriticSummary,
  CriticEvaluation,
  InvestigationMetrics,
  InvestigationReport,
  HumanActionRequest,
  HumanActionResponse,
} from '../types';

export const investigationApi = {
  createInvestigation: async (incidentId: string, payload: CreateInvestigationPayload = {}): Promise<Investigation> => {
    const response = await apiClient.post<Investigation>(`/api/v1/incidents/${incidentId}/investigations`, payload);
    return response.data;
  },

  getInvestigation: async (id: string): Promise<Investigation> => {
    const response = await apiClient.get<Investigation>(`/api/v1/investigations/${id}`);
    return response.data;
  },

  startInvestigation: async (id: string): Promise<Investigation> => {
    const response = await apiClient.post<Investigation>(`/api/v1/investigations/${id}/start`);
    return response.data;
  },

  getTasks: async (id: string): Promise<InvestigationTask[]> => {
    const response = await apiClient.get<InvestigationTask[]>(`/api/v1/investigations/${id}/tasks`);
    return response.data;
  },

  getEvidence: async (id: string): Promise<EvidenceItem[]> => {
    const response = await apiClient.get<EvidenceItem[]>(`/api/v1/investigations/${id}/evidence`);
    return response.data;
  },

  getTimeline: async (id: string): Promise<TimelineEvent[]> => {
    const response = await apiClient.get<TimelineEvent[]>(`/api/v1/investigations/${id}/timeline`);
    return response.data;
  },

  getGraph: async (id: string): Promise<TaskGraph> => {
    const response = await apiClient.get<TaskGraph>(`/api/v1/investigations/${id}/graph`);
    return response.data;
  },

  getAgents: async (id: string): Promise<AgentActivity[]> => {
    const response = await apiClient.get<AgentActivity[]>(`/api/v1/investigations/${id}/agents`);
    return response.data;
  },

  getTools: async (id: string): Promise<ToolExecution[]> => {
    const response = await apiClient.get<ToolExecution[]>(`/api/v1/investigations/${id}/tools`);
    return response.data;
  },

  getRecoverySummary: async (id: string): Promise<RecoverySummary> => {
    const response = await apiClient.get<RecoverySummary>(`/api/v1/investigations/${id}/recovery`);
    return response.data;
  },

  getCriticSummary: async (id: string): Promise<CriticSummary> => {
    const response = await apiClient.get<CriticSummary>(`/api/v1/investigations/${id}/critic-summary`);
    return response.data;
  },

  getCriticEvaluations: async (id: string): Promise<CriticEvaluation[]> => {
    const response = await apiClient.get<CriticEvaluation[]>(`/api/v1/investigations/${id}/critic-evaluations`);
    return response.data;
  },

  getMetrics: async (id: string): Promise<InvestigationMetrics> => {
    const response = await apiClient.get<InvestigationMetrics>(`/api/v1/investigations/${id}/metrics`);
    return response.data;
  },

  getReport: async (id: string): Promise<InvestigationReport> => {
    const response = await apiClient.get<InvestigationReport>(`/api/v1/investigations/${id}/report`);
    return response.data;
  },

  submitHumanAction: async (id: string, payload: HumanActionRequest): Promise<HumanActionResponse> => {
    const response = await apiClient.post<HumanActionResponse>(`/api/v1/investigations/${id}/human-actions`, payload);
    return response.data;
  },
};
