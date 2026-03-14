// src/components/common/StatusBadge.tsx
import React from 'react';

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  SUBMITTED:          { label: 'Submitted',          className: 'bg-blue-100 text-blue-800' },
  VALIDATED:          { label: 'Validated',          className: 'bg-cyan-100 text-cyan-800' },
  SURVEYOR_ASSIGNED:  { label: 'Surveyor Assigned',  className: 'bg-indigo-100 text-indigo-800' },
  ASSESSED:           { label: 'Assessed',           className: 'bg-purple-100 text-purple-800' },
  APPROVED:           { label: 'Approved',           className: 'bg-green-100 text-green-800' },
  REJECTED:           { label: 'Rejected',           className: 'bg-red-100 text-red-800' },
  WORKSHOP_ASSIGNED:  { label: 'Workshop Assigned',  className: 'bg-orange-100 text-orange-800' },
  REPAIR_IN_PROGRESS: { label: 'Repair In Progress', className: 'bg-yellow-100 text-yellow-800' },
  REPAIR_COMPLETED:   { label: 'Repair Completed',   className: 'bg-teal-100 text-teal-800' },
  PAYMENT_PENDING:    { label: 'Payment Pending',    className: 'bg-amber-100 text-amber-800' },
  CLOSED:             { label: 'Closed',             className: 'bg-gray-100 text-gray-800' },
  CANCELLED:          { label: 'Cancelled',          className: 'bg-red-50 text-red-600' },
};

interface Props { status: string; size?: 'sm' | 'md'; }

export default function StatusBadge({ status, size = 'md' }: Props) {
  const cfg = STATUS_CONFIG[status] ?? { label: status, className: 'bg-gray-100 text-gray-700' };
  const sizeClass = size === 'sm' ? 'text-xs px-2 py-0.5' : 'text-sm px-2.5 py-1';
  return (
    <span className={`inline-flex items-center font-semibold rounded-full ${cfg.className} ${sizeClass}`}>
      {cfg.label}
    </span>
  );
}
