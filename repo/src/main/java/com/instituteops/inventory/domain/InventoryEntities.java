package com.instituteops.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_units")
class InventoryUnitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "unit_type", nullable = false)
    private String unitType;

    @Column(name = "base_unit", nullable = false)
    private boolean baseUnit;

    public Long getId() {
        return id;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public boolean isBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(boolean baseUnit) {
        this.baseUnit = baseUnit;
    }
}

@Entity
@Table(name = "unit_conversions")
class UnitConversionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_unit_id", nullable = false)
    private Long fromUnitId;

    @Column(name = "to_unit_id", nullable = false)
    private Long toUnitId;

    @Column(name = "factor", nullable = false)
    private BigDecimal factor;

    public Long getId() {
        return id;
    }

    public Long getFromUnitId() {
        return fromUnitId;
    }

    public void setFromUnitId(Long fromUnitId) {
        this.fromUnitId = fromUnitId;
    }

    public Long getToUnitId() {
        return toUnitId;
    }

    public void setToUnitId(Long toUnitId) {
        this.toUnitId = toUnitId;
    }

    public BigDecimal getFactor() {
        return factor;
    }

    public void setFactor(BigDecimal factor) {
        this.factor = factor;
    }
}

@Entity
@Table(name = "ingredients")
class IngredientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_code", nullable = false)
    private String ingredientCode;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    @Column(name = "default_unit_id", nullable = false)
    private Long defaultUnitId;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public String getIngredientCode() {
        return ingredientCode;
    }

    public void setIngredientCode(String ingredientCode) {
        this.ingredientCode = ingredientCode;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public void setIngredientName(String ingredientName) {
        this.ingredientName = ingredientName;
    }

    public Long getDefaultUnitId() {
        return defaultUnitId;
    }

    public void setDefaultUnitId(Long defaultUnitId) {
        this.defaultUnitId = defaultUnitId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

@Entity
@Table(name = "inventory_batches")
class InventoryBatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "batch_no", nullable = false)
    private String batchNo;

    @Column(name = "quantity_received", nullable = false)
    private BigDecimal quantityReceived;

    @Column(name = "quantity_available", nullable = false)
    private BigDecimal quantityAvailable;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "status", nullable = false)
    private String status;

    public Long getId() {
        return id;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(BigDecimal quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public BigDecimal getQuantityAvailable() {
        return quantityAvailable;
    }

    public void setQuantityAvailable(BigDecimal quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

@Entity
@Table(name = "loss_reason_codes")
class LossReasonCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    public Long getId() {
        return id;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}

@Entity
@Table(name = "inventory_transactions")
class InventoryTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "loss_reason_id")
    private Long lossReasonId;

    @Column(name = "note")
    private String note;

    @Column(name = "transaction_at", nullable = false)
    private LocalDateTime transactionAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Long getLossReasonId() {
        return lossReasonId;
    }

    public void setLossReasonId(Long lossReasonId) {
        this.lossReasonId = lossReasonId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getTransactionAt() {
        return transactionAt;
    }

    public void setTransactionAt(LocalDateTime transactionAt) {
        this.transactionAt = transactionAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}

@Entity
@Table(name = "stock_counts")
class StockCountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_no", nullable = false)
    private String countNo;

    @Column(name = "counted_at", nullable = false)
    private LocalDateTime countedAt;

    @Column(name = "counted_by", nullable = false)
    private Long countedBy;

    @Column(name = "variance_percent_threshold", nullable = false)
    private BigDecimal variancePercentThreshold;

    @Column(name = "variance_value_threshold", nullable = false)
    private BigDecimal varianceValueThreshold;

    @Column(name = "status", nullable = false)
    private String status;

    public Long getId() {
        return id;
    }

    public String getCountNo() {
        return countNo;
    }

    public void setCountNo(String countNo) {
        this.countNo = countNo;
    }

    public LocalDateTime getCountedAt() {
        return countedAt;
    }

    public void setCountedAt(LocalDateTime countedAt) {
        this.countedAt = countedAt;
    }

    public Long getCountedBy() {
        return countedBy;
    }

    public void setCountedBy(Long countedBy) {
        this.countedBy = countedBy;
    }

    public BigDecimal getVariancePercentThreshold() {
        return variancePercentThreshold;
    }

    public void setVariancePercentThreshold(BigDecimal variancePercentThreshold) {
        this.variancePercentThreshold = variancePercentThreshold;
    }

    public BigDecimal getVarianceValueThreshold() {
        return varianceValueThreshold;
    }

    public void setVarianceValueThreshold(BigDecimal varianceValueThreshold) {
        this.varianceValueThreshold = varianceValueThreshold;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

@Entity
@Table(name = "stock_count_lines")
class StockCountLineEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_count_id", nullable = false)
    private Long stockCountId;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "expected_qty", nullable = false)
    private BigDecimal expectedQty;

    @Column(name = "actual_qty", nullable = false)
    private BigDecimal actualQty;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "expected_value", nullable = false)
    private BigDecimal expectedValue;

    @Column(name = "actual_value", nullable = false)
    private BigDecimal actualValue;

    @Column(name = "variance_qty", nullable = false)
    private BigDecimal varianceQty;

    @Column(name = "variance_value", nullable = false)
    private BigDecimal varianceValue;

    @Column(name = "variance_percent", nullable = false)
    private BigDecimal variancePercent;

    @Column(name = "flagged", nullable = false)
    private boolean flagged;

    @Column(name = "line_note")
    private String lineNote;

    public Long getId() {
        return id;
    }

    public Long getStockCountId() {
        return stockCountId;
    }

    public void setStockCountId(Long stockCountId) {
        this.stockCountId = stockCountId;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public BigDecimal getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(BigDecimal expectedQty) {
        this.expectedQty = expectedQty;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(BigDecimal expectedValue) {
        this.expectedValue = expectedValue;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
    }

    public BigDecimal getVarianceQty() {
        return varianceQty;
    }

    public void setVarianceQty(BigDecimal varianceQty) {
        this.varianceQty = varianceQty;
    }

    public BigDecimal getVarianceValue() {
        return varianceValue;
    }

    public void setVarianceValue(BigDecimal varianceValue) {
        this.varianceValue = varianceValue;
    }

    public BigDecimal getVariancePercent() {
        return variancePercent;
    }

    public void setVariancePercent(BigDecimal variancePercent) {
        this.variancePercent = variancePercent;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public String getLineNote() {
        return lineNote;
    }

    public void setLineNote(String lineNote) {
        this.lineNote = lineNote;
    }
}

@Entity
@Table(name = "system_alerts")
class SystemAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

@Entity
@Table(name = "replenishment_recommendations")
class ReplenishmentRecommendationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "trailing_30d_consumption", nullable = false)
    private BigDecimal trailing30dConsumption;

    @Column(name = "avg_daily_usage", nullable = false)
    private BigDecimal avgDailyUsage;

    @Column(name = "suggested_reorder_qty", nullable = false)
    private BigDecimal suggestedReorderQty;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "confidence_score", nullable = false)
    private BigDecimal confidenceScore;

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setTrailing30dConsumption(BigDecimal trailing30dConsumption) {
        this.trailing30dConsumption = trailing30dConsumption;
    }

    public void setAvgDailyUsage(BigDecimal avgDailyUsage) {
        this.avgDailyUsage = avgDailyUsage;
    }

    public void setSuggestedReorderQty(BigDecimal suggestedReorderQty) {
        this.suggestedReorderQty = suggestedReorderQty;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
}
