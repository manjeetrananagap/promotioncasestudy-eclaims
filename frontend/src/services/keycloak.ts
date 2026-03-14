// src/services/keycloak.ts
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: process.env.REACT_APP_KEYCLOAK_URL || 'http://localhost:8180',
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'eclaims',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT || 'eclaims-frontend',
});

export default keycloak;
