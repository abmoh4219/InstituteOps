# Delivery Acceptance & Project Architecture Static Audit

## 1. Verdict
- **Overall conclusion:** **Partial Pass**
- The repository is substantial and largely aligned to the Prompt, but there are material security/architecture gaps (notably least-privilege overreach and sensitive response exposure) and some requirement-fit/compliance gaps that prevent a full pass.

## 2. Scope and Static Verification Boundary
- **Reviewed:** docs/config (`README.md`, `pom.xml`, `application.yml`, Docker manifests), security/authn/authz, core modules (student/grades/inventory/procurement/store/governance/recommender), DB migrations, templates/static assets, and test code under `src/test`.
- **Not reviewed in depth:** generated/build artifacts (`build/`, `target/`) as implementation truth sources.
- **Intentionally not executed:** app startup, tests, Docker, DB runtime flows, browser interactions, network calls.
- **Manual verification required for runtime-only claims:** transaction/concurrency behavior under load (inventory locking/FIFO races), end-to-end browser behavior timing/countdown correctness, LAN-only enforcement behind proxies/NAT, operational logging completeness under real traffic.

## 3. Repository / Requirement Mapping Summary
- **Prompt core goal mapped:** offline Spring Boot + Thymeleaf + MySQL institute operations platform with student lifecycle, grade ledger/recompute, inventory/procurement, group-buy store, governance, and recommender.
- **Main mapped implementation areas:**
  - Student lifecycle/timeline/homework in `src/main/java/com/instituteops/student/**` and `src/main/resources/templates/student-*.html`.
  - Grade ledger/recompute in `src/main/java/com/instituteops/grades/**` with schema in `src/main/resources/db/migration/V1__initial_schema.sql`.
  - Inventory/procurement/store/group-buy in `src/main/java/com/instituteops/inventory/**`, `procurement/**`, `store/**`.
  - Governance (CSV, duplicates, recycle, consistency) in `src/main/java/com/instituteops/governance/**`.
  - Security/auditing/internal API auth in `src/main/java/com/instituteops/security/**`, `src/main/java/com/instituteops/audit/**`.
  - Recommender CF module in `src/main/java/com/instituteops/recommender/**` and `src/main/resources/db/migration/V9__recommender_cf_module_seed.sql`.

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- **Conclusion:** Partial Pass
- **Rationale:** Startup/test instructions exist, but are Docker-first only and do not provide a clear non-Docker local run path; this weakens static verifiability in constrained environments.
- **Evidence:** `README.md:3`, `README.md:8`, `README.md:15`, `README.md:31`, `run_test.sh:33`, `run_test.sh:34`, `pom.xml:120`
- **Manual verification note:** Runtime viability of the documented Docker flow is **Manual Verification Required**.

#### 1.2 Material deviation from Prompt
- **Conclusion:** Pass
- **Rationale:** Implementation is centered on the Prompt domains (student lifecycle, grades, inventory/procurement, group-buy, governance, recommender) with matching modules and schema.
- **Evidence:** `src/main/java/com/instituteops/student/model/StudentModuleService.java:37`, `src/main/java/com/instituteops/grades/domain/GradeEntryService.java:25`, `src/main/java/com/instituteops/inventory/domain/InventoryModuleService.java:23`, `src/main/java/com/instituteops/procurement/domain/ProcurementService.java:21`, `src/main/java/com/instituteops/store/domain/CatalogService.java:27`, `src/main/java/com/instituteops/recommender/domain/RecommenderService.java:33`

### 2. Delivery Completeness

#### 2.1 Coverage of explicit core requirements
- **Conclusion:** Partial Pass
- **Rationale:** Most core requirements are implemented; notable deficits are around strict least-privilege boundaries and strict append-only/immutability guarantees.
- **Evidence:**
  - Implemented: `src/main/java/com/instituteops/student/model/StudentModuleService.java:41`, `src/main/java/com/instituteops/student/model/StudentModuleService.java:302`, `src/main/java/com/instituteops/grades/domain/GradeEntryService.java:112`, `src/main/java/com/instituteops/inventory/domain/InventoryModuleService.java:165`, `src/main/java/com/instituteops/procurement/domain/ProcurementService.java:248`, `src/main/java/com/instituteops/store/domain/CatalogService.java:202`
  - Gaps: `src/main/java/com/instituteops/security/SecurityConfig.java:74`, `src/main/resources/db/migration/V1__initial_schema.sql:233`
