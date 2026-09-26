import React, { useState } from 'react';
import { X, PlusCircle, AlertCircle, Sparkles } from 'lucide-react';
import { CreateIncidentPayload, IncidentSeverity } from '../../types';

interface CreateIncidentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateIncidentPayload) => Promise<void>;
}

export const CreateIncidentModal: React.FC<CreateIncidentModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [title, setTitle] = useState('');
  const [serviceName, setServiceName] = useState('checkout-service');
  const [environment, setEnvironment] = useState('production');
  const [severity, setSeverity] = useState<IncidentSeverity>('P1');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handlePreloadGoldenScenario = (scenario: 'checkout' | 'latency') => {
    if (scenario === 'checkout') {
      setTitle('Checkout API 5xx error rate increased from 2% to 18%');
      setServiceName('checkout-service');
      setEnvironment('production');
      setSeverity('P1');
      setDescription('Checkout service error rate spiked shortly after deployment. Multiple customers report payment failures on cart checkout.');
    } else {
      setTitle('Payment Gateway P99 latency exceeded 1800ms threshold');
      setServiceName('payment-service');
      setEnvironment('production');
      setSeverity('P2');
      setDescription('Third party payment gateway responses degraded. Elevated connection timeouts detected across payment workers.');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim() || !serviceName.trim()) {
      setError('Please fill in all required fields.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        title: title.trim(),
        description: description.trim(),
        serviceName: serviceName.trim(),
        environment: environment.trim(),
        severity,
      });
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create incident');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <PlusCircle size={22} style={{ color: 'var(--accent-blue)' }} />
            <h3 style={{ fontSize: '1.15rem', fontWeight: 600 }}>Create New Engineering Incident</h3>
          </div>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Quick Preload Scenarios for Hackathon Demo */}
        <div style={{
          backgroundColor: 'rgba(56, 189, 248, 0.08)',
          border: '1px dashed var(--accent-blue)',
          borderRadius: '8px',
          padding: '12px',
          marginBottom: '20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.78rem', color: 'var(--accent-blue)', fontWeight: 600, marginBottom: '8px' }}>
            <Sparkles size={14} /> Quick Hackathon Scenarios
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={() => handlePreloadGoldenScenario('checkout')}
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '4px 10px' }}
            >
              Checkout API 5xx Spike (Golden Demo)
            </button>
            <button
              type="button"
              onClick={() => handlePreloadGoldenScenario('latency')}
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '4px 10px' }}
            >
              Payment Gateway Latency (P2)
            </button>
          </div>
        </div>

        {error && (
          <div style={{
            backgroundColor: 'rgba(244, 63, 94, 0.15)',
            border: '1px solid var(--accent-rose)',
            borderRadius: '8px',
            padding: '10px 14px',
            fontSize: '0.82rem',
            color: 'var(--accent-rose)',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <AlertCircle size={16} /> {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Incident Title *</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g., Checkout API error rate spiked to 18%"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label">Severity *</label>
              <select
                className="form-select"
                value={severity}
                onChange={(e) => setSeverity(e.target.value as IncidentSeverity)}
              >
                <option value="P1">P1 - Critical Outage</option>
                <option value="P2">P2 - Major Degradation</option>
                <option value="P3">P3 - Minor Issue</option>
                <option value="P4">P4 - Low Severity</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Service Name *</label>
              <input
                type="text"
                className="form-input"
                value={serviceName}
                onChange={(e) => setServiceName(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Environment *</label>
              <input
                type="text"
                className="form-input"
                value={environment}
                onChange={(e) => setEnvironment(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Description & Symptoms *</label>
            <textarea
              className="form-textarea"
              placeholder="Provide telemetry observations, error logs, and anomaly detection context..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '24px' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Creating Incident...' : 'Create & Investigate'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
