// src/pages/customer/SubmitClaim.tsx
import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { claimsApi, documentsApi, SubmitClaimRequest } from '../../services/api';
import { MapPin, Upload, X, AlertCircle } from 'lucide-react';

interface FormData extends SubmitClaimRequest {
  files: FileList;
}

export default function SubmitClaim() {
  const navigate = useNavigate();
  const [gpsLoading, setGpsLoading] = useState(false);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<FormData>();

  // Capture GPS location
  const captureGPS = useCallback(() => {
    if (!navigator.geolocation) {
      toast.error('Geolocation not supported by your browser');
      return;
    }
    setGpsLoading(true);
    navigator.geolocation.getCurrentPosition(
      ({ coords: { latitude, longitude } }) => {
        setCoords({ lat: latitude, lng: longitude });
        setValue('accidentLat', latitude);
        setValue('accidentLng', longitude);
        toast.success('GPS location captured!');
        setGpsLoading(false);
      },
      () => {
        toast.error('Could not get GPS location. Please enter address manually.');
        setGpsLoading(false);
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  }, [setValue]);

  // Submit mutation
  const submitMutation = useMutation({
    mutationFn: async (data: SubmitClaimRequest) => {
      const claim = await claimsApi.submit(data);
      // Upload any attached files
      for (const file of selectedFiles) {
        await documentsApi.upload(claim.id, file, 'ACCIDENT_PHOTO');
      }
      return claim;
    },
    onSuccess: (claim) => {
      toast.success(`Claim ${claim.claimNumber} submitted successfully!`);
      navigate(`/claims/${claim.id}`);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.detail ?? 'Failed to submit claim');
    },
  });

  const onSubmit = (data: FormData) => {
    submitMutation.mutate({
      policyId:            data.policyId,
      vehicleReg:          data.vehicleReg,
      vehicleMake:         data.vehicleMake,
      vehicleModel:        data.vehicleModel,
      accidentLat:         coords?.lat,
      accidentLng:         coords?.lng,
      accidentAddress:     data.accidentAddress,
      incidentDate:        data.incidentDate,
      incidentDescription: data.incidentDescription,
    });
  };

  const handleFileDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const files = Array.from(e.dataTransfer.files);
    setSelectedFiles(prev => [...prev, ...files]);
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Submit Insurance Claim</h1>
        <p className="text-gray-500 mt-1">Fill in the details of your accident or loss.</p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">

        {/* Policy + Vehicle */}
        <section className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
          <h2 className="font-semibold text-gray-800 mb-4">Policy & Vehicle Details</h2>
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Policy ID *</label>
              <input
                {...register('policyId', { required: 'Policy ID is required' })}
                placeholder="e.g. POL-2024-001234"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {errors.policyId && <p className="text-red-500 text-xs mt-1">{errors.policyId.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Vehicle Registration *</label>
              <input
                {...register('vehicleReg', { required: 'Vehicle registration is required' })}
                placeholder="e.g. DL01AB1234"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
              />
              {errors.vehicleReg && <p className="text-red-500 text-xs mt-1">{errors.vehicleReg.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Incident Date *</label>
              <input
                type="date"
                {...register('incidentDate', { required: 'Date is required' })}
                max={new Date().toISOString().split('T')[0]}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {errors.incidentDate && <p className="text-red-500 text-xs mt-1">{errors.incidentDate.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Vehicle Make *</label>
              <input
                {...register('vehicleMake', { required: true })}
                placeholder="e.g. Toyota"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Vehicle Model *</label>
              <input
                {...register('vehicleModel', { required: true })}
                placeholder="e.g. Innova"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
        </section>

        {/* Incident Details */}
        <section className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
          <h2 className="font-semibold text-gray-800 mb-4">Incident Details</h2>

          {/* GPS Button */}
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">Accident Location</label>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={captureGPS}
                disabled={gpsLoading}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition"
              >
                <MapPin size={16} />
                {gpsLoading ? 'Getting GPS...' : coords ? 'Update GPS' : 'Capture GPS Location'}
              </button>
              {coords && (
                <div className="flex items-center gap-1 text-sm text-green-700 bg-green-50 px-3 py-1 rounded-lg border border-green-200">
                  <MapPin size={14} />
                  {coords.lat.toFixed(6)}, {coords.lng.toFixed(6)}
                </div>
              )}
            </div>
          </div>

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">Accident Address</label>
            <input
              {...register('accidentAddress')}
              placeholder="e.g. Connaught Place, New Delhi"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description of Incident *</label>
            <textarea
              {...register('incidentDescription', { required: 'Description is required', minLength: { value: 20, message: 'Please provide at least 20 characters' } })}
              rows={4}
              placeholder="Describe what happened — location, cause, damage sustained, other parties involved..."
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
            {errors.incidentDescription && (
              <p className="text-red-500 text-xs mt-1">{errors.incidentDescription.message}</p>
            )}
          </div>
        </section>

        {/* Document Upload */}
        <section className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
          <h2 className="font-semibold text-gray-800 mb-4">Supporting Documents</h2>
          <div
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleFileDrop}
            className={`border-2 border-dashed rounded-xl p-6 text-center transition-colors ${
              dragOver ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-gray-400'
            }`}
          >
            <Upload size={28} className="mx-auto text-gray-400 mb-2" />
            <p className="text-sm text-gray-600">Drag photos or documents here, or</p>
            <label className="mt-2 inline-block cursor-pointer text-blue-600 text-sm font-medium hover:underline">
              browse files
              <input
                type="file"
                accept="image/*,.pdf"
                multiple
                className="hidden"
                onChange={(e) => setSelectedFiles(prev => [...prev, ...Array.from(e.target.files ?? [])])}
              />
            </label>
            <p className="text-xs text-gray-400 mt-1">Accident photos, police report, any other evidence (max 20MB each)</p>
          </div>

          {selectedFiles.length > 0 && (
            <ul className="mt-3 space-y-2">
              {selectedFiles.map((file, i) => (
                <li key={i} className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2 text-sm">
                  <span className="text-gray-700 truncate max-w-xs">{file.name}</span>
                  <button
                    type="button"
                    onClick={() => setSelectedFiles(prev => prev.filter((_, j) => j !== i))}
                    className="text-gray-400 hover:text-red-500 ml-2"
                  >
                    <X size={16} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Notice */}
        <div className="flex gap-3 bg-amber-50 border border-amber-200 rounded-xl p-4 text-sm text-amber-800">
          <AlertCircle size={18} className="flex-shrink-0 mt-0.5" />
          <p>By submitting, you confirm that all information provided is accurate. Fraudulent claims are subject to legal action and policy cancellation.</p>
        </div>

        {/* Submit */}
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={submitMutation.isPending}
            className="flex-1 bg-[#1A73E8] text-white py-3 rounded-xl font-semibold text-sm hover:bg-blue-700 disabled:opacity-50 transition"
          >
            {submitMutation.isPending ? 'Submitting...' : 'Submit Claim'}
          </button>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="px-6 py-3 border border-gray-300 text-gray-700 rounded-xl font-semibold text-sm hover:bg-gray-50 transition"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