- **Manual verification note:** deterministic recompute behavior under real dataset size is **Manual Verification Required**.

#### 2.2 Basic end-to-end 0->1 deliverable
- **Conclusion:** Pass
- **Rationale:** Multi-module structure, schema migrations, templates, API endpoints, and tests indicate a full application deliverable rather than a snippet/demo.
- **Evidence:** `pom.xml:25`, `src/main/resources/db/migration/V1__initial_schema.sql:1`, `src/main/resources/templates/dashboard.html:1`, `src/main/resources/templates/store-student.html:1`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:37`

### 3. Engineering and Architecture Quality

#### 3.1 Structure and module decomposition
- **Conclusion:** Pass
- **Rationale:** Domain-oriented package decomposition is clear; controllers/services/entities/repositories are separated across major business areas.
- **Evidence:** `src/main/java/com/instituteops/student/web/StudentController.java:27`, `src/main/java/com/instituteops/grades/domain/GradeEntryController.java:20`, `src/main/java/com/instituteops/inventory/domain/InventoryController.java:19`, `src/main/java/com/instituteops/procurement/domain/ProcurementController.java:17`, `src/main/java/com/instituteops/store/domain/CatalogController.java:21`, `src/main/java/com/instituteops/governance/domain/GovernanceController.java:19`

#### 3.2 Maintainability and extensibility
- **Conclusion:** Partial Pass
- **Rationale:** Overall maintainable, but some cross-cutting guarantees rely on application logic instead of hard data-layer guarantees (e.g., grade ledger append-only immutability).
- **Evidence:** `src/main/java/com/instituteops/grades/domain/GradeEntryService.java:77`, `src/main/resources/db/migration/V1__initial_schema.sql:233`, `src/main/java/com/instituteops/grades/domain/GradeRepositories.java:32`

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API design
- **Conclusion:** Partial Pass
- **Rationale:** Error handling and input validation are generally strong; operation/data-access logging exists but is not comprehensive for all sensitive data flows.
- **Evidence:** `src/main/java/com/instituteops/web/ApiExceptionHandler.java:29`, `src/main/java/com/instituteops/inventory/domain/InventoryModuleService.java:170`, `src/main/java/com/instituteops/procurement/domain/ProcurementService.java:270`, `src/main/java/com/instituteops/audit/RequestAuditFilter.java:62`, `src/main/java/com/instituteops/audit/RequestAuditFilter.java:75`

#### 4.2 Product/service realism vs demo
- **Conclusion:** Pass
- **Rationale:** Includes authentication, persistence migrations, role permissions, auditing, governance workflows, and module-level tests consistent with a productized service.
- **Evidence:** `src/main/java/com/instituteops/security/SecurityConfig.java:57`, `src/main/resources/db/migration/V1__initial_schema.sql:799`, `src/main/resources/db/migration/V1__initial_schema.sql:816`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:84`

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Semantic fit to business goals and constraints
- **Conclusion:** Partial Pass
- **Rationale:** Business intent is broadly understood and implemented. Main mismatches are least-privilege drift in store student APIs and sensitive response shaping for student timelines.
- **Evidence:** `src/main/java/com/instituteops/security/SecurityConfig.java:66`, `src/main/java/com/instituteops/security/SecurityConfig.java:74`, `src/main/java/com/instituteops/student/model/StudentModuleService.java:355`, `src/main/java/com/instituteops/student/model/HomeworkAttachmentRecordEntity.java:123`

### 6. Aesthetics (frontend/full-stack)

#### 6.1 Visual/interaction quality fit
- **Conclusion:** Pass
- **Rationale:** Pages are structured with consistent component styling, status feedback, keyboard shortcut support, and countdown UI; no external HTTPS assets in templates supports offline intent.
- **Evidence:** `src/main/resources/templates/fragments/app-shell.html:56`, `src/main/resources/static/js/saas-ui.js:68`, `src/main/resources/static/js/saas-ui.js:123`, `src/main/resources/templates/store-student.html:38`, `src/test/java/com/instituteops/web/FrontendOfflineAssetsTest.java:14`
- **Manual verification note:** responsive behavior across target devices is **Manual Verification Required**.

