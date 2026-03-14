// src/components/common/LoadingSpinner.tsx
import React from 'react';

interface Props { message?: string; }

export default function LoadingSpinner({ message = 'Loading...' }: Props) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="text-center">
        <div className="w-12 h-12 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin mx-auto mb-4" />
        <p className="text-gray-500 text-sm">{message}</p>
        <p className="text-[#EA4335] font-bold mt-4 text-lg">nagarro</p>
      </div>
    </div>
  );
}
