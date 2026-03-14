// src/pages/customer/ClaimDetails.tsx
import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { claimsApi } from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import { format } from 'date-fns';
import { ArrowLeft, MapPin, Car, FileText, Clock } from 'lucide-react';

const TIMELINE_STEPS = [
  { status: 'SUBMITTED',          label: 'Claim Submitted',         desc: 'Your claim has been received.' },
  { status: 'VALIDATED',          label: 'Under Review',            desc: 'Policy coverage verified.' },
  { status: 'SURVEYOR_ASSIGNED',  label: 'Surveyor Assigned',       desc: 'Surveyor dispatched to assess damage.' },
  { status: 'ASSESSED',           label: 'Assessment Done',         desc: 'Damage report submitted by surveyor.' },
  { status: 'APPROVED',           label: 'Claim Approved',          desc: 'Approved amount confirmed by adjustor.' },
  { status: 'WORKSHOP_ASSIGNED',  label: 'Workshop Assigned',       desc: 'Vehicle booked in for repairs.' },
  { status: 'REPAIR_IN_PROGRESS', label: 'Repairs In Progress',     desc: 'Vehicle is being repaired at workshop.' },
  { status: 'REPAIR_COMPLETED',   label: 'Repairs Completed',       desc: 'Vehicle ready for collection.' },
  { status: 'PAYMENT_PENDING',    label: 'Payment Due',             desc: 'Final payment required.' },
  { status: 'CLOSED',             label: 'Claim Closed',            desc: 'All done! Claim fully settled.' },
];

export default function ClaimDetails() {
  const { id } = useParams<{ id: string }>();

  const { data: claim, isLoading } = useQuery({
    queryKey: ['claim', id],
    queryFn: () => claimsApi.getById(id!),
    enabled: !!id,
  });

  const { data: history } = useQuery({
    queryKey: ['claim-history', id],
    queryFn: () => claimsApi.getHistory(id!),
    enabled: !!id,
  });

  if (isLoading) return <div className="text-center py-12 text-gray-400">Loading claim details...</div>;
  if (!claim)    return <div className="text-center py-12 text-red-500">Claim not found.</div>;

  const currentStepIdx = TIMELINE_STEPS.findIndex(s => s.status === claim.status);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Link to="/claims" className="inline-flex items-center gap-2 text-gray-500 hover:text-gray-800 text-sm">
        <ArrowLeft size={16} /> Back to Claims
      </Link>

      {/* Header */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900 font-mono">{claim.claimNumber}</h1>
            <p className="text-gray-500 text-sm mt-1">
              Submitted {format(new Date(claim.createdAt), 'dd MMM yyyy, HH:mm')}
            </p>
          </div>
          <StatusBadge status={claim.status} />
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
          <div className="flex items-start gap-2">
            <Car size={16} className="text-gray-400 mt-0.5" />
            <div>
              <div className="text-xs text-gray-400">Vehicle</div>
              <div className="text-sm font-medium">{claim.vehicleMake} {claim.vehicleModel}</div>
              <div className="text-xs text-gray-500 font-mono">{claim.vehicleReg}</div>
            </div>
          </div>
          <div className="flex items-start gap-2">
            <FileText size={16} className="text-gray-400 mt-0.5" />
            <div>
              <div className="text-xs text-gray-400">Policy ID</div>
              <div className="text-sm font-medium font-mono">{claim.policyId}</div>
            </div>
          </div>
          <div className="flex items-start gap-2">
            <Clock size={16} className="text-gray-400 mt-0.5" />
            <div>
              <div className="text-xs text-gray-400">Incident Date</div>
              <div className="text-sm font-medium">{format(new Date(claim.incidentDate), 'dd MMM yyyy')}</div>
            </div>
          </div>
          {claim.accidentAddress && (
            <div className="flex items-start gap-2">
              <MapPin size={16} className="text-gray-400 mt-0.5" />
              <div>
                <div className="text-xs text-gray-400">Location</div>
                <div className="text-sm font-medium">{claim.accidentAddress}</div>
              </div>
            </div>
          )}
        </div>

        {claim.approvedAmount != null && (
          <div className="mt-6 bg-green-50 border border-green-200 rounded-lg p-4 grid grid-cols-3 gap-4 text-center">
            <div>
              <div className="text-xs text-gray-500">Approved Amount</div>
              <div className="text-lg font-bold text-green-700">₹{claim.approvedAmount?.toLocaleString()}</div>
            </div>
            <div>
              <div className="text-xs text-gray-500">Deductible</div>
              <div className="text-lg font-bold text-gray-700">₹{(claim as any).deductibleAmount?.toLocaleString()}</div>
            </div>
            <div>
              <div className="text-xs text-gray-500">Your Payment</div>
              <div className="text-lg font-bold text-blue-700">₹{(claim as any).customerContribution?.toLocaleString()}</div>
            </div>
          </div>
        )}
      </div>

      {/* Timeline */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-semibold text-gray-800 mb-6">Claim Progress</h2>
        <div className="space-y-0">
          {TIMELINE_STEPS.filter(s => s.status !== 'REJECTED' && s.status !== 'CANCELLED').map((step, idx) => {
            const done    = idx < currentStepIdx;
            const current = idx === currentStepIdx;
            const future  = idx > currentStepIdx;
            return (
              <div key={step.status} className="flex gap-4">
                <div className="flex flex-col items-center">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0 ${
                    done    ? 'bg-green-500 text-white' :
                    current ? 'bg-blue-600 text-white ring-4 ring-blue-100' :
                    'bg-gray-100 text-gray-400'
                  }`}>
                    {done ? '✓' : idx + 1}
                  </div>
                  {idx < TIMELINE_STEPS.length - 2 && (
                    <div className={`w-0.5 h-8 ${done ? 'bg-green-300' : 'bg-gray-200'}`} />
                  )}
                </div>
                <div className="pb-8">
                  <div className={`text-sm font-semibold ${current ? 'text-blue-700' : done ? 'text-gray-800' : 'text-gray-400'}`}>
                    {step.label}
                  </div>
                  <div className={`text-xs mt-0.5 ${future ? 'text-gray-300' : 'text-gray-500'}`}>
                    {step.desc}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Description */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-semibold text-gray-800 mb-3">Incident Description</h2>
        <p className="text-gray-600 text-sm leading-relaxed">{claim.incidentDescription}</p>
      </div>
    </div>
  );
}
