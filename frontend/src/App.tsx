// src/App.tsx
import React from 'react';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import keycloak from './services/keycloak';
import { AuthProvider, useAuth } from './context/AuthContext';
import Layout from './components/common/Layout';

// Pages
import CustomerDashboard  from './pages/customer/Dashboard';
import SubmitClaim        from './pages/customer/SubmitClaim';
import ClaimDetails       from './pages/customer/ClaimDetails';
import ClaimsList         from './pages/customer/ClaimsList';
import AdjustorDashboard  from './pages/adjustor/Dashboard';
import AdjustorClaims     from './pages/adjustor/Claims';
import AdjustorReview     from './pages/adjustor/Review';
import SurveyorDashboard  from './pages/surveyor/Dashboard';
import SurveyorAssessment from './pages/surveyor/Assessment';
import PartnerDashboard   from './pages/partner/Dashboard';
import WorkOrders         from './pages/partner/WorkOrders';
import LoadingSpinner     from './components/common/LoadingSpinner';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 30_000 } },
});

const AppRoutes = () => {
  const { isCustomer, isSurveyor, isAdjustor, isCaseManager } = useAuth();

  if (isCustomer) return (
    <Routes>
      <Route path="/"               element={<CustomerDashboard />} />
      <Route path="/claims"         element={<ClaimsList />} />
      <Route path="/claims/new"     element={<SubmitClaim />} />
      <Route path="/claims/:id"     element={<ClaimDetails />} />
      <Route path="*"               element={<Navigate to="/" replace />} />
    </Routes>
  );

  if (isAdjustor || isCaseManager) return (
    <Routes>
      <Route path="/"               element={<AdjustorDashboard />} />
      <Route path="/claims"         element={<AdjustorClaims />} />
      <Route path="/claims/:id"     element={<AdjustorReview />} />
      <Route path="*"               element={<Navigate to="/" replace />} />
    </Routes>
  );

  if (isSurveyor) return (
    <Routes>
      <Route path="/"               element={<SurveyorDashboard />} />
      <Route path="/assessment/:id" element={<SurveyorAssessment />} />
      <Route path="*"               element={<Navigate to="/" replace />} />
    </Routes>
  );

  return (
    <Routes>
      <Route path="/"               element={<PartnerDashboard />} />
      <Route path="/work-orders"    element={<WorkOrders />} />
      <Route path="*"               element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default function App() {
  return (
    <ReactKeycloakProvider
      authClient={keycloak}
      initOptions={{ onLoad: 'login-required', checkLoginIframe: false }}
      LoadingComponent={<LoadingSpinner message="Authenticating..." />}
    >
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <Toaster position="top-right" />
            <Layout>
              <AppRoutes />
            </Layout>
          </BrowserRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ReactKeycloakProvider>
  );
}
