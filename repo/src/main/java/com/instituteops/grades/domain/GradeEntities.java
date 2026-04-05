package com.instituteops.grades.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_rule_sets")
class GradeRuleSetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruleset_code", nullable = false)
    private String rulesetCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    public Long getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public String getRulesetCode() {
        return rulesetCode;
    }

    public String getName() {
        return name;
    }
}

@Entity
@Table(name = "grade_rule_versions")
class GradeRuleVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruleset_id", nullable = false)
    private Long rulesetId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "rule_json", nullable = false, columnDefinition = "json")
    private String ruleJson;

    public Long getId() {
        return id;
    }

    public Long getRulesetId() {
        return rulesetId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getRuleJson() {
        return ruleJson;
    }
}

@Entity
@Table(name = "override_reason_codes")
class OverrideReasonCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "immutable", nullable = false)
    private boolean immutable;

    public Long getId() {
        return id;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public String getDescription() {
        return description;
    }
}

@Entity
@Table(name = "grade_ledger_entries")
@org.hibernate.annotations.Immutable
class GradeLedgerEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "class_session_id")
    private Long classSessionId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "assessment_key", nullable = false)
    private String assessmentKey;

    @Column(name = "raw_score", nullable = false)
    private BigDecimal rawScore;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "grade_letter")
    private String gradeLetter;

    @Column(name = "credits_earned", nullable = false)
    private BigDecimal creditsEarned;

    @Column(name = "gpa_points", nullable = false)
    private BigDecimal gpaPoints;

    @Column(name = "ruleset_id", nullable = false)
    private Long rulesetId;

    @Column(name = "rule_version_id", nullable = false)
    private Long ruleVersionId;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "operation_reason_code")
    private String operationReasonCode;

    @Column(name = "previous_entry_id")
    private Long previousEntryId;

    @Column(name = "delta_score")
    private BigDecimal deltaScore;

    @Column(name = "delta_credits")
    private BigDecimal deltaCredits;

    @Column(name = "delta_gpa_points")
    private BigDecimal deltaGpaPoints;

    @Column(name = "entered_by", nullable = false)
    private Long enteredBy;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "append_only_marker", nullable = false)
    private String appendOnlyMarker;

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getClassSessionId() {
        return classSessionId;
    }

    public void setClassSessionId(Long classSessionId) {
        this.classSessionId = classSessionId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getAssessmentKey() {
        return assessmentKey;
    }

    public void setAssessmentKey(String assessmentKey) {
        this.assessmentKey = assessmentKey;
    }

    public BigDecimal getRawScore() {
        return rawScore;
    }

    public void setRawScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public String getGradeLetter() {
        return gradeLetter;
    }

    public void setGradeLetter(String gradeLetter) {
        this.gradeLetter = gradeLetter;
    }

    public BigDecimal getCreditsEarned() {
        return creditsEarned;
    }

    public void setCreditsEarned(BigDecimal creditsEarned) {
        this.creditsEarned = creditsEarned;
    }

    public BigDecimal getGpaPoints() {
        return gpaPoints;
    }

    public void setGpaPoints(BigDecimal gpaPoints) {
        this.gpaPoints = gpaPoints;
    }

    public Long getRulesetId() {
        return rulesetId;
    }

    public void setRulesetId(Long rulesetId) {
        this.rulesetId = rulesetId;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationReasonCode() {
        return operationReasonCode;
    }

    public void setOperationReasonCode(String operationReasonCode) {
        this.operationReasonCode = operationReasonCode;
    }

    public Long getPreviousEntryId() {
        return previousEntryId;
    }

    public void setPreviousEntryId(Long previousEntryId) {
        this.previousEntryId = previousEntryId;
    }

    public BigDecimal getDeltaScore() {
        return deltaScore;
    }

    public void setDeltaScore(BigDecimal deltaScore) {
        this.deltaScore = deltaScore;
    }

    public BigDecimal getDeltaCredits() {
        return deltaCredits;
    }

    public void setDeltaCredits(BigDecimal deltaCredits) {
        this.deltaCredits = deltaCredits;
    }

    public BigDecimal getDeltaGpaPoints() {
        return deltaGpaPoints;
    }

    public void setDeltaGpaPoints(BigDecimal deltaGpaPoints) {
        this.deltaGpaPoints = deltaGpaPoints;
    }

    public Long getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(Long enteredBy) {
        this.enteredBy = enteredBy;
    }

    public LocalDateTime getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(LocalDateTime enteredAt) {
        this.enteredAt = enteredAt;
    }

    public String getAppendOnlyMarker() {
        return appendOnlyMarker;
    }

    public void setAppendOnlyMarker(String appendOnlyMarker) {
        this.appendOnlyMarker = appendOnlyMarker;
    }
}

@Entity
@Table(name = "grade_recalculations")
class GradeRecalculationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "triggered_by", nullable = false)
    private Long triggeredBy;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "scope_json", nullable = false, columnDefinition = "json")
    private String scopeJson;

    @Column(name = "deterministic_hash", nullable = false)
    private String deterministicHash;

    @Column(name = "status", nullable = false)
    private String status;

    public Long getId() {
        return id;
    }

    public String getDeterministicHash() {
        return deterministicHash;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public void setTriggeredBy(Long triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public void setDeterministicHash(String deterministicHash) {
        this.deterministicHash = deterministicHash;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

@Entity
@Table(name = "grade_recalculation_deltas")
class GradeRecalculationDeltaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recalculation_id", nullable = false)
    private Long recalculationId;

    @Column(name = "grade_ledger_entry_id", nullable = false)
    private Long gradeLedgerEntryId;

    @Column(name = "previous_result_json", nullable = false, columnDefinition = "json")
    private String previousResultJson;

    @Column(name = "new_result_json", nullable = false, columnDefinition = "json")
    private String newResultJson;

    @Column(name = "delta_json", nullable = false, columnDefinition = "json")
    private String deltaJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setRecalculationId(Long recalculationId) {
        this.recalculationId = recalculationId;
    }

    public void setGradeLedgerEntryId(Long gradeLedgerEntryId) {
        this.gradeLedgerEntryId = gradeLedgerEntryId;
    }

    public void setPreviousResultJson(String previousResultJson) {
        this.previousResultJson = previousResultJson;
    }

    public void setNewResultJson(String newResultJson) {
        this.newResultJson = newResultJson;
    }

    public void setDeltaJson(String deltaJson) {
        this.deltaJson = deltaJson;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
