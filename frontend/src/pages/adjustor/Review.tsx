// src/pages/adjustor/Review.tsx
import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { claimsApi, documentsApi } from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import { ArrowLeft, CheckCircle, XCircle, FileText } from 'lucide-react';
import { format } from 'date-fns';

interface ApproveForm {
  approvedAmount: number;
  deductibleAmount: number;
  insurerContribution: number;
  customerContribution: number;
  remarks: string;
}

export default function AdjustorReview() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [decision, setDecision] = useState<'approve' | 'reject' | null>(null);

  const { data: claim } = useQuery({ queryKey: ['claim', id], queryFn: () => claimsApi.getById(id!) });
  const { data: docs  } = useQuery({ queryKey: ['docs', id],  queryFn: () => documentsApi.listByClaim(id!) });

  const { register, handleSubmit, watch, formState: { errors } } = useForm<ApproveForm>();
  const approved = watch('approvedAmount');
  const deductible = watch('deductibleAmount');

  const approveMutation = useMutation({
    mutationFn: (data: ApproveForm) => claimsApi.approve(id!, data),
    onSuccess: () => {
      toast.success('Claim approved successfully!');
      queryClient.invalidateQueries({ queryKey: ['claim', id] });
      navigate('/claims');
    },
    onError: (e: any) => toast.error(e.response?.data?.detail ?? 'Approval failed'),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => claimsApi.reject(id!, reason),
    onSuccess: () => {
      toast.success('Claim rejected.');
      navigate('/claims');
    },
    onError: (e: any) => toast.error(e.response?.data?.detail ?? 'Rejection failed'),
  });

  if (!claim) return <div className="text-center py-12 text-gray-400">Loading...</div>;

  const canDecide = claim.status === 'ASSESSED';

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Link to="/claims" className="inline-flex items-center gap-2 text-gray-500 hover:text-gray-800 text-sm">
        <ArrowLeft size={16} /> Back
      </Link>

      {/* Claim header */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-xl font-bold font-mono">{claim.claimNumber}</h1>
            <p className="text-gray-500 text-sm">{claim.vehicleMake} {claim.vehicleModel} · {claim.vehicleReg}</p>
          </div>
          <StatusBadge status={claim.status} />
        </div>

        <div className="grid grid-cols-3 gap-4 mt-4 text-sm">
          <div><span className="text-gray-400 block">Policy</span>{claim.policyId}</div>
          <div><span className="text-gray-400 block">Incident Date</span>{format(new Date(claim.incidentDate), 'dd MMM yyyy')}</div>
          <div><span className="text-gray-400 block">Estimated Cost</span>
            {claim.estimatedAmount ? `₹${claim.estimatedAmount.toLocaleString()}` : 'Pending assessment'}
          </div>
        </div>

        <div className="mt-4 bg-gray-50 rounded-lg p-3">
          <span className="text-xs font-medium text-gray-500">Incident Description</span>
          <p className="text-sm text-gray-700 mt-1">{claim.incidentDescription}</p>
        </div>
      </div>

      {/* Documents */}
      {docs?.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <h2 className="font-semibold text-gray-800 mb-3">Attached Documents</h2>
          <div className="grid grid-cols-2 gap-3">
            {docs.map((d: any) => (
              <a key={d.id} href={d.downloadUrl} target="_blank" rel="noreferrer"
                 className="flex items-center gap-2 p-3 border border-gray-200 rounded-lg hover:border-blue-300 text-sm">
                <FileText size={16} className="text-blue-500" />
                <span className="truncate">{d.originalFilename}</span>
                <span className="ml-auto text-xs text-gray-400">{d.documentType}</span>
              </a>
            ))}
          </div>
        </div>
      )}

      {/* Decision section */}
      {canDecide && !decision && (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <h2 className="font-semibold text-gray-800 mb-4">Make Your Decision</h2>
          <div className="flex gap-4">
            <button
              onClick={() => setDecision('approve')}
              className="flex-1 flex items-center justify-center gap-2 py-3 bg-green-600 text-white rounded-xl font-semibold hover:bg-green-700 transition"
            >
              <CheckCircle size={18} /> Approve Claim
            </button>
            <button
              onClick={() => setDecision('reject')}
              className="flex-1 flex items-center justify-center gap-2 py-3 bg-red-600 text-white rounded-xl font-semibold hover:bg-red-700 transition"
            >
              <XCircle size={18} /> Reject Claim
            </button>
          </div>
        </div>
      )}

      {/* Approve form */}
      {decision === 'approve' && (
        <form
          onSubmit={handleSubmit(d => approveMutation.mutate(d))}
          className="bg-white rounded-xl border border-green-200 shadow-sm p-6 space-y-4"
        >
          <h2 className="font-semibold text-green-800 flex items-center gap-2">
            <CheckCircle size={18} /> Approve Claim
          </h2>
          <div className="grid grid-cols-2 gap-4">
            {[
              { name: 'approvedAmount',       label: 'Approved Amount (₹)' },
              { name: 'deductibleAmount',     label: 'Deductible (₹)' },
              { name: 'insurerContribution',  label: 'Insurer Pays (₹)' },
              { name: 'customerContribution', label: 'Customer Pays (₹)' },
            ].map(({ name, label }) => (
              <div key={name}>
                <label className="block text-sm font-medium text-gray-700 mb-1">{label} *</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  {...register(name as any, { required: 'Required', min: 0 })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-green-400 focus:outline-none"
                />
              </div>
            ))}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Remarks *</label>
            <textarea
              rows={3}
              {...register('remarks', { required: 'Remarks required' })}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-green-400 focus:outline-none resize-none"
              placeholder="Justification for approval amount..."
            />
          </div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={approveMutation.isPending}
              className="flex-1 py-2.5 bg-green-600 text-white rounded-lg font-semibold text-sm hover:bg-green-700 disabled:opacity-50"
            >
              {approveMutation.isPending ? 'Approving...' : 'Confirm Approval'}
            </button>
            <button type="button" onClick={() => setDecision(null)} className="px-4 py-2.5 border border-gray-300 rounded-lg text-sm">
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Reject form */}
      {decision === 'reject' && (
        <div className="bg-white rounded-xl border border-red-200 shadow-sm p-6 space-y-4">
          <h2 className="font-semibold text-red-800 flex items-center gap-2">
            <XCircle size={18} /> Reject Claim
          </h2>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Rejection Reason *</label>
            <textarea
              id="rejectReason"
              rows={4}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:outline-none resize-none"
              placeholder="State the reason for rejection clearly..."
            />
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => {
                const reason = (document.getElementById('rejectReason') as HTMLTextAreaElement).value;
                if (!reason.trim()) { toast.error('Please provide rejection reason'); return; }
                rejectMutation.mutate(reason);
              }}
              disabled={rejectMutation.isPending}
              className="flex-1 py-2.5 bg-red-600 text-white rounded-lg font-semibold text-sm hover:bg-red-700 disabled:opacity-50"
            >
              {rejectMutation.isPending ? 'Rejecting...' : 'Confirm Rejection'}
            </button>
            <button type="button" onClick={() => setDecision(null)} className="px-4 py-2.5 border border-gray-300 rounded-lg text-sm">
              Cancel
            </button>
          </div>
        </div>
      )}

      {!canDecide && claim.status !== 'ASSESSED' && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-4 text-sm text-gray-500 text-center">
          This claim is in <strong>{claim.status}</strong> state — no action required from you at this stage.
        </div>
      )}
    </div>
  );
}
