# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:25-alpine AS builder

WORKDIR /app

# Install deps first (better layer caching)
COPY package.json package-lock.json* ./
RUN npm ci --silent

# Build
COPY . .
ARG REACT_APP_API_URL=http://localhost:8080
ARG REACT_APP_KEYCLOAK_URL=http://localhost:8180
ARG REACT_APP_KEYCLOAK_REALM=eclaims
ARG REACT_APP_KEYCLOAK_CLIENT=eclaims-frontend

ENV REACT_APP_API_URL=$REACT_APP_API_URL
ENV REACT_APP_KEYCLOAK_URL=$REACT_APP_KEYCLOAK_URL
ENV REACT_APP_KEYCLOAK_REALM=$REACT_APP_KEYCLOAK_REALM
ENV REACT_APP_KEYCLOAK_CLIENT=$REACT_APP_KEYCLOAK_CLIENT

RUN npm run build

# ── Stage 2: Serve with Nginx ────────────────────────────────────────────────
FROM nginx:1.25-alpine AS runtime

# Copy built assets
COPY --from=builder /app/build /usr/share/nginx/html

# Nginx config for React Router (SPA)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --quiet --tries=1 --spider http://localhost:3000/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
