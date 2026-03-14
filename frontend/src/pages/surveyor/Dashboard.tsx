// src/pages/surveyor/Dashboard.tsx
import React from 'react';
import { useAuth } from '../../context/AuthContext';
export default function SurveyorDashboard() {
  const { user } = useAuth();
  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Surveyor Dashboard</h1>
      <p className="text-gray-500">Welcome, {user?.name}. Your assigned claims will appear here.</p>
      <div className="mt-6 bg-white rounded-xl border border-gray-200 p-8 text-center text-gray-400">
        No active assignments at this time. You will receive notifications when a claim is assigned to you.
      </div>
    </div>
  );
}
