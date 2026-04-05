package com.instituteops.governance.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instituteops.security.UserIdentityService;
import com.instituteops.security.repo.UserRepository;
import com.instituteops.student.model.StudentProfileEntity;
import com.instituteops.student.model.StudentProfileLookupRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GovernanceService {

    private static final String STUDENT_ENTITY = "STUDENT";
    private static final String[] STUDENT_IMPORT_HEADER = {
        "student_no",
        "first_name",
        "last_name",
        "preferred_name",
        "date_of_birth",
        "contact_email",
        "contact_phone",
        "contact_address",
        "emergency_contact"
    };
    private static final String[] STUDENT_LEGACY_EXPORT_HEADER = {
        "student_no",
        "first_name",
        "last_name",
        "preferred_name",
        "date_of_birth",
        "status",
        "masked_email",
        "masked_phone"
    };

    private final StudentProfileLookupRepository studentProfileRepository;
    private final DuplicateDetectionResultRepository duplicateDetectionResultRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final RecycleBinRepository recycleBinRepository;
    private final BulkJobRepository bulkJobRepository;
    private final ConsistencyIssueRepository consistencyIssueRepository;
    private final GovernanceAuditService governanceAuditService;
    private final UserIdentityService userIdentityService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public GovernanceService(
        StudentProfileLookupRepository studentProfileRepository,
        DuplicateDetectionResultRepository duplicateDetectionResultRepository,
        ChangeHistoryRepository changeHistoryRepository,
        RecycleBinRepository recycleBinRepository,
        BulkJobRepository bulkJobRepository,
        ConsistencyIssueRepository consistencyIssueRepository,
        GovernanceAuditService governanceAuditService,
        UserIdentityService userIdentityService,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate
    ) {
        this.studentProfileRepository = studentProfileRepository;
        this.duplicateDetectionResultRepository = duplicateDetectionResultRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.recycleBinRepository = recycleBinRepository;
        this.bulkJobRepository = bulkJobRepository;
        this.consistencyIssueRepository = consistencyIssueRepository;
        this.governanceAuditService = governanceAuditService;
        this.userIdentityService = userIdentityService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String exportStudentsCsv() {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(STUDENT_IMPORT_HEADER).build())) {
            for (StudentProfileEntity student : studentProfileRepository.findByDeletedAtIsNullOrderByLastNameAscFirstNameAsc()) {
                printer.printRecord(
                    nullable(student.getStudentNo()),
                    nullable(student.getFirstName()),
                    nullable(student.getLastName()),
                    nullable(student.getPreferredName()),
                    student.getDateOfBirth() == null ? null : student.getDateOfBirth().toString(),
                    nullable(student.getContactEmail()),
                    nullable(student.getContactPhone()),
                    nullable(student.getContactAddress()),
                    nullable(student.getEmergencyContact())
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to export students CSV", ex);
        }
        return out.toString();
    }

    @Transactional
    public CsvImportResult importStudentsCsv(String fileName, String csvText, boolean allowUpdate) {
        if (!StringUtils.hasText(csvText)) {
            throw new IllegalArgumentException("CSV payload is required");
        }
        CsvSchema schema;
        List<CSVRecord> records;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build()
            .parse(new StringReader(csvText))) {
            records = parser.getRecords();
            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV must contain a header and at least one row");
            }
            schema = detectSchema(parser.getHeaderMap());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to parse CSV payload", ex);
        }

        int created = 0;
        int updated = 0;
        int failed = 0;
        int total = records.size();

        BulkJobEntity job = new BulkJobEntity();
        job.setJobType("IMPORT");
        job.setEntityType(STUDENT_ENTITY);
        job.setFileName(StringUtils.hasText(fileName) ? fileName : "students-import.csv");
        job.setFileChecksum(sha256(csvText));
        job.setStartedBy(currentUserId());
        job.setStartedAt(LocalDateTime.now());
        job.setStatus("RUNNING");
        job = bulkJobRepository.save(job);

        for (int i = 0; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            try {
                String studentNo = required(readField(record, schema.studentNo()), "student_no");
                String firstName = required(readField(record, schema.firstName()), "first_name");
                String lastName = required(readField(record, schema.lastName()), "last_name");
                LocalDate dob = LocalDate.parse(required(readField(record, schema.dateOfBirth()), "date_of_birth"));
                StudentProfileEntity existing = studentProfileRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull(studentNo).orElse(null);
                if (existing != null) {
                    if (!allowUpdate) {
                        throw new IllegalArgumentException("student_no already exists");
                    }
                    Map<String, Object> before = studentSnapshot(existing);
                    existing.setFirstName(firstName);
                    existing.setLastName(lastName);
                    existing.setPreferredName(nullable(readField(record, schema.preferredName())));
                    existing.setDateOfBirth(dob);
                    String email = nullable(readField(record, schema.contactEmail()));
                    String phone = nullable(readField(record, schema.contactPhone()));
                    existing.setContactEmail(email);
                    existing.setContactPhone(phone);
                    existing.setContactAddress(nullable(readField(record, schema.contactAddress())));
                    existing.setEmergencyContact(nullable(readField(record, schema.emergencyContact())));
                    existing.setMaskedEmail(maskEmail(email));
                    existing.setMaskedPhone(maskPhone(phone));
                    studentProfileRepository.save(existing);
                    governanceAuditService.recordChange(STUDENT_ENTITY, existing.getId(), "UPDATE", before, studentSnapshot(existing), "CSV_IMPORT_UPDATE");
                    updated++;
                } else {
                    StudentProfileEntity createdStudent = new StudentProfileEntity();
                    createdStudent.setStudentNo(studentNo.trim());
                    createdStudent.setFirstName(firstName.trim());
                    createdStudent.setLastName(lastName.trim());
                    createdStudent.setPreferredName(nullable(readField(record, schema.preferredName())));
                    createdStudent.setDateOfBirth(dob);
                    createdStudent.setStatus("ACTIVE");
                    String newEmail = nullable(readField(record, schema.contactEmail()));
                    String newPhone = nullable(readField(record, schema.contactPhone()));
                    createdStudent.setContactEmail(newEmail);
                    createdStudent.setContactPhone(newPhone);
                    createdStudent.setContactAddress(nullable(readField(record, schema.contactAddress())));
                    createdStudent.setEmergencyContact(nullable(readField(record, schema.emergencyContact())));
                    createdStudent.setMaskedEmail(maskEmail(newEmail));
                    createdStudent.setMaskedPhone(maskPhone(newPhone));
                    studentProfileRepository.save(createdStudent);
                    governanceAuditService.recordChange(STUDENT_ENTITY, createdStudent.getId(), "CREATE", null, studentSnapshot(createdStudent), "CSV_IMPORT_CREATE");
                    created++;
                }
            } catch (Exception ex) {
                failed++;
                ConsistencyIssueEntity issue = new ConsistencyIssueEntity();
                issue.setIssueType("CSV_IMPORT_ROW_ERROR");
                issue.setEntityType(STUDENT_ENTITY);
                issue.setEntityId((long) (i + 1));
                issue.setIssueDetails(toJson(Map.of("row", i + 1, "error", ex.getMessage())));
                issue.setDetectedAt(LocalDateTime.now());
                issue.setResolved(false);
                consistencyIssueRepository.save(issue);
            }
        }

        detectStudentDuplicates();

        job.setCompletedAt(LocalDateTime.now());
        job.setStatus(failed == 0 ? "COMPLETED" : (created + updated > 0 ? "PARTIAL" : "FAILED"));
        job.setSummary(toJson(Map.of("total", total, "created", created, "updated", updated, "failed", failed)));
        bulkJobRepository.save(job);

        return new CsvImportResult(job.getId(), total, created, updated, failed, job.getStatus());
    }

    @Transactional
    public List<DuplicateCandidate> detectStudentDuplicates() {
        duplicateDetectionResultRepository.deleteByReviewedFalse();
        List<StudentProfileEntity> students = studentProfileRepository.findByDeletedAtIsNullOrderByLastNameAscFirstNameAsc();
        for (int i = 0; i < students.size(); i++) {
            for (int j = i + 1; j < students.size(); j++) {
                StudentProfileEntity left = students.get(i);
                StudentProfileEntity right = students.get(j);
                if (!left.getDateOfBirth().equals(right.getDateOfBirth())) {
                    continue;
                }
                String leftName = normalizeName(left);
                String rightName = normalizeName(right);
                if (leftName.equals(rightName)) {
                    persistDuplicate(left, right, "EXACT", BigDecimal.valueOf(100), "first_name,last_name,date_of_birth");
                    continue;
                }
                int distance = levenshtein(leftName, rightName);
                if (distance <= 2) {
                    persistDuplicate(left, right, "FUZZY", BigDecimal.valueOf(Math.max(70, 95 - (distance * 10))), "name,date_of_birth");
                }
            }
        }

        return duplicateDetectionResultRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(d -> new DuplicateCandidate(d.getId(), d.getMatchMode(), d.getSourceEntityId(), d.getMatchedEntityId(), d.getScore(), d.getMatchedOn()))
            .toList();
    }

    public List<DuplicateCandidate> latestDuplicateCandidates() {
        return duplicateDetectionResultRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(d -> new DuplicateCandidate(d.getId(), d.getMatchMode(), d.getSourceEntityId(), d.getMatchedEntityId(), d.getScore(), d.getMatchedOn()))
            .toList();
    }

    @Transactional
    public void markDuplicateReviewed(Long duplicateId) {
        DuplicateDetectionResultEntity entity = duplicateDetectionResultRepository.findById(duplicateId)
            .orElseThrow(() -> new IllegalArgumentException("Duplicate candidate not found"));
        entity.setReviewed(true);
        duplicateDetectionResultRepository.save(entity);
    }

    public List<ChangeEntry> changeHistory(String entityType, Long entityId) {
        return changeHistoryRepository.findTop200ByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType.toUpperCase(Locale.ROOT), entityId)
            .stream()
            .map(c -> new ChangeEntry(c.getId(), c.getOperation(), c.getChangedBy(), c.getChangedAt(), c.getOldData(), c.getNewData(), c.getReasonCode()))
            .toList();
    }

    public List<RecycleBinView> recycleBin() {
        return recycleBinRepository.findByRestoredFalseOrderByDeletedAtDesc().stream()
            .map(r -> new RecycleBinView(r.getId(), r.getEntityType(), r.getEntityId(), r.getDeletedBy(), r.getDeletedAt(), r.getPurgeAfter(), r.getPayload()))
            .toList();
    }

    @Transactional
    public void restoreFromRecycleBin(Long recycleId) {
        RecycleBinEntity entry = recycleBinRepository.findById(recycleId)
            .orElseThrow(() -> new IllegalArgumentException("Recycle record not found"));
        if (entry.isRestored()) {
            throw new IllegalStateException("Recycle record already restored");
        }
        if (!STUDENT_ENTITY.equals(entry.getEntityType())) {
            throw new IllegalArgumentException("Unsupported recycle entity type: " + entry.getEntityType());
        }

        StudentProfileEntity student = studentProfileRepository.findById(entry.getEntityId())
            .orElseThrow(() -> new IllegalArgumentException("Student not found for recycle record"));
        student.setDeletedAt(null);
        student.setDeletedBy(null);
        if (!"GRADUATED".equals(student.getStatus())) {
            student.setStatus("ACTIVE");
        }
        studentProfileRepository.save(student);

        governanceAuditService.markRestored(STUDENT_ENTITY, student.getId());
        governanceAuditService.recordChange(STUDENT_ENTITY, student.getId(), "RESTORE", null, studentSnapshot(student), "RECYCLE_RESTORE");
    }

    @Transactional
    public int purgeExpiredRecycleBin() {
        int purged = 0;
        for (RecycleBinEntity entry : recycleBinRepository.findByRestoredFalseAndPurgeAfterBefore(LocalDateTime.now())) {
            if (STUDENT_ENTITY.equals(entry.getEntityType())) {
                studentProfileRepository.findById(entry.getEntityId()).ifPresent(student -> {
                    Long sid = student.getId();
                    jdbcTemplate.update("DELETE FROM homework_attachments WHERE student_id = ?", sid);
                    jdbcTemplate.update("DELETE FROM instructor_comments WHERE student_id = ?", sid);
                    jdbcTemplate.update("DELETE FROM payment_transactions WHERE student_id = ?", sid);
                    List<Long> enrollmentIds = jdbcTemplate.queryForList(
                        "SELECT id FROM enrollments WHERE student_id = ?", Long.class, sid);
                    if (!enrollmentIds.isEmpty()) {
                        for (Long eid : enrollmentIds) {
                            jdbcTemplate.update("DELETE FROM attendance_records WHERE enrollment_id = ?", eid);
                        }
                        jdbcTemplate.update("DELETE FROM enrollments WHERE student_id = ?", sid);
                    }
                    jdbcTemplate.update("DELETE FROM grade_ledger_entries WHERE student_id = ?", sid);
                    studentProfileRepository.delete(student);
                });
                purged++;
            }
            recycleBinRepository.delete(entry);
        }
        return purged;
    }

    public void assertStudentCanAccessOwnHistory(Authentication authentication, Long studentId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        if (hasAnyRole(authentication, "ROLE_SYSTEM_ADMIN", "ROLE_REGISTRAR_FINANCE_CLERK")) {
            return;
        }

        if (hasAnyRole(authentication, "ROLE_STUDENT")) {
            StudentProfileEntity mine = studentProfileRepository.findByStudentNoIgnoreCaseAndDeletedAtIsNull(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("No student profile is bound to this principal"));
            if (!mine.getId().equals(studentId)) {
                throw new AccessDeniedException("Cross-student access denied");
            }
            return;
        }

        if (hasAnyRole(authentication, "ROLE_INSTRUCTOR")) {
            Long instructorUserId = userRepository.findIdByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Instructor account is not linked to an active user"));
            if (instructorCanAccessStudent(instructorUserId, studentId)) {
                return;
            }
            throw new AccessDeniedException("Instructor can only access governance history for assigned students");
        }

        throw new AccessDeniedException("Role is not permitted to access governance history");
    }

    @Transactional
    public List<ConsistencyIssueView> runConsistencyScan() {
        consistencyIssueRepository.deleteByResolvedFalseAndIssueTypeIn(List.of("STUDENT_SOFT_DELETE_STATUS_MISMATCH", "ENROLLMENT_INACTIVE_STUDENT"));

        jdbcTemplate.query(
            """
                SELECT id, status
                FROM students
                WHERE deleted_at IS NOT NULL
                  AND status NOT IN ('INACTIVE', 'WITHDRAWN')
            """,
            rs -> {
                ConsistencyIssueEntity issue = new ConsistencyIssueEntity();
                issue.setIssueType("STUDENT_SOFT_DELETE_STATUS_MISMATCH");
                issue.setEntityType(STUDENT_ENTITY);
                issue.setEntityId(rs.getLong("id"));
                issue.setIssueDetails(toJson(Map.of(
                    "studentId", rs.getLong("id"),
                    "status", rs.getString("status"),
                    "message", "Soft-deleted student must be INACTIVE or WITHDRAWN"
                )));
                issue.setDetectedAt(LocalDateTime.now());
                issue.setResolved(false);
                consistencyIssueRepository.save(issue);
            }
        );

        jdbcTemplate.query(
            """
                SELECT e.id, e.student_id, e.class_id, e.enrollment_status
                FROM enrollments e
                JOIN students s ON s.id = e.student_id
                WHERE e.deleted_at IS NULL
                  AND s.deleted_at IS NOT NULL
                  AND e.enrollment_status IN ('ENROLLED', 'WAITLISTED', 'COMPLETED')
            """,
            rs -> {
                ConsistencyIssueEntity issue = new ConsistencyIssueEntity();
                issue.setIssueType("ENROLLMENT_INACTIVE_STUDENT");
                issue.setEntityType("ENROLLMENT");
                issue.setEntityId(rs.getLong("id"));
                issue.setIssueDetails(toJson(Map.of(
                    "enrollmentId", rs.getLong("id"),
                    "studentId", rs.getLong("student_id"),
                    "classId", rs.getLong("class_id"),
                    "enrollmentStatus", rs.getString("enrollment_status"),
                    "message", "Active enrollment points to soft-deleted student"
                )));
                issue.setDetectedAt(LocalDateTime.now());
                issue.setResolved(false);
                consistencyIssueRepository.save(issue);
            }
        );

        return latestConsistencyIssues();
    }

    public List<ConsistencyIssueView> latestConsistencyIssues() {
        return consistencyIssueRepository.findTop200ByOrderByDetectedAtDesc().stream()
            .map(i -> new ConsistencyIssueView(i.getId(), i.getIssueType(), i.getEntityType(), i.getEntityId(), i.getIssueDetails(), i.getDetectedAt(), i.isResolved()))
            .toList();
    }

    private void persistDuplicate(StudentProfileEntity source, StudentProfileEntity matched, String mode, BigDecimal score, String matchedOn) {
        DuplicateDetectionResultEntity entity = new DuplicateDetectionResultEntity();
        entity.setSourceEntityType(STUDENT_ENTITY);
        entity.setSourceEntityId(source.getId());
        entity.setMatchedEntityType(STUDENT_ENTITY);
        entity.setMatchedEntityId(matched.getId());
        entity.setMatchMode(mode);
        entity.setScore(score);
        entity.setMatchedOn(matchedOn);
        entity.setReviewed(false);
        entity.setCreatedAt(LocalDateTime.now());
        duplicateDetectionResultRepository.save(entity);
    }

    private static String normalizeName(StudentProfileEntity student) {
        return (student.getFirstName() + " " + student.getLastName()).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String nullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CsvSchema detectSchema(Map<String, Integer> headerMap) {
        List<String> headers = headerMap.keySet().stream().map(h -> h.toLowerCase(Locale.ROOT).trim()).toList();
        if (headers.containsAll(Arrays.asList(STUDENT_IMPORT_HEADER))) {
            return new CsvSchema(
                "student_no",
                "first_name",
                "last_name",
                "preferred_name",
                "date_of_birth",
                "contact_email",
                "contact_phone",
                "contact_address",
                "emergency_contact"
            );
        }
        if (headers.containsAll(Arrays.asList(STUDENT_LEGACY_EXPORT_HEADER))) {
            return new CsvSchema(
                "student_no",
                "first_name",
                "last_name",
                "preferred_name",
                "date_of_birth",
                "masked_email",
                "masked_phone",
                "contact_address",
                "emergency_contact"
            );
        }
        throw new IllegalArgumentException("Unsupported CSV header");
    }

    private static String readField(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            return null;
        }
        return record.get(header);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.length() <= 2 ? "**" : local.substring(0, 2) + "***";
        return masked + "@" + parts[1];
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("\\D", "");
        if (normalized.length() <= 4) {
            return "****";
        }
        return "***-***-" + normalized.substring(normalized.length() - 4);
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private Long currentUserId() {
        return userIdentityService.resolveCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("Authenticated user context is required but not available"));
    }

    private static boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null) {
            return false;
        }
        Set<String> granted = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        for (String role : roles) {
            if (granted.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean instructorCanAccessStudent(Long instructorUserId, Long studentId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM enrollments e
                JOIN classes c ON c.id = e.class_id
                WHERE e.student_id = ?
                  AND e.deleted_at IS NULL
                  AND e.enrollment_status IN ('ENROLLED', 'WAITLISTED', 'COMPLETED')
                  AND c.instructor_user_id = ?
            """,
            Integer.class,
            studentId,
            instructorUserId
        );
        return count != null && count > 0;
    }

    private Map<String, Object> studentSnapshot(StudentProfileEntity student) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", student.getId());
        map.put("studentNo", student.getStudentNo());
        map.put("firstName", student.getFirstName());
        map.put("lastName", student.getLastName());
        map.put("preferredName", student.getPreferredName());
        map.put("dateOfBirth", student.getDateOfBirth());
        map.put("status", student.getStatus());
        map.put("deletedAt", student.getDeletedAt());
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize governance payload", ex);
        }
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute checksum", ex);
        }
    }

    public record CsvImportResult(Long jobId, int totalRows, int createdRows, int updatedRows, int failedRows, String status) {
    }

    public record DuplicateCandidate(Long id, String matchMode, Long sourceEntityId, Long matchedEntityId, BigDecimal score, String matchedOn) {
    }

    public record ChangeEntry(Long id, String operation, Long changedBy, LocalDateTime changedAt, String oldData, String newData, String reasonCode) {
    }

    public record RecycleBinView(
        Long id,
        String entityType,
        Long entityId,
        Long deletedBy,
        LocalDateTime deletedAt,
        LocalDateTime purgeAfter,
        String payload
    ) {
    }

    public record ConsistencyIssueView(
        Long id,
        String issueType,
        String entityType,
        Long entityId,
        String issueDetails,
        LocalDateTime detectedAt,
        boolean resolved
    ) {
    }

    private record CsvSchema(
        String studentNo,
        String firstName,
        String lastName,
        String preferredName,
        String dateOfBirth,
        String contactEmail,
        String contactPhone,
        String contactAddress,
        String emergencyContact
    ) {
    }
}
