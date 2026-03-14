// src/pages/surveyor/Assessment.tsx
import React from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { partnersApi } from '../../services/api';

export default function SurveyorAssessment() {
  const { id } = useParams<{ id: string }>();
  const { register, handleSubmit } = useForm();
  const mutation = useMutation({
    mutationFn: (data: any) => partnersApi.submitAssessment({ claimId: id!, ...data }),
    onSuccess: () => toast.success('Assessment submitted!'),
    onError: () => toast.error('Failed to submit'),
  });
  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">Submit Assessment — Claim {id?.slice(0,8)}</h1>
      <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="bg-white rounded-xl border p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Damage Description *</label>
          <textarea {...register('damageDescription', { required: true })} rows={4} className="w-full border rounded-lg px-3 py-2 text-sm"/>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Estimated Repair Cost (₹) *</label>
          <input type="number" {...register('estimatedRepairCost', { required: true })} className="w-full border rounded-lg px-3 py-2 text-sm"/>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Damage Category</label>
          <select {...register('damageCategory')} className="w-full border rounded-lg px-3 py-2 text-sm">
            <option value="MINOR">Minor</option>
            <option value="MODERATE">Moderate</option>
            <option value="SEVERE">Severe</option>
            <option value="TOTAL_LOSS">Total Loss</option>
          </select>
        </div>
        <button type="submit" disabled={mutation.isPending}
          className="w-full py-2.5 bg-green-600 text-white rounded-lg font-semibold text-sm">
          {mutation.isPending ? 'Submitting...' : 'Submit Assessment'}
        </button>
      </form>
    </div>
  );
}
