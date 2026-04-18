# Healthcare Interoperability Platform — Architecture

## Overview

An AI-assisted, production-grade platform that enables any EHR system to exchange healthcare data
with any other EHR system through:
1. **Self-service registration** — EHRs register their API endpoints via a portal
2. **Subscription-based routing** — Admin configures which EHRs receive data from which sources
3. **Automatic fanout** — When an EHR sends data, the platform routes it to all subscribed targets
4. **FHIR R4 as canonical model** — All data passes through FHIR for semantic normalization
5. **AI-assisted mapping** — Claude generates field mappings for new EHR combinations; rules engine validates

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Healthcare Interoperability Platform                   │
│                                                                               │
│  ┌─────────────┐     ┌─────────────┐     ┌───────────────────────────────┐  │
│  │  EHR Portal │     │ Admin Portal│     │       Any EHR System          │  │
│  │  (React)    │     │  (React)    │     │  (sends data via API key)     │  │
│  │  port 3000  │     │  port 3001  │     │                               │  │
│  └──────┬──────┘     └──────┬──────┘     └──────────────┬────────────────┘  │
│         │                   │                           │                    │
│         ▼                   ▼                           ▼                    │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                    API Gateway (port 8080)                               │ │
│  │   • JWT/API-key auth  • Rate limiting  • CORS  • Circuit breaker        │ │
│  └────────┬────────────────────────┬────────────────────┬──────────────────┘ │
│           │                        │                    │                    │
│           ▼                        ▼                    ▼                    │
│  ┌──────────────────┐   ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ EHR Registration │   │  Subscription    │  │    Routing Engine        │ │
│  │    Service       │   │    Service       │  │   (Fanout Orchestrator)  │ │
│  │  port 8081       │   │  port 8082       │  │    port 8083             │ │
│  │                  │   │                  │  │                          │ │
│  │ • Register EHRs  │   │ • Manage plans   │  │ • Resolve routing rules  │ │
│  │ • Store endpoint │   │ • Assign EHRs    │  │ • Fan out N pipelines    │ │
│  │   configs        │   │   to plans       │  │ • Aggregate results      │ │
│  │ • API key mgmt   │   │ • Configure      │  │ • Publish to Kafka       │ │
│  │ • AES-256 creds  │   │   routing rules  │  │                          │ │
│  └────────┬─────────┘   └──────────────────┘  └────────────┬─────────────┘ │
│           │                                                 │                │
│           │                     ┌───────────────────────────┘                │
│           │                     │                                            │
│           ▼                     ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │               Transformation Pipeline (per route)                     │   │
│  │                                                                       │   │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐  │   │
│  │  │  Dynamic       │  │ FHIR Canonical │  │   Mapping Service      │  │   │
│  │  │  Adapter       │  │   Service      │  │   (AI + Rules Engine)  │  │   │
│  │  │  port 8084     │  │   port 8086    │  │   port 8085            │  │   │
│  │  │                │  │                │  │                        │  │   │
│  │  │ • Load EHR     │  │ • Normalize    │  │ 1. Lookup DB template  │  │   │
│  │  │   endpoint     │  │   source →     │  │ 2. If none: call Claude│  │   │
│  │  │   config       │  │   FHIR R4      │  │ 3. Rules engine validates│ │   │
│  │  │ • Resolve auth │  │ • HAPI FHIR    │  │ 4. Apply field mappings │  │   │
│  │  │   (OAuth2,     │  │   validation   │  │ 5. Store AI template    │  │   │
│  │  │   Bearer,etc.) │  │ • R4 Bundle    │  │    in PostgreSQL        │  │   │
│  │  │ • Render       │  │   output       │  │                        │  │   │
│  │  │   payload      │  │                │  └────────────────────────┘  │   │
│  │  │   template     │  └────────────────┘                              │   │
│  │  │ • POST to EHR  │                                                  │   │
│  │  └────────────────┘                                                  │   │
│  │                                                                       │   │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐  │   │
│  │  │ Context        │  │  Validation    │  │   Audit Service        │  │   │
│  │  │ Resolver       │  │   Service      │  │   port 8089            │  │   │
│  │  │ port 8087      │  │   port 8088    │  │                        │  │   │
│  │  │                │  │                │  │ • Kafka consumer       │  │   │
│  │  │ • Fetch EBPLP  │  │ • JSON Schema  │  │ • Persist to PG        │  │   │
│  │  │   IDs from     │  │   validation   │  │ • Query by correlId    │  │   │
│  │  │   target EHR   │  │ • FHIR compli- │  │ • HIPAA audit trail    │  │   │
│  │  │ • Redis cache  │  │   ance check   │  │                        │  │   │
│  │  │   (1h TTL)     │  │ • Business     │  └────────────────────────┘  │   │
│  │  │ • Circuit      │  │   rules        │                              │   │
│  │  │   breaker      │  │                │                              │   │
│  │  └────────────────┘  └────────────────┘                              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                         Infrastructure                                   │ │
│  │  PostgreSQL 16  │  Redis 7  │  Apache Kafka  │  (MongoDB — history)     │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Data Flow: Omnione → EHR1 + EHR2 (Patient fanout)

