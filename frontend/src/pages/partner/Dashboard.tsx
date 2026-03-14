// src/pages/partner/Dashboard.tsx
import React from 'react';
export default function PartnerDashboard() {
  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Partner Workshop Dashboard</h1>
      <p className="text-gray-500">Manage vehicle repair work orders and track payments.</p>
      <div className="mt-6 grid grid-cols-3 gap-4">
        {[['Active Work Orders','0','bg-blue-50 text-blue-600'],['Completed This Month','0','bg-green-50 text-green-600'],['Pending Payment','₹0','bg-amber-50 text-amber-600']].map(([l,v,c])=>(
          <div key={l} className="bg-white rounded-xl border border-gray-200 p-5">
            <div className={`text-2xl font-bold ${c.split(' ')[1]}`}>{v}</div>
            <div className="text-sm text-gray-500 mt-1">{l}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
