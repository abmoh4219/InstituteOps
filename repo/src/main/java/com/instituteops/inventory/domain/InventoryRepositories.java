package com.instituteops.inventory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryUnitRepository extends JpaRepository<InventoryUnitEntity, Long> {

    Optional<InventoryUnitEntity> findByUnitCode(String unitCode);
}

interface UnitConversionRepository extends JpaRepository<UnitConversionEntity, Long> {

    Optional<UnitConversionEntity> findByFromUnitIdAndToUnitId(Long fromUnitId, Long toUnitId);
}

interface IngredientRepository extends JpaRepository<IngredientEntity, Long> {

    Optional<IngredientEntity> findByIngredientCode(String ingredientCode);
}

interface InventoryBatchRepository extends JpaRepository<InventoryBatchEntity, Long> {

    List<InventoryBatchEntity> findByIngredientIdOrderByExpiresAtAscReceivedAtAsc(Long ingredientId);
}

interface LossReasonCodeRepository extends JpaRepository<LossReasonCodeEntity, Long> {

    Optional<LossReasonCodeEntity> findByReasonCode(String reasonCode);
}

interface InventoryTransactionRepository extends JpaRepository<InventoryTransactionEntity, Long> {

    List<InventoryTransactionEntity> findTop200ByOrderByTransactionAtDesc();

    List<InventoryTransactionEntity> findByIngredientIdOrderByTransactionAtDesc(Long ingredientId);
}

interface StockCountRepository extends JpaRepository<StockCountEntity, Long> {
}

interface StockCountLineRepository extends JpaRepository<StockCountLineEntity, Long> {

    List<StockCountLineEntity> findByStockCountId(Long stockCountId);
}

interface SystemAlertRepository extends JpaRepository<SystemAlertEntity, Long> {
}

interface ReplenishmentRecommendationRepository extends JpaRepository<ReplenishmentRecommendationEntity, Long> {
}
