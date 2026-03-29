package com.instituteops.recommender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommender_events")
class RecommenderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "item_type", nullable = false)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "event_value", nullable = false)
    private BigDecimal eventValue;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getEventValue() {
        return eventValue;
    }

    public void setEventValue(BigDecimal eventValue) {
        this.eventValue = eventValue;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

@Entity
@Table(name = "recommender_models")
class RecommenderModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_code", nullable = false)
    private String modelCode;

    @Column(name = "algorithm_family", nullable = false)
    private String algorithmFamily;

    @Column(name = "similarity_metric", nullable = false)
    private String similarityMetric;

    @Column(name = "time_decay_half_life_days", nullable = false)
    private Integer timeDecayHalfLifeDays;

    @Column(name = "popularity_penalty", nullable = false)
    private BigDecimal popularityPenalty;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getAlgorithmFamily() {
        return algorithmFamily;
    }

    public void setAlgorithmFamily(String algorithmFamily) {
        this.algorithmFamily = algorithmFamily;
    }

    public String getSimilarityMetric() {
        return similarityMetric;
    }

    public void setSimilarityMetric(String similarityMetric) {
        this.similarityMetric = similarityMetric;
    }

    public Integer getTimeDecayHalfLifeDays() {
        return timeDecayHalfLifeDays;
    }

    public void setTimeDecayHalfLifeDays(Integer timeDecayHalfLifeDays) {
        this.timeDecayHalfLifeDays = timeDecayHalfLifeDays;
    }

    public BigDecimal getPopularityPenalty() {
        return popularityPenalty;
    }

    public void setPopularityPenalty(BigDecimal popularityPenalty) {
        this.popularityPenalty = popularityPenalty;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

@Entity
@Table(name = "recommender_model_versions")
class RecommenderModelVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "trained_from", nullable = false)
    private LocalDateTime trainedFrom;

    @Column(name = "trained_to", nullable = false)
    private LocalDateTime trainedTo;

    @Column(name = "config_json", nullable = false)
    private String configJson;

    @Column(name = "quality_metrics_json")
    private String qualityMetricsJson;

    @Column(name = "training_status", nullable = false)
    private String trainingStatus;

    @Column(name = "rollback_of_version")
    private Long rollbackOfVersion;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public LocalDateTime getTrainedFrom() {
        return trainedFrom;
    }

    public void setTrainedFrom(LocalDateTime trainedFrom) {
        this.trainedFrom = trainedFrom;
    }

    public LocalDateTime getTrainedTo() {
        return trainedTo;
    }

    public void setTrainedTo(LocalDateTime trainedTo) {
        this.trainedTo = trainedTo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getQualityMetricsJson() {
        return qualityMetricsJson;
    }

    public void setQualityMetricsJson(String qualityMetricsJson) {
        this.qualityMetricsJson = qualityMetricsJson;
    }

    public String getTrainingStatus() {
        return trainingStatus;
    }

    public void setTrainingStatus(String trainingStatus) {
        this.trainingStatus = trainingStatus;
    }

    public Long getRollbackOfVersion() {
        return rollbackOfVersion;
    }

    public void setRollbackOfVersion(Long rollbackOfVersion) {
        this.rollbackOfVersion = rollbackOfVersion;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

@Entity
@Table(name = "recommender_recommendations")
class RecommenderRecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_id", nullable = false)
    private Long modelVersionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "item_type", nullable = false)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public Long getId() {
        return id;
    }

    public Long getModelVersionId() {
        return modelVersionId;
    }

    public void setModelVersionId(Long modelVersionId) {
        this.modelVersionId = modelVersionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public void setRankNo(Integer rankNo) {
        this.rankNo = rankNo;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}

@Entity
@Table(name = "recommender_incremental_updates")
class RecommenderIncrementalUpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_id", nullable = false)
    private Long modelVersionId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "details")
    private String details;

    public Long getId() {
        return id;
    }

    public Long getModelVersionId() {
        return modelVersionId;
    }

    public void setModelVersionId(Long modelVersionId) {
        this.modelVersionId = modelVersionId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
