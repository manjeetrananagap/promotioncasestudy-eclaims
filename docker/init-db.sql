-- eClaims — PostgreSQL initialisation script
-- Creates all per-service databases

CREATE DATABASE claims_db;
CREATE DATABASE notification_db;
CREATE DATABASE document_db;
CREATE DATABASE partner_db;
CREATE DATABASE workflow_db;
CREATE DATABASE keycloak_db;

-- Grant all to eclaims user
GRANT ALL PRIVILEGES ON DATABASE claims_db       TO eclaims;
GRANT ALL PRIVILEGES ON DATABASE notification_db  TO eclaims;
GRANT ALL PRIVILEGES ON DATABASE document_db      TO eclaims;
GRANT ALL PRIVILEGES ON DATABASE partner_db       TO eclaims;
GRANT ALL PRIVILEGES ON DATABASE workflow_db      TO eclaims;
GRANT ALL PRIVILEGES ON DATABASE keycloak_db      TO eclaims;
