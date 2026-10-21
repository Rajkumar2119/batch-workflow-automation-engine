#!/usr/bin/env bash
#
# Control-M wrapper for the batch engine.
#
# Control-M runs this as an OS (Command) job. It starts dailyFeedJob through the REST API,
# polls until the run finishes and exits 0 on COMPLETED, non-zero otherwise, so Control-M
# marks the job Ended OK / Ended Not OK and its normal rerun and alerting rules apply.
#
# Usage: run_feed_job.sh <ODATE yyyymmdd> [force]
#   ODATE   Control-M order date, passed as %%$ODATE
#   force   optional; re-run a date that already completed
#
# Environment:
#   ENGINE_URL          default http://localhost:8080
#   POLL_SECONDS        default 15
#   TIMEOUT_SECONDS     default 7200

set -euo pipefail

ODATE="${1:?usage: run_feed_job.sh <yyyymmdd> [force]}"
FORCE="false"
if [[ "${2:-}" == "force" ]]; then
  FORCE="true"
fi

ENGINE_URL="${ENGINE_URL:-http://localhost:8080}"
POLL_SECONDS="${POLL_SECONDS:-15}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-7200}"
RUN_DATE="${ODATE:0:4}-${ODATE:4:2}-${ODATE:6:2}"
TRIGGERED_BY="${HOSTNAME:-controlm}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# First occurrence of a string/number field in the JSON response (no jq needed).
# Top-level fields come before the nested step list, so the first match is the job's own value.
json_field() {
  grep -o "\"$1\": *\"\{0,1\}[^\",}]*" | head -n 1 | sed "s/\"$1\": *\"\{0,1\}//"
}

log "Starting dailyFeedJob for run date ${RUN_DATE} (force=${FORCE})"
RESPONSE=$(curl -sS -w '\n%{http_code}' -X POST "${ENGINE_URL}/api/v1/jobs/dailyFeedJob/runs" \
  -H 'Content-Type: application/json' \
  -d "{\"runDate\":\"${RUN_DATE}\",\"triggerSource\":\"CONTROL_M\",\"triggeredBy\":\"${TRIGGERED_BY}\",\"forceRerun\":${FORCE}}")
HTTP_CODE=$(echo "${RESPONSE}" | tail -n 1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [[ "${HTTP_CODE}" != "202" ]]; then
  log "Launch failed with HTTP ${HTTP_CODE}: ${BODY}"
  exit 2
fi

EXECUTION_ID=$(echo "${BODY}" | json_field executionId)
log "Execution ${EXECUTION_ID} accepted"

ELAPSED=0
while (( ELAPSED < TIMEOUT_SECONDS )); do
  sleep "${POLL_SECONDS}"
  ELAPSED=$(( ELAPSED + POLL_SECONDS ))
  STATUS=$(curl -sS "${ENGINE_URL}/api/v1/jobs/runs/${EXECUTION_ID}" | json_field status)
  log "Execution ${EXECUTION_ID}: ${STATUS}"
  case "${STATUS}" in
    COMPLETED) log "Ended OK"; exit 0 ;;
    FAILED|STOPPED|ABANDONED) log "Ended NOT OK (${STATUS})"; exit 1 ;;
  esac
done

log "Timed out after ${TIMEOUT_SECONDS}s; execution ${EXECUTION_ID} still running"
exit 3