```
Step 1: Omnione sends patient data
  POST /api/v1/ingest
  Headers: X-API-Key: ihp_xxxxx
  Body: { sourceEhrCode: "OMNIONE", resourceType: "PATIENT", payload: {...} }
         ↓
Step 2: API Gateway
  - Validates API key against PostgreSQL (cached in Redis)
  - Identifies source EHR = OMNIONE
  - Rate limit check (Redis token bucket)
  - Routes to Routing Engine
         ↓
Step 3: Routing Engine
  - Calls Subscription Service: GET /admin/routing-rules/active?sourceEhrCode=OMNIONE&resourceType=PATIENT
  - Receives: [{ target: EHR1, op: SAVE_PATIENT }, { target: EHR2, op: SAVE_PATIENT }]
  - Validates subscription not expired / not rate-limited
  - Fans out 2 parallel pipelines
         ↓
┌────── Pipeline A (→ EHR1) ─────────────────────────────────────────────────┐
│                                                                              │
│ Step 4A: Dynamic Adapter (parse)                                            │
│   - Loads Omnione schema from schema_registry                               │
│   - Normalizes Omnione JSON to intermediate format                          │
│                                                                              │
│ Step 5A: FHIR Canonical Service                                             │
│   - Converts intermediate → FHIR R4 Patient Bundle                         │
│   - HAPI FHIR validation                                                    │
│                                                                              │
│ Step 6A: Context Resolver                                                   │
│   - Checks Redis: "ctx:EHR1:SAVE_PATIENT"                                  │
│   - Cache miss → calls EHR1's registered org API                           │
│   - Returns: { orgId: "ORG-123", facilityId: "FAC-456" }                   │
│   - Caches result for 1 hour                                                │
│                                                                              │
│ Step 7A: Mapping Service                                                    │
│   - DB lookup: mapping_templates WHERE source=OMNIONE AND target=EHR1      │
│   - Found → apply deterministic field rules                                 │
│     (Not found → call Claude → rules engine validates → persist template)  │
│   - Apply transform functions (DATE_FORMAT_ISO, PHONE_FORMAT, etc.)        │
│   - Inject resolved IDs into payload                                        │
│                                                                              │
│ Step 8A: Dynamic Adapter (deliver)                                          │
│   - Load EHR1 endpoint config from Redis / DB                               │
│   - Resolve auth header (OAuth2 token from Redis or fresh fetch)           │
│   - Render payload template with {{placeholders}}                           │
│   - POST to EHR1.base_url/patients (3 retries, exponential backoff)        │
│   - Circuit breaker: open after 5 failures in 10-call window                │
│                                                                              │
│ Step 9A: Validation                                                         │
│   - JSON Schema validation                                                  │
│   - Business rule checks (valid DOB, gender code, required NPI)            │
│                                                                              │
│ → Result: { target: EHR1, status: SUCCESS, durationMs: 342 }               │
└──────────────────────────────────────────────────────────────────────────────┘

┌────── Pipeline B (→ EHR2) — same steps in parallel ────────────────────────┐
│ → Result: { target: EHR2, status: FAILED, error: "Connection timeout" }    │
└──────────────────────────────────────────────────────────────────────────────┘

Step 10: Routing Engine aggregates
  {
    correlationId: "uuid",
    overallStatus: "PARTIAL_SUCCESS",
    results: [
      { target: "EHR1", status: "SUCCESS" },
      { target: "EHR2", status: "FAILED", error: "Connection timeout" }
    ]
  }
  → EHR2 failure published to Kafka dead-letter topic for retry

Step 11: Audit trail
  Kafka topic: interop.audit
  → Audit Service persists both pipeline results to PostgreSQL
  → HIPAA-compliant audit log with correlationId, timing, AI usage
```

