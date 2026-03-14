#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
RED='\033[0;31m';GREEN='\033[0;32m';YELLOW='\033[1;33m';BLUE='\033[0;34m';CYAN='\033[0;36m';BOLD='\033[1m';NC='\033[0m'
log()  { echo -e "${CYAN}[eClaims]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC}     $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}   $*"; }
fail() { echo -e "${RED}[FAIL]${NC}   $*"; exit 1; }

declare -A SERVICES=([api-gateway]="8080" [claims-service]="8081" [notification-service]="8082" [document-service]="8083" [partner-service]="8084" [workflow-service]="8085")
PID_DIR="/tmp/eclaims-pids"; mkdir -p "$PID_DIR"

wait_for() {
  local name=$1 url=$2 max=${3:-60} i=0
  printf "${CYAN}[eClaims]${NC} Waiting for %s " "$name"
  while ! curl -sf "$url" >/dev/null 2>&1; do
    printf "."; sleep 2; i=$((i+2)); [ $i -lt $max ] || { echo; fail "$name did not start in ${max}s"; }
  done; echo; ok "$name is up"
}

start_infra() {
  log "Starting infrastructure..."
  docker compose up -d postgres zookeeper kafka kafka-init keycloak minio minio-init mailhog elasticsearch zeebe operate
  wait_for "Keycloak" "http://localhost:8180/health/ready" 120
  wait_for "MinIO"    "http://localhost:9000/minio/health/live" 30
  wait_for "Zeebe"    "http://localhost:9600/ready" 90
  ok "Infrastructure ready"
  echo -e "\n${BOLD}URLs:${NC}"
  echo -e "  Keycloak:   ${BLUE}http://localhost:8180${NC}  (admin/admin123)"
  echo -e "  MinIO:      ${BLUE}http://localhost:9001${NC}  (eclaims_admin/eclaims_secret_2024)"
  echo -e "  Kafka UI:   ${BLUE}http://localhost:8090${NC}"
  echo -e "  Mailhog:    ${BLUE}http://localhost:8025${NC}"
  echo -e "  Operate:    ${BLUE}http://localhost:8889${NC}\n"
}

build_all() {
  log "Building all modules (Maven)..."
  mvn clean package -DskipTests --batch-mode -q
  ok "Build complete"
}

