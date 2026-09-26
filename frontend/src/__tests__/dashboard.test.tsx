import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Header } from '../components/layout/Header';
import { IncidentList } from '../components/incidents/IncidentList';
import { CreateIncidentModal } from '../components/incidents/CreateIncidentModal';
import { Incident } from '../types';

describe('Header Component', () => {
  it('renders brand name and control plane status', () => {
    render(
      <MemoryRouter>
        <Header activeInvestigationsCount={2} />
      </MemoryRouter>
    );

    expect(screen.getByText('IncidentMind')).toBeInTheDocument();
    expect(screen.getByText(/Control Plane: Active/i)).toBeInTheDocument();
    expect(screen.getByText(/2 Active Investigations/i)).toBeInTheDocument();
  });
});

describe('IncidentList Component', () => {
  const mockIncidents: Incident[] = [
    {
      id: 'inc-123',
      incidentKey: 'INC-000001',
      title: 'Checkout 5xx spike',
      description: 'Checkout error rate spiked to 18%',
      severity: 'P1',
      status: 'INVESTIGATING',
      serviceName: 'checkout-service',
      environment: 'production',
      startedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ];

  it('renders incident key, severity badge, and service name', () => {
    render(
      <MemoryRouter>
        <IncidentList
          incidents={mockIncidents}
          investigationsMap={{}}
          onStartInvestigation={vi.fn()}
          isStartingId={null}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('INC-000001')).toBeInTheDocument();
    expect(screen.getByText('Checkout 5xx spike')).toBeInTheDocument();
    expect(screen.getByText('P1')).toBeInTheDocument();
    expect(screen.getByText('checkout-service')).toBeInTheDocument();
  });
});

describe('CreateIncidentModal Component', () => {
  it('preloads golden hackathon checkout scenario on button click', () => {
    const handleSubmit = vi.fn();
    render(
      <CreateIncidentModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={handleSubmit}
      />
    );

    const goldenBtn = screen.getByText(/Checkout API 5xx Spike/i);
    fireEvent.click(goldenBtn);

    const titleInput = screen.getByDisplayValue(/Checkout API 5xx error rate increased/i);
    expect(titleInput).toBeInTheDocument();
  });
});
