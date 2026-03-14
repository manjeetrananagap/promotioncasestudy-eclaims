// src/context/AuthContext.tsx
import React, { createContext, useContext, ReactNode } from 'react';
import { useKeycloak } from '@react-keycloak/web';

interface AuthContextType {
  user: { name: string; email: string; userId: string } | null;
  roles: string[];
  isCustomer: boolean;
  isSurveyor: boolean;
  isAdjustor: boolean;
  isCaseManager: boolean;
  isAdmin: boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const { keycloak } = useKeycloak();

  const roles: string[] = keycloak.tokenParsed?.realm_access?.roles ?? [];
  const name = keycloak.tokenParsed?.name ?? '';
  const email = keycloak.tokenParsed?.email ?? '';
  const userId = keycloak.tokenParsed?.sub ?? '';

  const value: AuthContextType = {
    user: keycloak.authenticated ? { name, email, userId } : null,
    roles,
    isCustomer:    roles.includes('CUSTOMER'),
    isSurveyor:    roles.includes('SURVEYOR'),
    isAdjustor:    roles.includes('ADJUSTOR'),
    isCaseManager: roles.includes('CASE_MANAGER'),
    isAdmin:       roles.includes('ADMIN'),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => useContext(AuthContext);