## 5. Issues / Suggestions (Severity-Rated)

### High

1) **Severity:** High  
   **Title:** Store student/order APIs over-authorize `STORE_MANAGER` role (least-privilege violation)  
   **Conclusion:** Fail  
   **Evidence:** `src/main/java/com/instituteops/security/SecurityConfig.java:66`, `src/main/java/com/instituteops/security/SecurityConfig.java:74`, `src/main/java/com/instituteops/store/domain/CatalogController.java:156`  
   **Impact:** Role boundaries are broader than business need; non-student roles can reach student-order surfaces, violating least-privilege policy and increasing abuse/misuse risk.  
   **Minimum actionable fix:** Restrict `/store/student/**` and `/api/store/order` to `STUDENT` (plus optionally `SYSTEM_ADMIN` for support), and split manager-only endpoints explicitly.

2) **Severity:** High  
   **Title:** Student timeline API exposes internal homework storage metadata  
   **Conclusion:** Fail  
   **Evidence:** `src/main/java/com/instituteops/student/web/StudentController.java:203`, `src/main/java/com/instituteops/student/model/StudentModuleService.java:355`, `src/main/java/com/instituteops/student/model/StudentModuleService.java:724`, `src/main/java/com/instituteops/student/model/HomeworkAttachmentRecordEntity.java:91`, `src/main/java/com/instituteops/student/model/HomeworkAttachmentRecordEntity.java:123`, `src/main/java/com/instituteops/student/model/HomeworkAttachmentRecordEntity.java:131`  
   **Impact:** API responses can disclose `storedFileName`, `uploadPath`, and internal uploader IDs to student clients; this is unnecessary exposure of internal filesystem/operational metadata.  
   **Minimum actionable fix:** Return a redacted attachment DTO from timeline APIs (e.g., display name/type/size/checksum/timestamp only), and hide internal storage fields with DTO mapping or `@JsonIgnore`.

3) **Severity:** High  
   **Title:** Grade ledger append-only requirement is not hard-enforced at data layer  
   **Conclusion:** Partial Fail  
   **Evidence:** `src/main/resources/db/migration/V1__initial_schema.sql:233`, `src/main/java/com/instituteops/grades/domain/GradeEntryService.java:77`, `src/main/java/com/instituteops/grades/domain/GradeRepositories.java:32`  
   **Impact:** Prompt requires append-only auditable ledger; current approach is convention-based in service logic, without visible DB-level immutability controls (e.g., update/delete blockers), weakening audit guarantees.  
   **Minimum actionable fix:** Add DB-level immutability enforcement for `grade_ledger_entries` (e.g., deny UPDATE/DELETE triggers or restricted DB permissions), and add regression tests for attempted mutation.

### Medium

4) **Severity:** Medium  
   **Title:** Verification documentation is Docker-centric and weak for static/local review workflows  
   **Conclusion:** Partial Fail  
   **Evidence:** `README.md:3`, `README.md:8`, `README.md:31`, `run_test.sh:33`  
   **Impact:** Reviewers/operators without Docker (or in restricted environments) lack clear local startup/test instructions, reducing delivery verifiability.  
   **Minimum actionable fix:** Add explicit non-Docker local setup/run/test commands (`./mvnw spring-boot:run`, profile/env setup, integration-test invocation and prerequisites).

5) **Severity:** Medium  
   **Title:** Data-access audit logging is selective, not comprehensive across sensitive read/write paths  
   **Conclusion:** Partial Fail  
   **Evidence:** `src/main/java/com/instituteops/audit/RequestAuditFilter.java:75`, `src/main/java/com/instituteops/audit/RequestAuditFilter.java:82`, `src/main/java/com/instituteops/audit/RequestAuditFilter.java:89`  
   **Impact:** Audit trail may miss important sensitive accesses outside currently inferred path patterns (e.g., payments/grades read patterns), undermining compliance evidence quality.  
   **Minimum actionable fix:** Expand explicit audit coverage to all sensitive entity access categories via service-level instrumentation and standardized access-type taxonomy.

## 6. Security Review Summary

