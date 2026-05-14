# DR Failover Runbook — eClaims Platform
**Version:** 1.0  
**Owner:** Platform Engineering  
**RTO Target:** 60 minutes | **RPO Target:** 15 minutes  
**Primary Region:** us-central1 | **DR Region:** us-east1

---

## Triggers

Initiate failover when **any** of the following are confirmed:
- GKE Autopilot cluster health checks failing for > 5 minutes
- Cloud SQL primary instance unavailable AND auto-failover not triggered within 10 min
- GCP us-central1 service disruption confirmed via [GCP Status Dashboard](https://status.cloud.google.com)
- PagerDuty P0 alert: `eclaims-region-unavailable`

---

## Pre-Failover Checklist (< 5 min)

- [ ] Confirm primary region is truly unavailable (check GCP console + status page)
- [ ] Notify on-call channel: `#eclaims-incidents` with incident ID
- [ ] Confirm DR GKE cluster (`eclaims-autopilot-dr` in us-east1) is green
- [ ] Confirm Cloud SQL DR replica (`eclaims-postgres-dr`) replication lag < 15 min

---

## Step 1 — Promote Cloud SQL DR Replica (0–10 min)

```bash
# Promote the read replica in us-east1 to a standalone writable instance
gcloud sql instances promote-replica eclaims-postgres-dr \
  --project=PROJECT_ID

# Confirm instance is RUNNABLE and writable
gcloud sql instances describe eclaims-postgres-dr \
  --project=PROJECT_ID \
  --format="value(state,settings.activationPolicy)"
# Expected: RUNNABLE  ALWAYS
```

**Note:** Promotion is irreversible. The DR instance becomes standalone and loses replication.

---

## Step 2 — Update ArgoCD to Point to DR Cluster (10–20 min)

```bash
# Fetch DR cluster credentials
gcloud container clusters get-credentials eclaims-autopilot-dr \
  --region us-east1 \
  --project PROJECT_ID

# Apply the DR ArgoCD application overlay
kubectl apply -f k8s/argocd/application-dr.yaml \
  --context=gke_PROJECT_ID_us-east1_eclaims-autopilot-dr

# Force ArgoCD sync
argocd app sync eclaims-dr --force
argocd app wait eclaims-dr --health --timeout 600
```

---

## Step 3 — Update DNS / Load Balancer (20–30 min)

```bash
# Switch Global HTTP(S) LB backend to DR region NEG
gcloud compute backend-services update eclaims-backend \
  --global \
  --project=PROJECT_ID \
  # Update backend group to DR NEGs — adjust group name as needed
  
# Alternatively, update Cloud DNS weighted routing record:
gcloud dns record-sets update eclaims.example.com \
  --type=A \
  --ttl=60 \
  --zone=eclaims-zone \
  --rrdatas=DR_LOAD_BALANCER_IP \
  --project=PROJECT_ID
```

**Verify:** `curl -I https://eclaims.example.com/actuator/health` → 200 from DR

---

## Step 4 — Validate Services (30–45 min)

```bash
# Check all pods in DR cluster are Running
kubectl get pods -n eclaims-prod \
  --context=gke_PROJECT_ID_us-east1_eclaims-autopilot-dr

# Run smoke tests against DR endpoint
./scripts/smoke-test.sh https://eclaims-dr.example.com

# Verify Kafka connectivity (Cloud Pub/Sub or MSK DR topic mirror)
kubectl exec -it deploy/claims-service -n eclaims-prod -- \
  curl -s http://localhost:8081/actuator/health | jq .components.kafka
```

**Expected:** All services HEALTHY, Kafka connection UP, DB queries returning data.

---

## Step 5 — Notify Stakeholders + Monitoring (45–60 min)

- [ ] Update `#eclaims-incidents` with DR active status and RTO/RPO achieved
- [ ] Email customer-facing status page update
- [ ] Enable enhanced monitoring on DR cluster:
  ```bash
  gcloud monitoring dashboards list --project=PROJECT_ID | grep eclaims-dr
  ```
- [ ] Set PagerDuty escalation policy to DR runbook contact
- [ ] Document incident start time, failover time, data loss window (if any)
- [ ] Schedule post-mortem within 48 hours

---

## Fail-Back Procedure (after primary region restored)

1. Restore primary Cloud SQL from backup / reinstate replication from DR
2. Sync data delta from DR to primary (pg_logical or Cloud DMS)
3. Run primary cluster health validation
4. Gradually shift traffic back (10% → 50% → 100% over 30 min)
5. Re-enable ArgoCD primary application: `argocd app sync eclaims-prod`
6. Decommission DR as primary, revert to replica configuration

---

## Contacts

| Role | Name | PagerDuty |
|------|------|-----------|
| Platform Lead | On-call rotation | P0 eclaims-platform |
| DBA | On-call rotation | P0 eclaims-database |
| Security | On-call rotation | P1 eclaims-security |
