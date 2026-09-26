import React from 'react';
import { Loader2, AlertCircle, RefreshCw } from 'lucide-react';

interface LoadingStateProps {
  message?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({ message = 'Loading investigation data...' }) => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '48px 24px',
      gap: '16px',
      color: 'var(--text-secondary)'
    }}>
      <Loader2 size={32} className="spin-animation" style={{ color: 'var(--accent-blue)' }} />
      <p style={{ fontSize: '0.9rem', fontWeight: 500 }}>{message}</p>
      <style>{`
        .spin-animation {
          animation: spin 1s linear infinite;
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};

interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Unable to Load Data',
  message,
  onRetry,
}) => {
  return (
    <div style={{
      backgroundColor: 'rgba(244, 63, 94, 0.1)',
      border: '1px solid rgba(244, 63, 94, 0.3)',
      borderRadius: '12px',
      padding: '24px',
      margin: '16px 0',
      display: 'flex',
      alignItems: 'flex-start',
      gap: '16px',
      color: 'var(--text-primary)'
    }}>
      <AlertCircle size={24} style={{ color: 'var(--accent-rose)', flexShrink: 0, marginTop: '2px' }} />
      <div style={{ flex: 1 }}>
        <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--accent-rose)', marginBottom: '4px' }}>
          {title}
        </h4>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: onRetry ? '12px' : '0' }}>
          {message}
        </p>
        {onRetry && (
          <button onClick={onRetry} className="btn btn-secondary" style={{ fontSize: '0.78rem', padding: '4px 10px' }}>
            <RefreshCw size={12} /> Retry
          </button>
        )}
      </div>
    </div>
  );
};
