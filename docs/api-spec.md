# InstituteOps API and Route Specification

## Notes

- Base URL: `http://localhost:8080`
- Authentication model:
  - Form login session for web UI and role-protected APIs
  - Header auth (`X-API-KEY`, `X-API-SECRET`) for `/api/internal/**`

## Thymeleaf routes

### Common

- `GET /` -> redirect to `/dashboard`
- `GET /login` -> login page
- `GET /dashboard` -> main dashboard with recommendations

### Student lifecycle

- `GET /student` -> student lifecycle management page

### Grades

- `GET /instructor/grades` -> grades ledger UI

### Inventory

- `GET /inventory` -> inventory operations UI

### Procurement

- `GET /procurement` -> procurement operations UI

### Store and campaigns

- `GET /store` -> store manager view
- `GET /store/student` -> student campaign/purchase view
- `POST /store/spu`
- `POST /store/sku`
- `POST /store/tier`
- `POST /store/campaign`
- `POST /store/group/{groupId}/confirm`
- `POST /store/refresh`
- `POST /store/student/order`

### Recommender admin

- `GET /admin/recommender` -> recommender administration UI

## REST APIs

### Internal

- `GET /api/internal/ping`
  - Headers: `X-API-KEY`, `X-API-SECRET`
  - Response: `pong`

### Grades

- `POST /api/grades/preview`
  - Body: grade entry request (student, class, assessment, raw/max score)
  - Response: computed percent/grade/credits/GPA impact
- `POST /api/grades/recompute`
  - Body: `{ studentId, classId, reasonCode }`
  - Response: recomputation id + deterministic hash + persisted delta summaries

### Inventory

- `GET /api/inventory/stock`
  - Response: current stock map by ingredient id

### Procurement

- `GET /api/procurement/recommendations`
  - Response: replenishment recommendation list

### Store

- `GET /api/store/quote?skuId={id}&quantity={n}`
- `POST /api/store/quote`
  - Body: quote request
- `GET /api/store/campaigns`
- `POST /api/store/order`
  - Body: place-order request

### Recommender

- `GET /api/recommender/me?limit={n}`
  - Response: ranked recommendations for current user
- `POST /api/recommender/events`
  - Body: event payload (`eventType`, `studentId`, `itemType`, `itemId`, `eventValue`, `occurredAt`, `source`)
- Admin/model operations (role restricted):
  - train/incremental/rollback endpoints under `/api/recommender/**`

### Governance

- `GET /api/governance/students/export`
  - Response: CSV export for active student records
- `POST /api/governance/students/import?allowUpdate={true|false}`
  - Body: CSV text with header
  - Response: bulk-job summary (`createdRows`, `updatedRows`, `failedRows`, `status`)
- `POST /api/governance/students/duplicates/scan`
  - Response: latest exact/fuzzy duplicate candidates by name + DOB
- `GET /api/governance/students/{studentId}/history`
  - Response: `change_history` entries (students can only read own history)
- `GET /api/governance/recycle-bin`
- `POST /api/governance/recycle-bin/{recycleId}/restore`
- `POST /api/governance/recycle-bin/purge-expired`
