import React, { useState } from 'react';
import { HumanActionType } from '../../types';
import { ShieldAlert, X, Check, XCircle, ArrowRight, StopCircle, RefreshCw } from 'lucide-react';

interface HitlActionModalProps {
  isOpen: boolean;
  onClose: () => void;
  investigationStatus: string;
  failureReason?: string;
  onSubmitAction: (action: HumanActionType, notes: string) => Promise<void>;
}

export const HitlActionModal: React.FC<HitlActionModalProps> = ({
  isOpen,
  onClose,
  investigationStatus,
  failureReason,
  onSubmitAction,
}) => {
  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const isSensitiveApproval = investigationStatus === 'HUMAN_APPROVAL_REQUIRED';

  const handleAction = async (action: HumanActionType) => {
    setIsSubmitting(true);
    setError(null);
    try {
      await onSubmitAction(action, notes);
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to submit human action');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              backgroundColor: 'var(--accent-amber)',
              color: '#000',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <ShieldAlert size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                {isSensitiveApproval ? 'Sensitive Action Requires Approval' : 'Human-in-the-Loop Review Required'}
              </h3>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Autonomous guardrail engaged • Operator decision required
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={20} />
          </button>
        </div>

        <div style={{
          backgroundColor: 'rgba(245, 158, 11, 0.1)',
          border: '1px solid var(--accent-amber)',
          borderRadius: '8px',
          padding: '14px 16px',
          marginBottom: '20px',
          fontSize: '0.85rem',
          color: 'var(--text-primary)'
        }}>
          <strong>Escalation Reason:</strong>
          <p style={{ marginTop: '4px', color: 'var(--text-secondary)' }}>
            {failureReason ||
              (isSensitiveApproval
                ? 'Proposed action may alter production state (e.g., ROLLBACK_PRODUCTION, UPDATE_DATABASE). Explicit human approval is required.'
                : 'Multiple consecutive critic rejections occurred without sufficient grounded evidence. Manual guidance requested.')}
          </p>
        </div>

        {error && (
          <div style={{
            backgroundColor: 'rgba(244, 63, 94, 0.15)',
            border: '1px solid var(--accent-rose)',
            borderRadius: '8px',
            padding: '10px 14px',
            fontSize: '0.82rem',
            color: 'var(--accent-rose)',
            marginBottom: '16px'
          }}>
            {error}
          </div>
        )}

        <div className="form-group">
          <label className="form-label">Operator Notes / Guidance (Optional)</label>
          <textarea
            className="form-textarea"
            placeholder="Add engineering context, specific commit SHAs to inspect, or approval justification..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            disabled={isSubmitting}
          />
        </div>

        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: '10px',
          flexWrap: 'wrap',
          marginTop: '24px',
          borderTop: '1px solid var(--border-color)',
          paddingTop: '16px'
        }}>
          {isSensitiveApproval ? (
            <>
              <button
                type="button"
                onClick={() => handleAction('REJECT_ACTION')}
                disabled={isSubmitting}
                className="btn btn-danger"
              >
                <XCircle size={15} /> Reject Sensitive Action
              </button>
              <button
                type="button"
                onClick={() => handleAction('APPROVE_ACTION')}
                disabled={isSubmitting}
                className="btn btn-emerald"
              >
                <Check size={15} /> Approve & Execute Action
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => handleAction('STOP')}
                disabled={isSubmitting}
                className="btn btn-danger"
              >
                <StopCircle size={15} /> Stop Investigation
              </button>
              <button
                type="button"
                onClick={() => handleAction('MODIFY_PLAN')}
                disabled={isSubmitting}
                className="btn btn-warning"
              >
                <RefreshCw size={15} /> Modify Plan Direction
              </button>
              <button
                type="button"
                onClick={() => handleAction('CONTINUE')}
                disabled={isSubmitting}
                className="btn btn-primary"
              >
                <ArrowRight size={15} /> Continue Autonomous Search
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
