// src/pages/adjustor/Dashboard.tsx
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { claimsApi } from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import { format } from 'date-fns';
import { ClipboardCheck, Clock, AlertTriangle, CheckCircle } from 'lucide-react';

export default function AdjustorDashboard() {
  const { data: page, isLoading } = useQuery({
    queryKey: ['adjustor-claims'],
    queryFn: () => claimsApi.list(0, 20),
  });

  const claims = page?.content ?? [];
  const pendingReview = claims.filter((c: any) => c.status === 'ASSESSED');
  const active        = claims.filter((c: any) => !['CLOSED','REJECTED','CANCELLED'].includes(c.status));

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Adjustor Dashboard</h1>
        <p className="text-gray-500 mt-1">Review and approve insurance claims.</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { icon: <ClipboardCheck size={22} className="text-purple-600" />, label: 'Pending Review', value: pendingReview.length, color: 'bg-purple-50' },
          { icon: <Clock size={22} className="text-blue-600" />,           label: 'Active Claims',  value: active.length,        color: 'bg-blue-50' },
          { icon: <CheckCircle size={22} className="text-green-600" />,    label: 'Approved Today', value: 0,                    color: 'bg-green-50' },
          { icon: <AlertTriangle size={22} className="text-amber-600" />,  label: 'Escalated',     value: 0,                    color: 'bg-amber-50' },
        ].map((s, i) => (
          <div key={i} className="bg-white rounded-xl border border-gray-200 p-5 flex items-center gap-4 shadow-sm">
            <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${s.color}`}>{s.icon}</div>
            <div>
              <div className="text-2xl font-bold text-gray-900">{s.value}</div>
              <div className="text-sm text-gray-500">{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Priority queue */}
      {pendingReview.length > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
          <h3 className="font-semibold text-amber-800 mb-3">⚡ Awaiting Your Review ({pendingReview.length})</h3>
          <div className="space-y-2">
            {pendingReview.map((c: any) => (
              <Link
                key={c.id}
                to={`/claims/${c.id}`}
                className="flex items-center justify-between bg-white rounded-lg p-3 border border-amber-100 hover:border-amber-300 transition"
              >
                <span className="font-mono font-medium text-sm">{c.claimNumber}</span>
                <span className="text-xs text-gray-500">{c.vehicleReg} — {c.vehicleMake} {c.vehicleModel}</span>
                <span className="text-blue-600 text-xs">Review →</span>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* All claims table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="px-6 py-4 border-b border-gray-100 font-semibold text-gray-800">
          All Claims Queue
        </div>
        {isLoading ? (
          <div className="p-8 text-center text-gray-400">Loading...</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['Claim #','Vehicle','Incident Date','Status','Action'].map(h => (
                  <th key={h} className="text-left px-6 py-3 text-gray-500 font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {claims.map((c: any) => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 font-mono font-medium">{c.claimNumber}</td>
                  <td className="px-6 py-4 text-gray-600">{c.vehicleMake} {c.vehicleModel}<br/><span className="text-xs text-gray-400">{c.vehicleReg}</span></td>
                  <td className="px-6 py-4 text-gray-500">{format(new Date(c.incidentDate), 'dd MMM yyyy')}</td>
                  <td className="px-6 py-4"><StatusBadge status={c.status} size="sm" /></td>
                  <td className="px-6 py-4">
                    <Link to={`/claims/${c.id}`} className="text-blue-600 hover:underline text-xs font-medium">
                      {c.status === 'ASSESSED' ? 'Review & Decide →' : 'View →'}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