- **Authentication entry points:** **Pass**  
  Evidence: form login with custom handler in `src/main/java/com/instituteops/security/SecurityConfig.java:137`; bcrypt encoder in `src/main/java/com/instituteops/security/SecurityBeansConfig.java:12`; internal API key/secret auth in `src/main/java/com/instituteops/security/InternalApiClientAuthFilter.java:62`.

- **Route-level authorization:** **Partial Pass**  
  Evidence: comprehensive `requestMatchers` in `src/main/java/com/instituteops/security/SecurityConfig.java:57`; over-broad store student/api permissions at `src/main/java/com/instituteops/security/SecurityConfig.java:66` and `src/main/java/com/instituteops/security/SecurityConfig.java:74`.

- **Object-level authorization:** **Partial Pass**  
  Evidence: student object checks in `src/main/java/com/instituteops/student/model/StudentModuleService.java:428`; grade target checks in `src/main/java/com/instituteops/grades/domain/GradeAuthorizationService.java:21`; governance history checks in `src/main/java/com/instituteops/governance/domain/GovernanceService.java:337`.  
  Gap: store order relies on principal-to-student mapping rather than explicit role+ownership constraints (`src/main/java/com/instituteops/store/domain/CatalogService.java:565`).

- **Function-level authorization:** **Partial Pass**  
  Evidence: mutation restrictions in `src/main/java/com/instituteops/student/model/StudentModuleService.java:456`; grade action checks in `src/main/java/com/instituteops/grades/domain/GradeEntryController.java:41`.  
  Gap: role-model mismatch in store student surfaces (see issue #1).

- **Tenant / user data isolation:** **Partial Pass**  
  Evidence: student self-only timeline checks `src/main/java/com/instituteops/student/model/StudentModuleService.java:436`; instructor assigned-student checks `src/main/java/com/instituteops/student/model/StudentModuleService.java:567`.  
  Gap: sensitive attachment storage metadata exposed in own timeline API responses (issue #2).

- **Admin / internal / debug endpoint protection:** **Pass**  
  Evidence: internal API requires `API_INTERNAL` + policy filter (`src/main/java/com/instituteops/security/SecurityConfig.java:59`, `src/main/java/com/instituteops/security/InternalApiClientAuthFilter.java:52`); recommender admin endpoints restricted (`src/main/java/com/instituteops/security/SecurityConfig.java:86`).

## 7. Tests and Logging Review

- **Unit tests:** **Pass (basic-to-good)**  
  Evidence: service-level tests for grades/inventory/procurement/governance/recommender/crypto in `src/test/java/com/instituteops/grades/domain/GradeEntryServiceTest.java:23`, `src/test/java/com/instituteops/inventory/domain/InventoryModuleServiceTest.java:21`, `src/test/java/com/instituteops/governance/domain/GovernanceServiceTest.java:26`, `src/test/java/com/instituteops/recommender/domain/RecommenderServiceTest.java:27`, `src/test/java/com/instituteops/shared/crypto/AesGcmStringEncryptorTest.java:9`.

- **API / integration tests:** **Partial Pass**  
  Evidence: broad security and MVC tests in `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:37`; MySQL integration for internal API in `src/test/java/com/instituteops/web/InternalApiMysqlIT.java:17`.  
  Gap: high-risk gaps remain (least-privilege store routing, timeline response redaction regression, DB-level ledger immutability).

- **Logging categories / observability:** **Partial Pass**  
  Evidence: operation logging in `src/main/java/com/instituteops/audit/AuditLogService.java:25`; request-level trace and access logging in `src/main/java/com/instituteops/audit/RequestAuditFilter.java:40`.  
  Gap: data-access categories are selective (issue #5).

- **Sensitive-data leakage risk in logs / responses:** **Partial Fail**  
  Evidence: timeline API returns attachment entities including internal path metadata (`src/main/java/com/instituteops/student/model/HomeworkAttachmentRecordEntity.java:123`).  
  Logging leakage specific proof is limited; response leakage is statically evident.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Unit tests exist:** Yes (`*ServiceTest`, crypto tests).  
  Evidence: `src/test/java/com/instituteops/grades/domain/GradeEntryServiceTest.java:23`, `src/test/java/com/instituteops/recommender/domain/RecommenderServiceTest.java:27`.
- **API/integration tests exist:** Yes (MockMvc + MySQL IT).  
  Evidence: `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:37`, `src/test/java/com/instituteops/web/InternalApiMysqlIT.java:17`.
- **Frameworks:** JUnit 5, Spring Boot Test, MockMvc, Mockito, spring-security-test, Failsafe IT profile.  
  Evidence: `pom.xml:68`, `pom.xml:73`, `pom.xml:97`, `pom.xml:120`.
- **Test entry points documented:** Yes, but Docker-centric.  
  Evidence: `README.md:15`, `run_test.sh:50`.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Internal API key/secret auth + disabled-by-default sync | `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:92`, `src/test/java/com/instituteops/web/InternalApiMysqlIT.java:38` | 403 when disabled, 200 with valid creds, 401 with invalid creds | sufficient | none major | Add proxy/forwarded-header LAN tests |
| Student object-level timeline isolation | `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:170`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:210` | student cross-access 403; self access 200 | sufficient | none major | Add deleted-student edge case |
| Instructor authorization by assigned class/student | `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:396`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:432` | grades preview/append/recompute 403 for unassigned | sufficient | none major | Add mixed-role user test |
| Governance duplicate/consistency workflows | `src/test/java/com/instituteops/governance/domain/GovernanceServiceTest.java:68`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:499` | duplicate save invoked; consistency findings exposed | basically covered | large-data behavior untested | Add pagination/large CSV scan tests |
| Group-buy failure -> void orders and release locks | `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:1079`, `src/test/java/com/instituteops/security/SecurityIntegrationTest.java:1127` | asserts VOID/FAILED and lock RELEASED | basically covered | race/concurrency not covered | Add concurrent order/refresh integration test |
| Least-privilege store student endpoints | *(no direct negative test for STORE_MANAGER calling student order APIs)* | N/A | missing | severe authz gap could pass test suite undetected | Add explicit 403 tests for manager on `/api/store/order` and `/store/student/order` if policy requires student-only |
| Timeline sensitive response redaction (attachments internal fields) | `src/test/java/com/instituteops/security/AuditFixVerificationTest.java:286` (only contact redaction) | checks contact fields absent | insufficient | attachment storage metadata not asserted | Add JSON assertions to deny `uploadPath`, `storedFileName`, `uploadedBy` |
| Append-only grade ledger immutability | *(no mutation-attempt tests)* | N/A | missing | DB-level immutability regressions undetected | Add migration/integration test asserting UPDATE/DELETE on ledger is blocked |
| Offline asset isolation | `src/test/java/com/instituteops/web/FrontendOfflineAssetsTest.java:14` | asserts no `https://` in templates | basically covered | static JS/CSS external refs not checked | Expand scan to static assets and config |

### 8.3 Security Coverage Audit
- **Authentication:** **sufficiently covered** for login/internal API happy/failure paths (`src/test/java/com/instituteops/security/SecurityIntegrationTest.java:84`, `src/test/java/com/instituteops/web/InternalApiMysqlIT.java:38`).
- **Route authorization:** **insufficient** for least-privilege in store student/order surfaces; no failing tests currently detect over-broad store-manager access.
- **Object-level authorization:** **basically covered** for student/instructor/governance history and class-session access (`src/test/java/com/instituteops/security/AuditFixVerificationTest.java:246`).
- **Tenant/data isolation:** **insufficient** for response-shape leakage (attachments metadata) despite good access checks.
- **Admin/internal protection:** **basically covered** for internal API/recommender admin route classes, but edge cases (proxy/LAN trust boundary) remain.

### 8.4 Final Coverage Judgment
**Partial Pass**

- Major authn/authz and domain workflows are tested, and several important negative paths are present.
- However, uncovered high-risk areas remain (least-privilege store routing, attachment metadata response leakage checks, and ledger immutability enforcement), so severe defects could still exist while tests pass.

## 9. Final Notes
- This report is static-only and evidence-based; runtime behavior claims were avoided unless directly inferable from code/tests.
- Strongest priorities to address before acceptance: tighten store route authorization, redact sensitive timeline attachment fields, and hard-enforce grade ledger immutability at DB level.
