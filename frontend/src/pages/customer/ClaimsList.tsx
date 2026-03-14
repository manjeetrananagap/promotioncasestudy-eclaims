// src/pages/adjustor/Claims.tsx
export { default } from './Dashboard';

// src/pages/customer/ClaimsList.tsx
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { claimsApi } from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import { format } from 'date-fns';
import { PlusCircle } from 'lucide-react';

export default function ClaimsList() {
  const { data: page, isLoading } = useQuery({ queryKey: ['claims'], queryFn: () => claimsApi.list() });
  const claims = page?.content ?? [];
  return (
    <div className="max-w-5xl mx-auto space-y-4">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">My Claims</h1>
        <Link to="/claims/new" className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-blue-700">
          <PlusCircle size={16}/> New Claim
        </Link>
      </div>
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {isLoading ? <div className="p-8 text-center text-gray-400">Loading...</div> : claims.length === 0 ? (
          <div className="p-12 text-center text-gray-400">No claims yet. <Link to="/claims/new" className="text-blue-600 hover:underline">Submit one now →</Link></div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>{['Claim #','Vehicle','Date','Status',''].map(h=><th key={h} className="text-left px-5 py-3 text-gray-500 font-medium text-xs uppercase tracking-wide">{h}</th>)}</tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {claims.map((c:any)=>(
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3 font-mono font-semibold">{c.claimNumber}</td>
                  <td className="px-5 py-3 text-gray-600">{c.vehicleMake} {c.vehicleModel} <span className="text-gray-400 text-xs">({c.vehicleReg})</span></td>
                  <td className="px-5 py-3 text-gray-500">{format(new Date(c.incidentDate),'dd MMM yyyy')}</td>
                  <td className="px-5 py-3"><StatusBadge status={c.status} size="sm"/></td>
                  <td className="px-5 py-3 text-right"><Link to={`/claims/${c.id}`} className="text-blue-600 hover:underline text-xs">View →</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
