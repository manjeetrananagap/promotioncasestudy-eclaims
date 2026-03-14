// src/services/api.ts
import axios, { AxiosInstance } from 'axios';
import keycloak from './keycloak';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const api: AxiosInstance = axios.create({ baseURL: BASE_URL });

// Attach JWT to every request
api.interceptors.request.use(async (config) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    } catch {
      keycloak.login();
    }
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) keycloak.login();
    return Promise.reject(err);
  }
);

// ── Claims ─────────────────────────────────────────────────────────────────
export const claimsApi = {
  submit: (data: SubmitClaimRequest) =>
    api.post('/api/v1/claims', data).then(r => r.data),

  list: (page = 0, size = 10) =>
    api.get(`/api/v1/claims?page=${page}&size=${size}`).then(r => r.data),

  getById: (id: string) =>
    api.get(`/api/v1/claims/${id}`).then(r => r.data),

  getHistory: (id: string) =>
    api.get(`/api/v1/claims/${id}/history`).then(r => r.data),

  approve: (id: string, data: ApproveClaimRequest) =>
    api.post(`/api/v1/claims/${id}/approve`, data).then(r => r.data),

  reject: (id: string, reason: string) =>
    api.post(`/api/v1/claims/${id}/reject`, { reason }).then(r => r.data),

  stats: () =>
    api.get('/api/v1/claims/stats').then(r => r.data),
};

// ── Documents ──────────────────────────────────────────────────────────────
export const documentsApi = {
  upload: (claimId: string, file: File, docType: string) => {
    const form = new FormData();
    form.append('file', file);
    form.append('claimId', claimId);
    form.append('documentType', docType);
    return api.post('/api/v1/documents/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data);
  },
  listByClaim: (claimId: string) =>
    api.get(`/api/v1/documents/claim/${claimId}`).then(r => r.data),
};

// ── Partners ───────────────────────────────────────────────────────────────
export const partnersApi = {
  workshops: (lat: number, lng: number) =>
    api.get(`/api/v1/workshops/nearby?lat=${lat}&lng=${lng}&radiusKm=30`).then(r => r.data),

  surveyors: (lat: number, lng: number) =>
    api.get(`/api/v1/surveyors/nearby?lat=${lat}&lng=${lng}&radiusKm=25`).then(r => r.data),

  submitAssessment: (data: AssessmentRequest) =>
    api.post('/api/v1/partners/assessments', data).then(r => r.data),

  updateRepairStatus: (workOrderId: string, status: string) =>
    api.patch(`/api/v1/partners/work-orders/${workOrderId}/status`, { status }).then(r => r.data),
};

// ── Types ──────────────────────────────────────────────────────────────────
export interface SubmitClaimRequest {
  policyId: string;
  vehicleReg: string;
  vehicleMake: string;
  vehicleModel: string;
  accidentLat?: number;
  accidentLng?: number;
  accidentAddress?: string;
  incidentDate: string;
  incidentDescription: string;
}

export interface ApproveClaimRequest {
  approvedAmount: number;
  deductibleAmount: number;
  insurerContribution: number;
  customerContribution: number;
  remarks: string;
}

export interface AssessmentRequest {
  claimId: string;
  damageDescription: string;
  estimatedRepairCost: number;
  damageCategory: string;
  assessmentNotes: string;
}

export interface Claim {
  id: string;
  claimNumber: string;
  policyId: string;
  vehicleReg: string;
  vehicleMake: string;
  vehicleModel: string;
  status: string;
  incidentDate: string;
  incidentDescription: string;
  accidentLat?: number;
  accidentLng?: number;
  accidentAddress?: string;
  approvedAmount?: number;
  estimatedAmount?: number;
  createdAt: string;
}

export default api;
