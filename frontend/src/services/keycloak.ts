// src/services/keycloak.ts
import Keycloak from 'keycloak-js';

const runtimeOrigin = typeof window !== 'undefined' ? window.location.origin : '';
const defaultKeycloakUrl = runtimeOrigin ? `${runtimeOrigin}/auth` : 'http://localhost:8180';

const keycloak = new Keycloak({
  url: process.env.REACT_APP_KEYCLOAK_URL || defaultKeycloakUrl,
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'eclaims',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT || 'eclaims-frontend',
});

export default keycloak;
