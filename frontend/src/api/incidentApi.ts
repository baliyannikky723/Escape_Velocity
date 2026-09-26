import { apiClient } from './client';
import { Incident, CreateIncidentPayload, Investigation } from '../types';

export const incidentApi = {
  getIncidents: async (): Promise<Incident[]> => {
    const response = await apiClient.get<Incident[]>('/api/v1/incidents');
    return response.data;
  },

  getIncidentById: async (id: string): Promise<Incident> => {
    const response = await apiClient.get<Incident>(`/api/v1/incidents/${id}`);
    return response.data;
  },

  createIncident: async (payload: CreateIncidentPayload): Promise<Incident> => {
    const response = await apiClient.post<Incident>('/api/v1/incidents', payload);
    return response.data;
  },

  getIncidentInvestigations: async (incidentId: string): Promise<Investigation[]> => {
    const response = await apiClient.get<Investigation[]>(`/api/v1/incidents/${incidentId}/investigations`);
    return response.data;
  },
};