---

## AI Design (Anti-Hallucination Architecture)

```
TRIGGER: No mapping template found for source→target+resourceType

                    ┌──────────────────────────────────┐
                    │        INPUT TO CLAUDE           │
                    │  (NO PHI — schema only)          │
                    │                                  │
                    │  • sourceSchema (field names     │
                    │    + types, NOT values)          │
                    │  • targetFHIRSchema              │
                    │  • resourceType                  │
                    │  • 2 few-shot examples           │
                    └─────────────┬────────────────────┘
                                  │
                                  ▼
                    ┌──────────────────────────────────┐
                    │    CLAUDE claude-sonnet-4-6      │
                    │                                  │
                    │  System prompt enforces:         │
                    │  • Output ONLY valid JSON        │
                    │  • confidence 0.0-1.0 per field  │
                    │  • Explicit unmappable fields    │
                    │  • No invented clinical values   │
                    └─────────────┬────────────────────┘
                                  │
                                  ▼
                    ┌──────────────────────────────────┐
                    │      RULES ENGINE                │
                    │   (Deterministic gatekeeper)     │
                    │                                  │
                    │  Rejects mappings where:         │
                    │  ✗ confidence < 0.85             │
                    │  ✗ unknown transformFunction     │
                    │  ✗ required FHIR fields missing  │
                    │  ✗ invalid code system           │
                    │                                  │
                    │  Overrides/enforces:             │
                    │  ✓ mandatory: id, name, birthDate│
                    │  ✓ gender: must be FHIR values   │
                    │  ✓ code systems: ICD-10, SNOMED  │
                    └─────────────┬────────────────────┘
                                  │
                       ┌──────────┴──────────┐
                       │                     │
                  VALID (pass)          INVALID (fail)
                       │                     │
                       ▼                     ▼
              Persist to DB           Return PARTIAL_MAPPING
              Use for this            error with details
              transform + all
              future transforms
```

---

## Database Schema Summary

```
PostgreSQL (interop_db):
  ehr_registrations     — EHR identity, credentials (AES-256), API key hash
  ehr_endpoints         — Per-operation HTTP configs per EHR
  subscription_plans    — BASIC / STANDARD / ENTERPRISE plans
  ehr_subscriptions     — EHR → Plan assignment with expiry
  routing_rules         — Source EHR → Target EHR routes (admin-configured)
  mapping_templates     — Versioned field mapping configs (AI + manual)
  schema_registry       — JSON schemas per EHR + resource type
  audit_logs            — Every transformation event (HIPAA trail)

Redis:
  ehr-registrations:{ehrCode}       — Cached EHR entity (1h TTL)
  ehr-endpoints:{ehrCode}:{op}      — Cached endpoint config (1h TTL)
  routing-rules:{source}:{resource} — Cached active routes (5min TTL)
  oauth2:token:{ehrCode}            — OAuth2 access tokens (expires_in - 60s)
  ai-mappings:{src}:{tgt}:{res}     — Cached AI mapping results (24h TTL)
  ctx:{ehrCode}:{operation}         — Resolved external IDs (1h TTL)
```

---

## Security & HIPAA Compliance

| Control | Implementation |
|---|---|
| Data in transit | TLS 1.3 (Ingress + inter-service) |
| Data at rest | PostgreSQL encryption + AES-256 for credentials |
| PHI in AI calls | Schema only — NO patient values sent to Claude |
| Authentication | API keys (EHRs) + JWT (admin users) |
| Authorization | RBAC via API Gateway filter |
| Audit trail | Every transformation logged with correlationId |
| Key rotation | Admin can rotate EHR API keys on demand |
| Secrets management | Kubernetes Secrets (use Sealed Secrets in prod) |
| Network isolation | Kubernetes namespace + NetworkPolicy |
| Rate limiting | Redis token bucket per API key |

