import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { incidentApi } from '../api/incidentApi';
import { investigationApi } from '../api/investigationApi';
import { Incident, Investigation, CreateIncidentPayload } from '../types';
import { Header } from '../components/layout/Header';
import { IncidentList } from '../components/incidents/IncidentList';
import { CreateIncidentModal } from '../components/incidents/CreateIncidentModal';
import { LoadingState, ErrorState } from '../components/layout/LoadingState';
import { PlusCircle, AlertOctagon, CheckCircle2, ShieldAlert, Wrench, RefreshCw } from 'lucide-react';

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [investigationsMap, setInvestigationsMap] = useState<Record<string, Investigation | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isStartingId, setIsStartingId] = useState<string | null>(null);

  const fetchIncidentsAndInvestigations = useCallback(async () => {
    try {
      setError(null);
      const incs = await incidentApi.getIncidents();
      setIncidents(incs);

      const map: Record<string, Investigation | null> = {};
      await Promise.all(
        incs.map(async (inc) => {
          try {
            const invs = await incidentApi.getIncidentInvestigations(inc.id);
            map[inc.id] = invs.length > 0 ? invs[0] : null;
          } catch {
            map[inc.id] = null;
          }
        })
      );
      setInvestigationsMap(map);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to connect to Spring Boot Control Plane');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchIncidentsAndInvestigations();
  }, [fetchIncidentsAndInvestigations]);

  const handleCreateIncident = async (payload: CreateIncidentPayload) => {
    const createdIncident = await incidentApi.createIncident(payload);
    // Immediately create and start investigation
    try {
      const inv = await investigationApi.createInvestigation(createdIncident.id, {
        objective: `Investigate root cause for ${createdIncident.title}`,
        maxTasks: 10,
        maxRetriesPerTask: 2,
        maxRuntimeSeconds: 120,
      });
      navigate(`/investigations/${inv.id}`);
    } catch {
      await fetchIncidentsAndInvestigations();
    }
  };

  const handleStartInvestigation = async (incidentId: string) => {
    setIsStartingId(incidentId);
    try {
      const incident = incidents.find((inc) => inc.id === incidentId);
      const title = incident ? incident.title : 'Incident';
      const inv = await investigationApi.createInvestigation(incidentId, {
        objective: `Investigate root cause and causal chain for ${title}`,
        maxTasks: 10,
        maxRetriesPerTask: 2,
        maxRuntimeSeconds: 120,
      });
      // Trigger start
      await investigationApi.startInvestigation(inv.id);
      navigate(`/investigations/${inv.id}`);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to start investigation');
    } finally {
      setIsStartingId(null);
    }
  };

  const activeInvestigationsCount = Object.values(investigationsMap).filter(
    (inv) => inv && (inv.status === 'RUNNING' || inv.status === 'WAITING')
  ).length;

  const completedCount = Object.values(investigationsMap).filter(
    (inv) => inv && inv.status === 'COMPLETED'
  ).length;

  const criticalP1Count = incidents.filter((i) => i.severity === 'P1').length;

  return (
    <div className="app-container">
      <Header
        activeInvestigationsCount={activeInvestigationsCount}
        onRefresh={fetchIncidentsAndInvestigations}
        isRefreshing={loading}
      />

      <main className="main-content">
        {/* Top Metric Cards */}
        <div className="metrics-row">
          <div className="metric-card">
            <div className="metric-header">
              <span>Active Outages</span>
              <AlertOctagon size={16} style={{ color: 'var(--accent-rose)' }} />
            </div>
            <div className="metric-value" style={{ color: criticalP1Count > 0 ? 'var(--accent-rose)' : 'var(--text-primary)' }}>
              {criticalP1Count}
            </div>
            <div className="metric-subtext">P1 Critical Incidents</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span>Active AI Investigations</span>
              <ShieldAlert size={16} style={{ color: 'var(--accent-blue)' }} />
            </div>
            <div className="metric-value" style={{ color: 'var(--accent-blue)' }}>
              {activeInvestigationsCount}
            </div>
            <div className="metric-subtext">Autonomous multi-agent loops</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span>Resolved Investigations</span>
              <CheckCircle2 size={16} style={{ color: 'var(--accent-emerald)' }} />
            </div>
            <div className="metric-value" style={{ color: 'var(--accent-emerald)' }}>
              {completedCount}
            </div>
            <div className="metric-subtext">Concluded with verified facts</div>
          </div>

          <div className="metric-card">
            <div className="metric-header">
              <span>Total Engineering Incidents</span>
              <Wrench size={16} style={{ color: 'var(--accent-indigo)' }} />
            </div>
            <div className="metric-value">
              {incidents.length}
            </div>
            <div className="metric-subtext">Registered in Control Plane</div>
          </div>
        </div>

        {/* Panel with Incident List */}
        <div className="panel">
          <div className="panel-header">
            <div className="panel-title">
              <ShieldAlert size={20} style={{ color: 'var(--accent-blue)' }} />
              <span>Production Incidents & Investigation Registry</span>
            </div>

            <button onClick={() => setIsModalOpen(true)} className="btn btn-primary">
              <PlusCircle size={16} /> New Incident
            </button>
          </div>

          {loading ? (
            <LoadingState message="Fetching production incidents and investigation state..." />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchIncidentsAndInvestigations} />
          ) : (
            <IncidentList
              incidents={incidents}
              investigationsMap={investigationsMap}
              onStartInvestigation={handleStartInvestigation}
              isStartingId={isStartingId}
            />
          )}
        </div>
      </main>

      <CreateIncidentModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleCreateIncident}
      />
    </div>
  );
};