start_service() {
  local svc=$1 port=${SERVICES[$1]} pid_file="$PID_DIR/$1.pid"
  [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null && { warn "$svc already running"; return; }
  local jar="$ROOT/$svc/target/$svc-1.0.0-POC.jar"
  [ -f "$jar" ] || { warn "JAR missing for $svc — run build first"; return; }
  log "Starting $svc on :$port..."
  java -XX:+UseContainerSupport -XX:MaxRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom -jar "$jar" > "/tmp/eclaims-$svc.log" 2>&1 &
  echo $! > "$pid_file"
  ok "$svc started (PID $!, log: /tmp/eclaims-$svc.log)"
}

start_all() {
  log "Starting all microservices..."
  for svc in claims-service notification-service document-service partner-service workflow-service; do
    start_service "$svc"; sleep 2
  done
  start_service api-gateway
  for svc in "${!SERVICES[@]}"; do
    wait_for "$svc" "http://localhost:${SERVICES[$svc]}/actuator/health" 90
  done
  echo -e "\n${BOLD}${GREEN}All services running!${NC}"
  echo -e "\n${BOLD}Service URLs:${NC}"
  echo -e "  Frontend:   ${BLUE}http://localhost:3000${NC}"
  echo -e "  API GW:     ${BLUE}http://localhost:8080${NC}"
  echo -e "  Claims:     ${BLUE}http://localhost:8081/swagger-ui.html${NC}"
  echo -e "  Partner:    ${BLUE}http://localhost:8084/swagger-ui.html${NC}"
  echo -e "\n${BOLD}Test credentials:${NC}"
  echo -e "  customer1/Customer@123  adjustor1/Adjustor@123  surveyor1/Surveyor@123\n"
}

stop_service() {
  local pid_file="$PID_DIR/$1.pid"
  if [ -f "$pid_file" ]; then
    local pid; pid=$(cat "$pid_file")
    kill -0 "$pid" 2>/dev/null && kill "$pid" && ok "Stopped $1 (PID $pid)" || warn "$1 not running"
    rm -f "$pid_file"
  fi
}

show_status() {
  echo -e "\n${BOLD}═══ Services ═══${NC}"
  for svc in "${!SERVICES[@]}"; do
    local port=${SERVICES[$svc]}
    if curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
      printf "  ${GREEN}● UP${NC}    %-28s :$port\n" "$svc"
    elif [ -f "$PID_DIR/$svc.pid" ] && kill -0 "$(cat "$PID_DIR/$svc.pid")" 2>/dev/null; then
      printf "  ${YELLOW}◐ STARTING${NC} %-28s :$port\n" "$svc"
    else
      printf "  ${RED}○ DOWN${NC}  %-28s :$port\n" "$svc"
    fi
  done
  echo -e "\n${BOLD}═══ Infrastructure ═══${NC}"
  docker compose ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null | grep -v "^NAME" | while IFS= read -r line; do echo "  $line"; done
  echo
}

get_token() {
  local user=${1:-customer1} pass=${2:-Customer@123}
  log "Getting JWT token for $user..."
  local token
  token=$(curl -sf -X POST "http://localhost:8180/realms/eclaims/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=eclaims-frontend&username=$user&password=$pass" | \
    python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null) || fail "Could not get token — is Keycloak up?"
  echo "$token" > /tmp/eclaims-token.txt
  ok "Token saved: /tmp/eclaims-token.txt"
  echo "  export TOKEN=\$(cat /tmp/eclaims-token.txt)"
}

test_claim() {
  log "End-to-end claim test..."
  get_token customer1 "Customer@123"
  TOKEN=$(cat /tmp/eclaims-token.txt)
  log "Submitting FNOL..."
  CLAIM=$(curl -sf -X POST "http://localhost:8080/api/v1/claims" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"policyId":"POL-TEST-001","vehicleReg":"DL01XX9999","vehicleMake":"Toyota","vehicleModel":"Innova","accidentLat":28.6139,"accidentLng":77.2090,"accidentAddress":"CP, New Delhi","incidentDate":"2024-03-14","incidentDescription":"Rear-end collision at traffic signal near Connaught Place."}')
  CLAIM_ID=$(echo "$CLAIM"   | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
  CLAIM_NUM=$(echo "$CLAIM"  | python3 -c "import sys,json; print(json.load(sys.stdin)['claimNumber'])")
  ok "Claim created: $CLAIM_NUM"
  get_token adjustor1 "Adjustor@123"
  ADJ_TOKEN=$(cat /tmp/eclaims-token.txt)
  log "Approving claim as adjustor..."
  curl -sf -X POST "http://localhost:8080/api/v1/claims/$CLAIM_ID/approve" \
    -H "Authorization: Bearer $ADJ_TOKEN" -H "Content-Type: application/json" \
    -d '{"approvedAmount":85000,"deductibleAmount":5000,"insurerContribution":80000,"customerContribution":5000,"remarks":"Approved per policy."}' | python3 -m json.tool
  ok "Test complete! Check http://localhost:8025 for emails and http://localhost:8889 for BPMN"
}

CMD=${1:-help}; shift || true
case "$CMD" in
  infra)       start_infra ;;
  build)       build_all ;;
  start)       build_all && start_infra && start_all ;;
  stop)        for s in "${!SERVICES[@]}"; do stop_service "$s"; done ;;
  restart)     for s in "${!SERVICES[@]}"; do stop_service "$s"; done; sleep 2; start_all ;;
  status)      show_status ;;
  logs)        SVC=${1:-}; [ -n "$SVC" ] && tail -f "/tmp/eclaims-$SVC.log" || tail -f /tmp/eclaims-*.log ;;
  token)       get_token "${1:-customer1}" "${2:-Customer@123}" ;;
  test-claim)  test_claim ;;
  down)        for s in "${!SERVICES[@]}"; do stop_service "$s" 2>/dev/null||true; done; docker compose down -v; ok "Done" ;;
  *)
    echo -e "\n${BOLD}eClaims Runner — Nagarro Software Pvt. Ltd.${NC}"
    echo -e "\nUsage: $0 <command>"
    echo -e "\n${BOLD}Commands:${NC}"
    echo "  infra        Start infrastructure (DB, Kafka, Keycloak, MinIO, Zeebe)"
    echo "  build        Maven build all services"
    echo "  start        Build + infra + start all services"
    echo "  stop         Stop microservices"
    echo "  restart      Restart microservices"
    echo "  status       Show service status"
    echo "  logs [svc]   Tail logs"
    echo "  token [user] Get JWT token"
    echo "  test-claim   End-to-end claim test"
    echo "  down         Stop all + remove volumes"
    echo
    ;;
esac
