package com.instituteops.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.instituteops.security.domain.InternalApiClientEntity;
import com.instituteops.security.repo.InternalApiClientRepository;
import com.instituteops.student.model.StudentProfileEntity;
import com.instituteops.student.model.StudentProfileLookupRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentProfileLookupRepository studentProfileLookupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private InternalApiClientRepository internalApiClientRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        ensureInstructorAuthorizationSchema();
        jdbcTemplate.update("DELETE FROM enrollments");
        studentProfileLookupRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM sync_config");
        seedTestUsers();

        StudentProfileEntity student1 = new StudentProfileEntity();
        student1.setStudentNo("student1");
        student1.setFirstName("Alice");
        student1.setLastName("One");
        student1.setDateOfBirth(LocalDate.parse("2010-01-01"));
        student1.setStatus("ACTIVE");
        studentProfileLookupRepository.save(student1);

        StudentProfileEntity student2 = new StudentProfileEntity();
        student2.setStudentNo("student2");
        student2.setFirstName("Bob");
        student2.setLastName("Two");
        student2.setDateOfBirth(LocalDate.parse("2010-02-02"));
        student2.setStatus("ACTIVE");
        studentProfileLookupRepository.save(student2);

        seedInstructorAssignmentForStudent(student1.getId());
    }

    @Test
    void unauthenticatedInternalApi_is401() throws Exception {
        enableInternalSyncConfig(true, false);
        mockMvc.perform(get("/api/internal/ping"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void internalApiDisabledByDefault_isForbiddenEvenWithValidCredentials() throws Exception {
        mockActiveInternalApiClient("client-disabled", "secret-disabled");

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "client-disabled")
                .header("X-API-SECRET", "secret-disabled"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedStudentTimeline_redirectsToLogin() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(get("/api/students/{id}/timeline", student1Id))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void unauthenticatedJavascriptAsset_isPubliclyAccessibleThroughSecurityFilters() throws Exception {
        mockMvc.perform(get("/js/saas-ui.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(Matchers.containsString("initSidebar")));
    }

    @Test
    void invalidInternalApiCredentials_is401() throws Exception {
        enableInternalSyncConfig(true, false);
        InternalApiClientEntity client = new InternalApiClientEntity();
        client.setClientKey("client-1");
        client.setClientSecretHash("stored");
        client.setActive(true);
        client.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(internalApiClientRepository.findByClientKeyAndActiveTrue("client-1")).thenReturn(Optional.of(client));
        when(passwordEncoder.matches("wrong-secret", "stored")).thenReturn(false);

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "client-1")
                .header("X-API-SECRET", "wrong-secret"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalApiClient_is401() throws Exception {
        enableInternalSyncConfig(true, false);
        InternalApiClientEntity client = new InternalApiClientEntity();
        client.setClientKey("client-2");
        client.setClientSecretHash("stored");
        client.setActive(true);
        client.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(internalApiClientRepository.findByClientKeyAndActiveTrue("client-2")).thenReturn(Optional.of(client));

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "client-2")
                .header("X-API-SECRET", "any"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveInternalApiClient_is401() throws Exception {
        enableInternalSyncConfig(true, false);
        when(internalApiClientRepository.findByClientKeyAndActiveTrue("inactive-client")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "inactive-client")
                .header("X-API-SECRET", "any"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void wrongRoleAccessToInventory_is403() throws Exception {
        mockMvc.perform(get("/api/inventory/stock"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentCrossAccessTimeline_is403() throws Exception {
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();

        mockMvc.perform(get("/api/students/{id}/timeline", student2Id))
            .andExpect(status().isForbidden());
    }

    @Test
    void internalApiLanOnly_rejectsNonLanRemoteAddress() throws Exception {
        enableInternalSyncConfig(true, true);
        mockActiveInternalApiClient("lan-client", "lan-secret");

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "lan-client")
                .header("X-API-SECRET", "lan-secret")
                .with(request -> {
                    request.setRemoteAddr("8.8.8.8");
                    return request;
                }))
            .andExpect(status().isForbidden());
    }

    @Test
    void internalApiLanOnly_allowsLoopbackRemoteAddress() throws Exception {
        enableInternalSyncConfig(true, true);
        mockActiveInternalApiClient("lan-ok-client", "lan-ok-secret");

        mockMvc.perform(get("/api/internal/ping")
                .header("X-API-KEY", "lan-ok-client")
                .header("X-API-SECRET", "lan-ok-secret")
                .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
            .andExpect(status().isOk())
            .andExpect(content().string("pong"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentSelfAccessTimeline_isOk() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(get("/api/students/{id}/timeline", student1Id))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentTimeline_hidesInternalCommentsButShowsStudentVisible() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        jdbcTemplate.update(
            "INSERT INTO instructor_comments (student_id, instructor_user_id, comment_text, visibility, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP())",
            student1Id,
            1L,
            "Internal note",
            "INTERNAL"
        );
        jdbcTemplate.update(
            "INSERT INTO instructor_comments (student_id, instructor_user_id, comment_text, visibility, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP())",
            student1Id,
            1L,
            "Visible note",
            "STUDENT_VISIBLE"
        );

        mockMvc.perform(get("/api/students/{id}/timeline", student1Id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments.length()").value(1))
            .andExpect(jsonPath("$.comments[0].commentText").value("Visible note"))
            .andExpect(jsonPath("$.comments[0].visibility").value("STUDENT_VISIBLE"));
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void staffTimeline_seesInternalAndStudentVisibleComments() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        jdbcTemplate.update(
            "INSERT INTO instructor_comments (student_id, instructor_user_id, comment_text, visibility, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP())",
            student1Id,
            1L,
            "Internal note",
            "INTERNAL"
        );
        jdbcTemplate.update(
            "INSERT INTO instructor_comments (student_id, instructor_user_id, comment_text, visibility, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP())",
            student1Id,
            1L,
            "Visible note",
            "STUDENT_VISIBLE"
        );

        mockMvc.perform(get("/api/students/{id}/timeline", student1Id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments.length()").value(2));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentCannotMutateAuthoritativeStudentRecords() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(post("/student/{id}/status", student1Id).param("status", "SUSPENDED").with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student")
                .param("studentNo", "STU-NEW")
                .param("firstName", "Test")
                .param("lastName", "User")
                .param("dateOfBirth", "2012-01-01")
                .with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/{id}/delete", student1Id).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/{id}/restore", student1Id).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/{id}/payments", student1Id)
                .param("paymentMethod", "CASH")
                .param("amount", "10.00")
                .with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/{id}/attendance", student1Id)
                .param("enrollmentId", "1")
                .param("classSessionId", "1")
                .param("attendanceStatus", "PRESENT")
                .with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/student/{id}/comments", student1Id)
                .param("commentText", "test")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentHomeworkUpload_isSelfOnly() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();
        MockMultipartFile file = new MockMultipartFile("file", "work.pdf", "application/pdf", "hello".getBytes());

        mockMvc.perform(multipart("/student/{id}/homework", student1Id)
                .file(file)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(multipart("/student/{id}/homework", student2Id)
                .file(file)
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void recommenderEventIgnoresForgedStudentId() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();
        long forgedItemId = 987654321L;

        String payload = """
            {
              "eventType": "VIEW",
              "studentId": %d,
              "itemType": "SKU",
              "itemId": %d,
              "eventValue": 1.0,
              "source": "WEB"
            }
            """.formatted(student2Id, forgedItemId);

        mockMvc.perform(post("/api/recommender/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isOk());

        Long storedStudentId = jdbcTemplate.queryForObject(
            "SELECT student_id FROM recommender_events WHERE item_id = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            forgedItemId
        );
        org.assertj.core.api.Assertions.assertThat(storedStudentId).isEqualTo(student1Id);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void governanceHistoryRead_isCapturedInDataAccessLogs() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        Long before = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_access_logs WHERE entity_type = 'STUDENT' AND entity_id = ?",
            Long.class,
            student1Id
        );

        mockMvc.perform(get("/api/governance/students/{id}/history", student1Id))
            .andExpect(status().isOk());

        Long after = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_access_logs WHERE entity_type = 'STUDENT' AND entity_id = ?",
            Long.class,
            student1Id
        );
        org.assertj.core.api.Assertions.assertThat(after).isGreaterThan(before);
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void gradePreview_requiresCsrfAndWorksWithCsrf() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        Long assignedClassId = assignedInstructorClassId();
        String payload = gradeEntryPayload(student1Id, assignedClassId, "ENTRY");

        mockMvc.perform(post("/api/grades/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/grades/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void gradeEntryAuthorization_deniesInstructorForUnassignedStudentAcrossPreviewAppendAndRecompute() throws Exception {
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();
        Long assignedClassId = assignedInstructorClassId();
        String payload = gradeEntryPayload(student2Id, assignedClassId, "ENTRY");

        mockMvc.perform(post("/api/grades/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/grades/ledger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/grades/recompute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    """
                    {
                      "studentId": %d,
                      "classId": %d,
                      "reasonCode": "POLICY_CHANGE"
                    }
                    """,
                    student2Id,
                    assignedClassId
                ))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void gradeEntryAuthorization_allowsInstructorForAssignedStudentAcrossPreviewAppendAndRecompute() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        Long assignedClassId = assignedInstructorClassId();
        String payload = gradeEntryPayload(student1Id, assignedClassId, "ENTRY");

        mockMvc.perform(post("/api/grades/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/grades/ledger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/grades/recompute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    """
                    {
                      "studentId": %d,
                      "classId": %d,
                      "reasonCode": "POLICY_CHANGE"
                    }
                    """,
                    student1Id,
                    assignedClassId
                ))
                .with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "registrar", roles = "REGISTRAR_FINANCE_CLERK")
    void governanceUiRoutes_renderAndImportActionsHandleSuccessAndFailure() throws Exception {
        mockMvc.perform(get("/governance"))
            .andExpect(status().isOk());

        MockMultipartFile valid = new MockMultipartFile(
            "file",
            "students.csv",
            "text/csv",
            ("student_no,first_name,last_name,preferred_name,date_of_birth,contact_email,contact_phone,contact_address,emergency_contact\n"
                + "STU-66,Jane,Doe,,2011-03-05,jane@example.com,123,Street,Parent").getBytes()
        );
        mockMvc.perform(multipart("/governance/import")
                .file(valid)
                .param("allowUpdate", "false")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        MockMultipartFile invalid = new MockMultipartFile("file", "bad.csv", "text/csv", "bad,header\n1,2".getBytes());
        mockMvc.perform(multipart("/governance/import")
                .file(invalid)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "registrar", roles = "REGISTRAR_FINANCE_CLERK")
    void governanceConsistencyScan_persistsAndExposesFindings() throws Exception {
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();
        jdbcTemplate.update("UPDATE students SET deleted_at = CURRENT_TIMESTAMP(), status = 'ACTIVE' WHERE id = ?", student2Id);

        mockMvc.perform(post("/api/governance/consistency/scan").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].issueType").exists());

        mockMvc.perform(get("/api/governance/consistency/issues"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.issueType == 'STUDENT_SOFT_DELETE_STATUS_MISMATCH')]").isNotEmpty());

        mockMvc.perform(get("/governance"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Consistency Findings")));
    }

    @Test
    @WithMockUser(username = "approver", roles = "PROCUREMENT_APPROVER")
    void procurementMvcEndToEnd_flowCreatesOrderAndInventoryReceipt() throws Exception {
        Long unitId = ensureInventoryUnitFixture();
        Long ingredientId = ensureIngredientFixture(unitId);

        mockMvc.perform(post("/procurement/supplier")
                .with(csrf())
                .param("supplierCode", "SUP-E2E")
                .param("supplierName", "E2E Supplier")
                .param("contactName", "Procurement QA"))
            .andExpect(status().is3xxRedirection());

        Long supplierId = jdbcTemplate.queryForObject("SELECT id FROM suppliers WHERE supplier_code = ?", Long.class, "SUP-E2E");

        mockMvc.perform(post("/procurement/supplier-item")
                .with(csrf())
                .param("supplierId", supplierId.toString())
                .param("ingredientId", ingredientId.toString())
                .param("supplierSku", "SUP-E2E-SKU")
                .param("packSize", "1.000")
                .param("packUnitId", unitId.toString())
                .param("preferred", "true"))
            .andExpect(status().is3xxRedirection());

        Long supplierItemId = jdbcTemplate.queryForObject("SELECT id FROM supplier_items WHERE supplier_id = ? AND ingredient_id = ?", Long.class, supplierId, ingredientId);

        mockMvc.perform(post("/procurement/price")
                .with(csrf())
                .param("supplierItemId", supplierItemId.toString())
                .param("price", "8.50")
                .param("currencyCode", "USD")
                .param("effectiveFrom", LocalDate.now().toString()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/request")
                .with(csrf())
                .param("justification", "E2E integration procurement")
                .param("neededBy", LocalDate.now().plusDays(7).toString()))
            .andExpect(status().is3xxRedirection());

        Long requestId = jdbcTemplate.queryForObject(
            "SELECT id FROM procurement_requests WHERE justification = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            "E2E integration procurement"
        );

        mockMvc.perform(post("/procurement/request-line")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("ingredientId", ingredientId.toString())
                .param("requestedQty", "3.000")
                .param("unitId", unitId.toString())
                .param("estimatedUnitPrice", "8.50")
                .param("note", "E2E line"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/approve")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("decision", "APPROVED")
                .param("decisionNote", "Approved for test"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/order")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("supplierId", supplierId.toString()))
            .andExpect(status().is3xxRedirection());

        Long poId = jdbcTemplate.queryForObject("SELECT id FROM purchase_orders WHERE request_id = ? ORDER BY id DESC LIMIT 1", Long.class, requestId);
        Long poLineId = jdbcTemplate.queryForObject("SELECT id FROM purchase_order_lines WHERE purchase_order_id = ? ORDER BY id LIMIT 1", Long.class, poId);

        mockMvc.perform(post("/procurement/receive")
                .with(csrf())
                .param("purchaseOrderId", poId.toString())
                .param("purchaseOrderLineId", poLineId.toString())
                .param("batchNo", "E2E-BATCH-1")
                .param("acceptedQty", "3.000")
                .param("rejectedQty", "0.000")
                .param("expiresAt", LocalDate.now().plusDays(30).toString())
                .param("note", "E2E receipt"))
            .andExpect(status().is3xxRedirection());

        String poStatus = jdbcTemplate.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, poId);
        Integer batchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_batches WHERE batch_no = ?", Integer.class, "E2E-BATCH-1");

        org.assertj.core.api.Assertions.assertThat(poStatus).isEqualTo("RECEIVED");
        org.assertj.core.api.Assertions.assertThat(batchCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "approver", roles = "PROCUREMENT_APPROVER")
    void procurementConvertsSupplierPackUnitsBeforePoAndInventoryPosting() throws Exception {
        Long ozUnitId = ensureInventoryUnit("OZ", "Ounce", "WEIGHT", true);
        Long lbUnitId = ensureInventoryUnit("LB", "Pound", "WEIGHT", false);
        jdbcTemplate.update(
            "MERGE INTO unit_conversions (from_unit_id, to_unit_id, factor) KEY(from_unit_id, to_unit_id) VALUES (?, ?, ?)",
            lbUnitId,
            ozUnitId,
            new BigDecimal("16.000000")
        );
        Long ingredientId = ensureIngredientFixture(ozUnitId);

        mockMvc.perform(post("/procurement/supplier")
                .with(csrf())
                .param("supplierCode", "SUP-PACK")
                .param("supplierName", "Pack Supplier")
                .param("contactName", "Pack QA"))
            .andExpect(status().is3xxRedirection());

        Long supplierId = jdbcTemplate.queryForObject("SELECT id FROM suppliers WHERE supplier_code = ?", Long.class, "SUP-PACK");

        mockMvc.perform(post("/procurement/supplier-item")
                .with(csrf())
                .param("supplierId", supplierId.toString())
                .param("ingredientId", ingredientId.toString())
                .param("supplierSku", "SUP-PACK-16OZ")
                .param("packSize", "16.000")
                .param("packUnitId", ozUnitId.toString())
                .param("preferred", "true"))
            .andExpect(status().is3xxRedirection());

        Long supplierItemId = jdbcTemplate.queryForObject("SELECT id FROM supplier_items WHERE supplier_id = ? AND ingredient_id = ?", Long.class, supplierId, ingredientId);

        mockMvc.perform(post("/procurement/price")
                .with(csrf())
                .param("supplierItemId", supplierItemId.toString())
                .param("price", "32.00")
                .param("currencyCode", "USD")
                .param("effectiveFrom", LocalDate.now().toString()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/request")
                .with(csrf())
                .param("justification", "Pack conversion procurement")
                .param("neededBy", LocalDate.now().plusDays(7).toString()))
            .andExpect(status().is3xxRedirection());

        Long requestId = jdbcTemplate.queryForObject(
            "SELECT id FROM procurement_requests WHERE justification = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            "Pack conversion procurement"
        );

        mockMvc.perform(post("/procurement/request-line")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("ingredientId", ingredientId.toString())
                .param("requestedQty", "2.000")
                .param("unitId", lbUnitId.toString())
                .param("estimatedUnitPrice", "20.00")
                .param("note", "Needs pounds"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/approve")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("decision", "APPROVED")
                .param("decisionNote", "approved"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/procurement/order")
                .with(csrf())
                .param("requestId", requestId.toString())
                .param("supplierId", supplierId.toString()))
            .andExpect(status().is3xxRedirection());

        Long poId = jdbcTemplate.queryForObject("SELECT id FROM purchase_orders WHERE request_id = ? ORDER BY id DESC LIMIT 1", Long.class, requestId);
        Long poLineId = jdbcTemplate.queryForObject("SELECT id FROM purchase_order_lines WHERE purchase_order_id = ? ORDER BY id LIMIT 1", Long.class, poId);

        BigDecimal orderedQty = jdbcTemplate.queryForObject("SELECT ordered_qty FROM purchase_order_lines WHERE id = ?", BigDecimal.class, poLineId);
        BigDecimal unitPrice = jdbcTemplate.queryForObject("SELECT unit_price FROM purchase_order_lines WHERE id = ?", BigDecimal.class, poLineId);
        Long poUnitId = jdbcTemplate.queryForObject("SELECT unit_id FROM purchase_order_lines WHERE id = ?", Long.class, poLineId);

        org.assertj.core.api.Assertions.assertThat(orderedQty).isEqualByComparingTo("32.000");
        org.assertj.core.api.Assertions.assertThat(unitPrice).isEqualByComparingTo("2.0000");
        org.assertj.core.api.Assertions.assertThat(poUnitId).isEqualTo(ozUnitId);

        mockMvc.perform(post("/procurement/receive")
                .with(csrf())
                .param("purchaseOrderId", poId.toString())
                .param("purchaseOrderLineId", poLineId.toString())
                .param("batchNo", "PACK-OZ-BATCH")
                .param("acceptedQty", "32.000")
                .param("rejectedQty", "0.000")
                .param("expiresAt", LocalDate.now().plusDays(30).toString())
                .param("note", "pack receive"))
            .andExpect(status().is3xxRedirection());

        BigDecimal batchQty = jdbcTemplate.queryForObject("SELECT quantity_received FROM inventory_batches WHERE batch_no = ?", BigDecimal.class, "PACK-OZ-BATCH");
        Long batchUnitId = jdbcTemplate.queryForObject("SELECT unit_id FROM inventory_batches WHERE batch_no = ?", Long.class, "PACK-OZ-BATCH");

        org.assertj.core.api.Assertions.assertThat(batchQty).isEqualByComparingTo("32.000");
        org.assertj.core.api.Assertions.assertThat(batchUnitId).isEqualTo(ozUnitId);
    }

    private Long ensureInventoryUnitFixture() {
        return ensureInventoryUnit("EA", "Each", "COUNT", true);
    }

    private Long ensureInventoryUnit(String unitCode, String unitName, String unitType, boolean baseUnit) {
        jdbcTemplate.update(
            "MERGE INTO inventory_units (unit_code, unit_name, unit_type, base_unit) KEY(unit_code) VALUES (?, ?, ?, ?)",
            unitCode,
            unitName,
            unitType,
            baseUnit
        );
        return jdbcTemplate.queryForObject("SELECT id FROM inventory_units WHERE unit_code = ?", Long.class, unitCode);
    }

    private Long ensureIngredientFixture(Long unitId) {
        jdbcTemplate.update(
            "MERGE INTO ingredients (ingredient_code, ingredient_name, default_unit_id, active) KEY(ingredient_code) VALUES (?, ?, ?, ?)",
            "ING-E2E",
            "E2E Ingredient",
            unitId,
            true
        );
        return jdbcTemplate.queryForObject("SELECT id FROM ingredients WHERE ingredient_code = ?", Long.class, "ING-E2E");
    }

    private Long assignedInstructorClassId() {
        return jdbcTemplate.queryForObject("SELECT id FROM classes WHERE class_code = ?", Long.class, "SEC-TST-CLASS");
    }

    private static String gradeEntryPayload(Long studentId, Long classId, String operationType) {
        return String.format(
            """
            {
              "studentId": %d,
              "classId": %d,
              "assessmentKey": "MIDTERM",
              "rawScore": 88,
              "maxScore": 100,
              "operationType": "%s"
            }
            """,
            studentId,
            classId,
            operationType
        );
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentUiHidesStaffOnlyActions() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(get("/student/{id}", student1Id))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Update Status"))
            ))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Record Payment"))
            ))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("Upload (PDF/JPG up to 10MB)")
            ));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void studentListPage_hidesCreateProfileFormForStudentRole() throws Exception {
        mockMvc.perform(get("/student"))
            .andExpect(status().isOk())
            .andExpect(content().string(Matchers.not(Matchers.containsString("Create Student Profile"))));
    }

    @Test
    @WithMockUser(username = "registrar", roles = "REGISTRAR_FINANCE_CLERK")
    void staffUiShowsStaffOnlyActions() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(get("/student/{id}", student1Id))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("Update Status")
            ))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("Record Payment")
            ));
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void instructorCannotAccessStudentNotInAssignedClasses() throws Exception {
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();

        mockMvc.perform(get("/student/{id}", student2Id))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void instructorCannotAccessGovernanceHistoryForUnassignedStudent() throws Exception {
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();

        mockMvc.perform(get("/api/governance/students/{id}/history", student2Id))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void instructorCanAccessGovernanceHistoryForAssignedStudent() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();

        mockMvc.perform(get("/api/governance/students/{id}/history", student1Id))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "sysadmin", roles = "SYSTEM_ADMIN")
    void dashboardLoadsWithoutSqlGrammarErrors() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("Pending Group-Buy Orders")
            ));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void groupBuyStudentMvcFlow_showsSuccessAndFailureFeedback() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        seedGroupBuyFixtures(student1Id);

        mockMvc.perform(post("/store/student/order")
                .with(csrf())
                .param("campaignId", "9104")
                .param("quantity", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store/student"))
            .andExpect(flash().attribute("successMessage", Matchers.containsString("submitted successfully")));

        mockMvc.perform(post("/store/student/order")
                .with(csrf())
                .param("campaignId", "9104")
                .param("quantity", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store/student"))
            .andExpect(flash().attribute("errorMessage", Matchers.containsString("Purchase limit exceeded")));

        mockMvc.perform(post("/store/student/order")
                .with(csrf())
                .param("campaignId", "9105")
                .param("quantity", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store/student"))
            .andExpect(flash().attribute("errorMessage", Matchers.containsString("Daily cutoff reached")));

        mockMvc.perform(post("/store/student/order")
                .with(csrf())
                .param("campaignId", "9106")
                .param("groupCode", "MISSING-GROUP")
                .param("quantity", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store/student"))
            .andExpect(flash().attribute("errorMessage", Matchers.containsString("Unable to join that group")));
    }

    @Test
    @WithMockUser(username = "store", roles = "STORE_MANAGER")
    void groupBuyConfirmationMvcFlow_showsSuccessAndErrorFeedback() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        seedGroupBuyFixtures(student1Id);

        jdbcTemplate.update(
            "INSERT INTO group_buy_groups (id, campaign_id, group_code, initiator_student_id, status, participants_target, participants_current, formed_at, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9108L,
            9104L,
            "GRP-9108",
            student1Id,
            "FORMED",
            2,
            2,
            LocalDateTime.now().minusMinutes(20),
            LocalDateTime.now().plusHours(6)
        );
        jdbcTemplate.update(
            "INSERT INTO group_buy_orders (id, order_no, campaign_id, group_id, student_id, order_status, quantity, unit_price, total_amount, payment_captured, void_reason, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9109L,
            "ORD-9109",
            9104L,
            9108L,
            student1Id,
            "INVENTORY_LOCKED",
            1,
            25.00,
            25.00,
            false,
            null
        );
        jdbcTemplate.update(
            "INSERT INTO inventory_locks (id, group_buy_order_id, ingredient_id, sku_id, locked_qty, unit_id, lock_status, lock_reason, locked_at, released_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?)",
            9110L,
            9109L,
            null,
            9102L,
            1.000,
            null,
            "LOCKED",
            "GROUP_FORMED_INVENTORY_LOCK",
            null
        );

        mockMvc.perform(post("/store/group/{groupId}/confirm", 9108L).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store"))
            .andExpect(flash().attribute("successMessage", Matchers.containsString("Inventory lock confirmation completed")));

        mockMvc.perform(post("/store/group/{groupId}/confirm", 999999L).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store"))
            .andExpect(flash().attribute("errorMessage", Matchers.containsString("Group not found")));
    }

    private void seedGroupBuyFixtures(Long studentId) {
        jdbcTemplate.update("DELETE FROM group_buy_group_members WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM inventory_locks WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM group_buy_orders WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM group_buy_groups WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM group_buy_campaigns WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM inventory_batches WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM ingredients WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM sku_pricing_tiers WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM sku_catalog WHERE id BETWEEN 9100 AND 9199");
        jdbcTemplate.update("DELETE FROM spu_catalog WHERE id BETWEEN 9100 AND 9199");

        jdbcTemplate.update(
            "MERGE INTO users (username, password_hash, display_name, active) KEY(username) VALUES (?, ?, ?, ?)",
            "store",
            "hashed",
            "Store Operator",
            true
        );

        jdbcTemplate.update(
            "INSERT INTO spu_catalog (id, spu_code, name, description, active) VALUES (?, ?, ?, ?, ?)",
            9101L,
            "SPU-9101",
            "Starter Kit",
            "Starter Kit",
            true
        );
        jdbcTemplate.update(
            "INSERT INTO sku_catalog (id, spu_id, sku_code, name, specs, inventory_item_ref, purchase_limit_per_student, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            9102L,
            9101L,
            "SKU-9102",
            "Starter Kit A",
            null,
            "KIT_STOCK",
            1,
            true
        );
        jdbcTemplate.update(
            "INSERT INTO ingredients (id, ingredient_code, ingredient_name, default_unit_id, active) VALUES (?, ?, ?, ?, ?)",
            9111L,
            "KIT_STOCK",
            "Kit Stock",
            1L,
            true
        );
        jdbcTemplate.update(
            "INSERT INTO inventory_batches (id, ingredient_id, batch_no, quantity_received, quantity_available, unit_id, unit_cost, received_at, expires_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?, ?)",
            9112L,
            9111L,
            "BATCH-KIT-1",
            2.000,
            2.000,
            1L,
            5.0000,
            java.sql.Date.valueOf(LocalDate.now().plusDays(10)),
            "AVAILABLE"
        );
        jdbcTemplate.update(
            "INSERT INTO sku_pricing_tiers (id, sku_id, min_qty, unit_price, currency_code, valid_from, valid_to) VALUES (?, ?, ?, ?, ?, ?, ?)",
            9103L,
            9102L,
            1,
            25.00,
            "USD",
            LocalDateTime.now().minusDays(1),
            null
        );

        jdbcTemplate.update(
            "INSERT INTO group_buy_campaigns (id, campaign_code, sku_id, title, starts_at, ends_at, cutoff_time, required_participants, formation_window_hours, status, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9104L,
            "CMP-LIMIT",
            9102L,
            "Limit Campaign",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().plusHours(6),
            java.sql.Time.valueOf("23:59:59"),
            2,
            24,
            "ACTIVE",
            1L
        );
        jdbcTemplate.update(
            "INSERT INTO group_buy_campaigns (id, campaign_code, sku_id, title, starts_at, ends_at, cutoff_time, required_participants, formation_window_hours, status, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9105L,
            "CMP-CUTOFF",
            9102L,
            "Cutoff Campaign",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().plusHours(6),
            java.sql.Time.valueOf("00:00:00"),
            2,
            24,
            "ACTIVE",
            1L
        );
        jdbcTemplate.update(
            "INSERT INTO group_buy_campaigns (id, campaign_code, sku_id, title, starts_at, ends_at, cutoff_time, required_participants, formation_window_hours, status, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9106L,
            "CMP-GROUP",
            9102L,
            "Group Join Campaign",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().plusHours(6),
            java.sql.Time.valueOf("23:59:59"),
            2,
            24,
            "ACTIVE",
            1L
        );

        jdbcTemplate.update(
            "INSERT INTO group_buy_groups (id, campaign_id, group_code, initiator_student_id, status, participants_target, participants_current, formed_at, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9107L,
            9104L,
            "GRP-9107",
            studentId,
            "FORMING",
            2,
            1,
            null,
            LocalDateTime.now().plusHours(6)
        );

        jdbcTemplate.update(
            "INSERT INTO group_buy_group_members (id, group_id, student_id, joined_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP(), ?)",
            9113L,
            9107L,
            studentId,
            "JOINED"
        );
        Long student2Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student2").orElseThrow().getId();
        jdbcTemplate.update(
            "INSERT INTO group_buy_group_members (id, group_id, student_id, joined_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP(), ?)",
            9116L,
            9107L,
            student2Id,
            "JOINED"
        );
    }

    @Test
    @WithMockUser(username = "store", roles = "STORE_MANAGER")
    void insufficientStockPreventsInventoryLockAndVoidsOrders() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        seedGroupBuyFixtures(student1Id);

        jdbcTemplate.update("UPDATE inventory_batches SET quantity_available = 0.500, quantity_received = 0.500 WHERE id = 9112");
        jdbcTemplate.update(
            "INSERT INTO group_buy_orders (id, order_no, campaign_id, group_id, student_id, order_status, quantity, unit_price, total_amount, payment_captured, void_reason, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9114L,
            "ORD-9114",
            9104L,
            9107L,
            student1Id,
            "PENDING_GROUP",
            1,
            25.00,
            25.00,
            false,
            null
        );

        mockMvc.perform(post("/store/refresh").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store"));

        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT order_status FROM group_buy_orders WHERE id = ?",
            String.class,
            9114L
        );
        String voidReason = jdbcTemplate.queryForObject(
            "SELECT void_reason FROM group_buy_orders WHERE id = ?",
            String.class,
            9114L
        );
        String groupStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM group_buy_groups WHERE id = ?",
            String.class,
            9107L
        );

        org.assertj.core.api.Assertions.assertThat(orderStatus).isEqualTo("VOID");
        org.assertj.core.api.Assertions.assertThat(voidReason).contains("Insufficient stock");
        org.assertj.core.api.Assertions.assertThat(groupStatus).isEqualTo("FAILED");
    }

    @Test
    @WithMockUser(username = "store", roles = "STORE_MANAGER")
    void inventoryReservationAndReleasePaths_adjustBatchAvailability() throws Exception {
        Long student1Id = studentProfileLookupRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull("student1").orElseThrow().getId();
        seedGroupBuyFixtures(student1Id);

        jdbcTemplate.update(
            "INSERT INTO group_buy_orders (id, order_no, campaign_id, group_id, student_id, order_status, quantity, unit_price, total_amount, payment_captured, void_reason, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())",
            9115L,
            "ORD-9115",
            9104L,
            9107L,
            student1Id,
            "PENDING_GROUP",
            1,
            25.00,
            25.00,
            false,
            null
        );

        mockMvc.perform(post("/store/refresh").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store"));

        BigDecimal afterLock = jdbcTemplate.queryForObject(
            "SELECT quantity_available FROM inventory_batches WHERE id = ?",
            BigDecimal.class,
            9112L
        );
        String lockedOrderStatus = jdbcTemplate.queryForObject(
            "SELECT order_status FROM group_buy_orders WHERE id = ?",
            String.class,
            9115L
        );
        org.assertj.core.api.Assertions.assertThat(lockedOrderStatus).isEqualTo("INVENTORY_LOCKED");
        org.assertj.core.api.Assertions.assertThat(afterLock).isEqualByComparingTo("1.000");

        jdbcTemplate.update("UPDATE group_buy_campaigns SET ends_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), 9104L);

        mockMvc.perform(post("/store/refresh").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/store"));

        BigDecimal afterRelease = jdbcTemplate.queryForObject(
            "SELECT quantity_available FROM inventory_batches WHERE id = ?",
            BigDecimal.class,
            9112L
        );
        String releasedOrderStatus = jdbcTemplate.queryForObject(
            "SELECT order_status FROM group_buy_orders WHERE id = ?",
            String.class,
            9115L
        );
        String lockStatus = jdbcTemplate.queryForObject(
            "SELECT lock_status FROM inventory_locks WHERE group_buy_order_id = ?",
            String.class,
            9115L
        );

        org.assertj.core.api.Assertions.assertThat(releasedOrderStatus).isEqualTo("VOID");
        org.assertj.core.api.Assertions.assertThat(lockStatus).isEqualTo("RELEASED");
        org.assertj.core.api.Assertions.assertThat(afterRelease).isEqualByComparingTo("2.000");
    }

    private void enableInternalSyncConfig(boolean enabled, boolean lanOnly) {
        jdbcTemplate.update(
            "INSERT INTO sync_config (sync_name, enabled, lan_only, cron_expression, endpoint) VALUES (?, ?, ?, ?, ?)",
            "LAN_OPTIONAL_SYNC",
            enabled,
            lanOnly,
            null,
            null
        );
    }

    private void mockActiveInternalApiClient(String clientKey, String rawSecret) {
        InternalApiClientEntity client = new InternalApiClientEntity();
        client.setClientKey(clientKey);
        client.setClientSecretHash("stored");
        client.setActive(true);
        client.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(internalApiClientRepository.findByClientKeyAndActiveTrue(clientKey)).thenReturn(Optional.of(client));
        when(passwordEncoder.matches(rawSecret, "stored")).thenReturn(true);
    }

    private void ensureInstructorAuthorizationSchema() {
        jdbcTemplate.execute("ALTER TABLE classes ADD COLUMN IF NOT EXISTS instructor_user_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE inventory_batches ADD COLUMN IF NOT EXISTS supplier_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE inventory_batches ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE inventory_batches ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS reference_type VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS reference_id BIGINT");
    }

    private void seedTestUsers() {
        for (String[] user : new String[][]{
            {"sysadmin", "System Admin"},
            {"registrar", "Registrar Clerk"},
            {"approver", "Procurement Approver"},
            {"inventory", "Inventory Manager"},
            {"store", "Store Manager"},
            {"student1", "Student User"}
        }) {
            jdbcTemplate.update(
                "MERGE INTO users (username, password_hash, display_name, active) KEY(username) VALUES (?, ?, ?, ?)",
                user[0], "hashed", user[1], true
            );
        }
    }

    private void seedInstructorAssignmentForStudent(Long studentId) {
        jdbcTemplate.update(
            "MERGE INTO users (username, password_hash, display_name, active) KEY(username) VALUES (?, ?, ?, ?)",
            "instructor",
            "hashed",
            "Instructor",
            true
        );
        Long instructorUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, "instructor");

        jdbcTemplate.update(
            "MERGE INTO classes (class_code, class_name, instructor_user_id) KEY(class_code) VALUES (?, ?, ?)",
            "SEC-TST-CLASS",
            "Security Test Class",
            instructorUserId
        );
        Long classId = jdbcTemplate.queryForObject("SELECT id FROM classes WHERE class_code = ?", Long.class, "SEC-TST-CLASS");
        jdbcTemplate.update("UPDATE classes SET instructor_user_id = ? WHERE id = ?", instructorUserId, classId);

        jdbcTemplate.update(
            "INSERT INTO enrollments (student_id, class_id, enrollment_status, enrolled_at, deleted_at) VALUES (?, ?, 'ENROLLED', CURRENT_TIMESTAMP(), NULL)",
            studentId,
            classId
        );
    }
}