---

## Scalability

```
Routing Engine:     3 → 20 pods (HPA on CPU 65%)
Mapping Service:    2 → 10 pods (HPA on CPU 70%)
Dynamic Adapter:    2 → 15 pods (HPA on CPU 70%)
FHIR Service:       1 → 8 pods  (HPA on CPU 75%)

Kafka partitions:
  interop.route:  8 partitions  (parallel pipeline processing)
  interop.audit:  4 partitions  (sequential audit persistence)
  interop.dlq:    2 partitions  (dead-letter for retry)

PostgreSQL: Connection pooling via HikariCP (max 20/service)
Redis:      Cluster mode for high availability
```

---

## Failure Scenarios & Mitigations

| Scenario | Detection | Mitigation |
|---|---|---|
| Target EHR API down | HTTP 5xx / timeout | Circuit breaker → DLQ retry (3x, exponential) |
| EBPLP resolution fails | Context resolver error | Use cached value; if none, return PARTIAL with warning |
| AI returns invalid JSON | Parse exception | Reject AI output; fall back to closest existing template |
| AI confidence too low | RulesEngine check (< 0.85) | Reject mapping; return MAPPING_INSUFFICIENT error |
| Kafka unavailable | Producer exception | Buffer in-memory (10k events); alert on queue size |
| DB pool exhausted | HikariCP timeout | Circuit breaker opens; return 503 with Retry-After |
| Redis unavailable | Connection error | Fall through to DB (cache miss); performance degrades |
| Subscription expired | Expiry check at routing | Return 402 Payment Required |
| Rate limit exceeded | Redis counter | Return 429 with Retry-After header |
| Schema version mismatch | Schema registry | Block transformation; alert admin |

---

## API Reference

### EHR Self-Registration
```
POST   /api/v1/ehr/register              Register new EHR (returns API key)
GET    /api/v1/ehr/{ehrCode}             Get EHR details
POST   /api/v1/ehr/{ehrCode}/test        Test connection to EHR
POST   /api/v1/ehr/{ehrCode}/endpoints   Add operation endpoint
POST   /api/v1/ehr/{ehrCode}/rotate-key  Rotate API key
```

### Data Ingestion (EHR-facing)
```
POST   /api/v1/ingest                    Send data → triggers fanout
GET    /api/v1/ingest/status/{corrId}    Check fanout result
```

### Admin APIs
```
POST   /api/v1/admin/plans               Create subscription plan
POST   /api/v1/admin/subscriptions       Assign EHR to plan
POST   /api/v1/admin/routing-rules       Create source→target routing rule
GET    /api/v1/admin/routing-rules       List routing rules
PUT    /api/v1/admin/routing-rules/{id}/toggle  Enable/disable rule
GET    /api/v1/audit/search              Query audit logs
GET    /api/v1/audit/{correlationId}     Get full trace by correlation ID
```

---

## Quick Start

```bash
# 1. Copy environment config
cp .env.example .env
# Edit .env with your ANTHROPIC_API_KEY and database passwords

# 2. Start full stack
docker-compose up -d

# 3. Access portals
open http://localhost:3000   # EHR Self-Registration Portal
open http://localhost:3001   # Admin Portal

# 4. Register ECW via portal or API
curl -X POST http://localhost:8080/api/v1/ehr/register \
  -H "Content-Type: application/json" \
  -d @docs/examples/ecw-registration.json

# 5. Create admin routing rule
curl -X POST http://localhost:8080/api/v1/admin/routing-rules \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d '{"sourceEhrCode":"ECW","targetEhrCode":"OMNIONE","resourceType":"PATIENT","targetOperation":"SAVE_PATIENT"}'

# 6. Send patient data
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "X-API-Key: ihp_your_ecw_api_key" \
  -H "Content-Type: application/json" \
  -d @docs/examples/ecw-patient.json

# 7. Check audit trail
curl http://localhost:8080/api/v1/audit/{correlationId}
```
