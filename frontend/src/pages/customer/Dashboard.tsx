// src/pages/customer/Dashboard.tsx
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { claimsApi, Claim } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import StatusBadge from '../../components/common/StatusBadge';
import { FileText, PlusCircle, Clock, CheckCircle, AlertCircle, TrendingUp } from 'lucide-react';
import { format } from 'date-fns';

const StatCard = ({ icon, label, value, color }: { icon: ReactNode; label: string; value: number | string; color: string }) => (
  <div className="bg-white rounded-xl border border-gray-200 p-5 flex items-center gap-4 shadow-sm">
    <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${color}`}>
      {icon}
    </div>
    <div>
      <div className="text-2xl font-bold text-gray-900">{value}</div>
      <div className="text-sm text-gray-500">{label}</div>
    </div>
  </div>
);

export default function CustomerDashboard() {
  const { user } = useAuth();

  const { data: claimsPage, isLoading } = useQuery({
    queryKey: ['claims'],
    queryFn: () => claimsApi.list(0, 5),
  });

  const claims: Claim[] = claimsPage?.content ?? [];

  const stats = {
    total:      claims.length,
    active:     claims.filter(c => !['CLOSED','REJECTED','CANCELLED'].includes(c.status)).length,
    closed:     claims.filter(c => c.status === 'CLOSED').length,
    pending:    claims.filter(c => c.status === 'PAYMENT_PENDING').length,
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      {/* Welcome */}
      <div className="bg-gradient-to-r from-[#1A2332] to-[#1A73E8] rounded-2xl p-6 text-white">
        <h1 className="text-2xl font-bold mb-1">Welcome back, {user?.name?.split(' ')[0]} 👋</h1>
        <p className="text-blue-200 text-sm">Track your insurance claims in real time.</p>
        <Link
          to="/claims/new"
          className="mt-4 inline-flex items-center gap-2 bg-white text-[#1A73E8] px-4 py-2 rounded-lg text-sm font-semibold hover:bg-blue-50 transition"
        >
          <PlusCircle size={16} />
          Submit New Claim
        </Link>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={<FileText size={22} className="text-blue-600" />}   label="Total Claims"   value={stats.total}   color="bg-blue-50" />
        <StatCard icon={<Clock size={22} className="text-orange-500" />}    label="Active"         value={stats.active}  color="bg-orange-50" />
        <StatCard icon={<CheckCircle size={22} className="text-green-600" />} label="Closed"       value={stats.closed}  color="bg-green-50" />
        <StatCard icon={<AlertCircle size={22} className="text-amber-600" />} label="Payment Due" value={stats.pending}  color="bg-amber-50" />
      </div>

      {/* Recent claims */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900">Recent Claims</h2>
          <Link to="/claims" className="text-sm text-blue-600 hover:underline">View all →</Link>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-gray-400">Loading claims...</div>
        ) : claims.length === 0 ? (
          <div className="p-8 text-center">
            <FileText size={40} className="mx-auto text-gray-300 mb-3" />
            <p className="text-gray-500 mb-4">No claims yet.</p>
            <Link to="/claims/new" className="text-blue-600 text-sm font-medium hover:underline">
              Submit your first claim →
            </Link>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-6 py-3 text-gray-500 font-medium">Claim #</th>
                <th className="text-left px-6 py-3 text-gray-500 font-medium">Vehicle</th>
                <th className="text-left px-6 py-3 text-gray-500 font-medium">Date</th>
                <th className="text-left px-6 py-3 text-gray-500 font-medium">Status</th>
                <th className="px-6 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {claims.map((claim) => (
                <tr key={claim.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 font-mono font-medium text-gray-900">{claim.claimNumber}</td>
                  <td className="px-6 py-4 text-gray-600">
                    {claim.vehicleMake} {claim.vehicleModel} ({claim.vehicleReg})
                  </td>
                  <td className="px-6 py-4 text-gray-500">
                    {format(new Date(claim.incidentDate), 'dd MMM yyyy')}
                  </td>
                  <td className="px-6 py-4">
                    <StatusBadge status={claim.status} size="sm" />
                  </td>
                  <td className="px-6 py-4 text-right">
                    <Link to={`/claims/${claim.id}`} className="text-blue-600 hover:underline text-xs">
                      View →
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
