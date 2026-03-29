package com.instituteops.inventory.domain;

import com.instituteops.security.UserIdentityService;
import com.instituteops.security.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InventoryModuleService {

    private static final List<String> LOSS_REASONS = List.of("SPOILAGE", "DAMAGE", "THEFT", "PREP_WASTE");

    private final InventoryUnitRepository inventoryUnitRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final LossReasonCodeRepository lossReasonCodeRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StockCountRepository stockCountRepository;
    private final StockCountLineRepository stockCountLineRepository;
    private final SystemAlertRepository systemAlertRepository;
    private final ReplenishmentRecommendationRepository replenishmentRecommendationRepository;
    private final UserIdentityService userIdentityService;
    private final UserRepository userRepository;

    public InventoryModuleService(
        InventoryUnitRepository inventoryUnitRepository,
        UnitConversionRepository unitConversionRepository,
        IngredientRepository ingredientRepository,
        InventoryBatchRepository inventoryBatchRepository,
        LossReasonCodeRepository lossReasonCodeRepository,
        InventoryTransactionRepository inventoryTransactionRepository,
        StockCountRepository stockCountRepository,
        StockCountLineRepository stockCountLineRepository,
        SystemAlertRepository systemAlertRepository,
        ReplenishmentRecommendationRepository replenishmentRecommendationRepository,
        UserIdentityService userIdentityService,
        UserRepository userRepository
    ) {
        this.inventoryUnitRepository = inventoryUnitRepository;
        this.unitConversionRepository = unitConversionRepository;
        this.ingredientRepository = ingredientRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.lossReasonCodeRepository = lossReasonCodeRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.stockCountRepository = stockCountRepository;
        this.stockCountLineRepository = stockCountLineRepository;
        this.systemAlertRepository = systemAlertRepository;
        this.replenishmentRecommendationRepository = replenishmentRecommendationRepository;
        this.userIdentityService = userIdentityService;
        this.userRepository = userRepository;
    }

    public List<InventoryUnitEntity> units() {
        return inventoryUnitRepository.findAll();
    }

    public List<UnitConversionEntity> conversions() {
        return unitConversionRepository.findAll();
    }

    public List<IngredientEntity> ingredients() {
        return ingredientRepository.findAll();
    }

    public List<InventoryBatchEntity> batches() {
        return inventoryBatchRepository.findAll();
    }

    public List<InventoryTransactionEntity> transactions() {
        return inventoryTransactionRepository.findTop200ByOrderByTransactionAtDesc();
    }

    public List<StockCountEntity> stockCounts() {
        return stockCountRepository.findAll();
    }

    @Transactional
    public InventoryUnitEntity createUnit(String unitCode, String unitName, String unitType, boolean baseUnit) {
        if (!StringUtils.hasText(unitCode) || !StringUtils.hasText(unitName) || !StringUtils.hasText(unitType)) {
            throw new IllegalArgumentException("unitCode, unitName, unitType are required");
        }
        InventoryUnitEntity entity = new InventoryUnitEntity();
        entity.setUnitCode(unitCode.trim().toUpperCase(Locale.ROOT));
        entity.setUnitName(unitName.trim());
        entity.setUnitType(unitType.trim().toUpperCase(Locale.ROOT));
        entity.setBaseUnit(baseUnit);
        return inventoryUnitRepository.save(entity);
    }

    @Transactional
    public UnitConversionEntity createConversion(Long fromUnitId, Long toUnitId, BigDecimal factor) {
        if (fromUnitId == null || toUnitId == null || factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fromUnitId, toUnitId, factor are required");
        }
        UnitConversionEntity entity = new UnitConversionEntity();
        entity.setFromUnitId(fromUnitId);
        entity.setToUnitId(toUnitId);
        entity.setFactor(factor);
        return unitConversionRepository.save(entity);
    }

    @Transactional
    public IngredientEntity createIngredient(String ingredientCode, String ingredientName, Long defaultUnitId) {
        if (!StringUtils.hasText(ingredientCode) || !StringUtils.hasText(ingredientName) || defaultUnitId == null) {
            throw new IllegalArgumentException("ingredientCode, ingredientName, defaultUnitId are required");
        }
        IngredientEntity entity = new IngredientEntity();
        entity.setIngredientCode(ingredientCode.trim().toUpperCase(Locale.ROOT));
        entity.setIngredientName(ingredientName.trim());
        entity.setDefaultUnitId(defaultUnitId);
        entity.setActive(true);
        return ingredientRepository.save(entity);
    }

    @Transactional
    public InventoryBatchEntity receiveBatch(ReceiveBatchRequest request) {
        IngredientEntity ingredient = ingredientRepository.findById(request.ingredientId())
            .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        BigDecimal normalizedQty = convert(request.quantity(), request.unitId(), ingredient.getDefaultUnitId());
        BigDecimal normalizedCost = request.unitCost().divide(request.quantity(), 6, RoundingMode.HALF_UP)
            .multiply(normalizedQty).setScale(4, RoundingMode.HALF_UP);

        InventoryBatchEntity batch = new InventoryBatchEntity();
        batch.setIngredientId(ingredient.getId());
        batch.setBatchNo(request.batchNo());
        batch.setQuantityReceived(normalizedQty);
        batch.setQuantityAvailable(normalizedQty);
        batch.setUnitId(ingredient.getDefaultUnitId());
        batch.setUnitCost(normalizedCost);
        batch.setReceivedAt(LocalDateTime.now());
        batch.setExpiresAt(request.expiresAt());
        batch.setStatus("AVAILABLE");
        InventoryBatchEntity saved = inventoryBatchRepository.save(batch);

        InventoryTransactionEntity receive = new InventoryTransactionEntity();
        receive.setIngredientId(ingredient.getId());
        receive.setBatchId(saved.getId());
        receive.setTransactionType("RECEIVE");
        receive.setQuantity(normalizedQty);
        receive.setUnitId(ingredient.getDefaultUnitId());
        receive.setUnitCost(normalizedCost);
        receive.setTransactionAt(LocalDateTime.now());
        receive.setCreatedBy(currentUserId());
        inventoryTransactionRepository.save(receive);

        return saved;
    }

    @Transactional
    public List<InventoryTransactionEntity> issueFifo(IssueRequest request) {
        IngredientEntity ingredient = ingredientRepository.findById(request.ingredientId())
            .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));

        String type = request.transactionType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ISSUE", "LOSS", "ADJUSTMENT").contains(type)) {
            throw new IllegalArgumentException("transactionType must be ISSUE, LOSS, or ADJUSTMENT");
        }
        if ("LOSS".equals(type)) {
            if (!StringUtils.hasText(request.lossReasonCode())) {
                throw new IllegalArgumentException("Loss reason is mandatory for LOSS transaction");
            }
            String reason = request.lossReasonCode().trim().toUpperCase(Locale.ROOT);
            if (!LOSS_REASONS.contains(reason)) {
                throw new IllegalArgumentException("Loss reason must be spoilage/damage/theft/prep waste");
            }
        }

        BigDecimal qtyToIssue = convert(request.quantity(), request.unitId(), ingredient.getDefaultUnitId());
        List<InventoryBatchEntity> batches = inventoryBatchRepository.findByIngredientIdOrderByExpiresAtAscReceivedAtAsc(ingredient.getId());
        BigDecimal available = batches.stream().map(InventoryBatchEntity::getQuantityAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(qtyToIssue) < 0) {
            throw new IllegalArgumentException("Insufficient stock for FIFO issue");
        }

        Long lossReasonId = null;
        if (StringUtils.hasText(request.lossReasonCode())) {
            lossReasonId = lossReasonCodeRepository.findByReasonCode(request.lossReasonCode().trim().toUpperCase(Locale.ROOT))
                .map(LossReasonCodeEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid loss reason code"));
        }

        List<InventoryTransactionEntity> created = new ArrayList<>();
        BigDecimal remaining = qtyToIssue;
        for (InventoryBatchEntity batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (batch.getQuantityAvailable().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal pick = batch.getQuantityAvailable().min(remaining).setScale(3, RoundingMode.HALF_UP);
            batch.setQuantityAvailable(batch.getQuantityAvailable().subtract(pick));
            if (batch.getQuantityAvailable().compareTo(BigDecimal.ZERO) == 0) {
                batch.setStatus("DEPLETED");
            }
            inventoryBatchRepository.save(batch);

            InventoryTransactionEntity tx = new InventoryTransactionEntity();
            tx.setIngredientId(ingredient.getId());
            tx.setBatchId(batch.getId());
            tx.setTransactionType(type);
            tx.setQuantity(pick);
            tx.setUnitId(ingredient.getDefaultUnitId());
            tx.setUnitCost(batch.getUnitCost());
            tx.setLossReasonId(lossReasonId);
            tx.setNote(request.note());
            tx.setTransactionAt(LocalDateTime.now());
            tx.setCreatedBy(currentUserId());
            created.add(inventoryTransactionRepository.save(tx));

            remaining = remaining.subtract(pick);
        }
        return created;
    }

    @Transactional
    public StockCountEntity recordStockCount(StockCountRequest request) {
        IngredientEntity ingredient = ingredientRepository.findById(request.ingredientId())
            .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        BigDecimal expected = currentStockQtyInDefaultUnit(ingredient.getId());
        BigDecimal actual = convert(request.actualQty(), request.actualUnitId(), ingredient.getDefaultUnitId());

        BigDecimal avgUnitCost = averageUnitCost(ingredient.getId());
        BigDecimal expectedValue = expected.multiply(avgUnitCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualValue = actual.multiply(avgUnitCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal varianceQty = actual.subtract(expected).setScale(3, RoundingMode.HALF_UP);
        BigDecimal varianceValue = actualValue.subtract(expectedValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal variancePercent = expected.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : varianceQty.multiply(BigDecimal.valueOf(100)).divide(expected, 3, RoundingMode.HALF_UP);

        StockCountEntity count = new StockCountEntity();
        count.setCountNo("SC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        count.setCountedAt(LocalDateTime.now());
        count.setCountedBy(currentUserId());
        count.setVariancePercentThreshold(BigDecimal.valueOf(2));
        count.setVarianceValueThreshold(BigDecimal.valueOf(50));
        count.setStatus("POSTED");
        StockCountEntity saved = stockCountRepository.save(count);

        boolean flagged = variancePercent.abs().compareTo(saved.getVariancePercentThreshold()) > 0
            || varianceValue.abs().compareTo(saved.getVarianceValueThreshold()) > 0;

        StockCountLineEntity line = new StockCountLineEntity();
        line.setStockCountId(saved.getId());
        line.setIngredientId(ingredient.getId());
        line.setExpectedQty(expected);
        line.setActualQty(actual);
        line.setUnitId(ingredient.getDefaultUnitId());
        line.setExpectedValue(expectedValue);
        line.setActualValue(actualValue);
        line.setVarianceQty(varianceQty);
        line.setVarianceValue(varianceValue);
        line.setVariancePercent(variancePercent);
        line.setFlagged(flagged);
        line.setLineNote(flagged ? "Variance exceeds threshold" : "Within threshold");
        stockCountLineRepository.save(line);

        if (flagged) {
            SystemAlertEntity alert = new SystemAlertEntity();
            alert.setAlertType("STOCK_VARIANCE");
            alert.setSeverity("HIGH");
            alert.setEntityType("INGREDIENT");
            alert.setEntityId(ingredient.getId());
            alert.setMessage("Stock count variance exceeds threshold");
            alert.setCreatedAt(LocalDateTime.now());
            systemAlertRepository.save(alert);
        }
        return saved;
    }

    @Transactional
    public void refreshExpiryAndLowStockSignals() {
        LocalDate nearExpiryBoundary = LocalDate.now().plusDays(10);
        for (InventoryBatchEntity batch : inventoryBatchRepository.findAll()) {
            if (batch.getExpiresAt() != null && !batch.getExpiresAt().isAfter(nearExpiryBoundary) && "AVAILABLE".equals(batch.getStatus())) {
                batch.setStatus("QUARANTINED");
                inventoryBatchRepository.save(batch);
            }
        }

        for (IngredientEntity ingredient : ingredientRepository.findAll()) {
            BigDecimal dailyAvg = trailingAverageUsagePerDay(ingredient.getId(), 30);
            BigDecimal sevenDayNeed = dailyAvg.multiply(BigDecimal.valueOf(7));
            BigDecimal stock = currentStockQtyInDefaultUnit(ingredient.getId());
            if (stock.compareTo(sevenDayNeed) < 0) {
                ReplenishmentRecommendationEntity recommendation = new ReplenishmentRecommendationEntity();
                recommendation.setIngredientId(ingredient.getId());
                recommendation.setGeneratedAt(LocalDateTime.now());
                recommendation.setTrailing30dConsumption(dailyAvg.multiply(BigDecimal.valueOf(30)).setScale(3, RoundingMode.HALF_UP));
                recommendation.setAvgDailyUsage(dailyAvg.setScale(3, RoundingMode.HALF_UP));
                recommendation.setSuggestedReorderQty(sevenDayNeed.subtract(stock).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
                recommendation.setUnitId(ingredient.getDefaultUnitId());
                recommendation.setConfidenceScore(BigDecimal.valueOf(80));
                replenishmentRecommendationRepository.save(recommendation);
            }
        }
    }

    public List<StockVarianceView> stockVarianceViews() {
        List<StockVarianceView> views = new ArrayList<>();
        for (StockCountEntity count : stockCountRepository.findAll()) {
            for (StockCountLineEntity line : stockCountLineRepository.findByStockCountId(count.getId())) {
                views.add(new StockVarianceView(
                    count.getCountNo(),
                    line.getIngredientId(),
                    line.getExpectedQty(),
                    line.getActualQty(),
                    line.getVariancePercent(),
                    line.getVarianceValue(),
                    line.isFlagged()
                ));
            }
        }
        views.sort(Comparator.comparing(StockVarianceView::countNo).reversed());
        return views;
    }

    public Map<Long, BigDecimal> currentStockByIngredient() {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (IngredientEntity ingredient : ingredientRepository.findAll()) {
            out.put(ingredient.getId(), currentStockQtyInDefaultUnit(ingredient.getId()));
        }
        return out;
    }

    private BigDecimal currentStockQtyInDefaultUnit(Long ingredientId) {
        return inventoryBatchRepository.findByIngredientIdOrderByExpiresAtAscReceivedAtAsc(ingredientId).stream()
            .map(InventoryBatchEntity::getQuantityAvailable)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal averageUnitCost(Long ingredientId) {
        List<InventoryBatchEntity> batches = inventoryBatchRepository.findByIngredientIdOrderByExpiresAtAscReceivedAtAsc(ingredientId);
        if (batches.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        for (InventoryBatchEntity b : batches) {
            totalCost = totalCost.add(b.getUnitCost().multiply(b.getQuantityReceived()));
            totalQty = totalQty.add(b.getQuantityReceived());
        }
        if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal trailingAverageUsagePerDay(Long ingredientId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
        BigDecimal total = inventoryTransactionRepository.findByIngredientIdOrderByTransactionAtDesc(ingredientId).stream()
            .filter(tx -> tx.getTransactionAt() != null && tx.getTransactionAt().isAfter(cutoff))
            .filter(tx -> List.of("ISSUE", "LOSS").contains(tx.getTransactionType()))
            .map(InventoryTransactionEntity::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal convert(BigDecimal qty, Long fromUnitId, Long toUnitId) {
        if (qty == null || fromUnitId == null || toUnitId == null) {
            throw new IllegalArgumentException("Quantity and unit ids are required");
        }
        if (fromUnitId.equals(toUnitId)) {
            return qty.setScale(3, RoundingMode.HALF_UP);
        }
        Optional<UnitConversionEntity> direct = unitConversionRepository.findByFromUnitIdAndToUnitId(fromUnitId, toUnitId);
        if (direct.isPresent()) {
            return qty.multiply(direct.get().getFactor()).setScale(3, RoundingMode.HALF_UP);
        }
        Optional<UnitConversionEntity> reverse = unitConversionRepository.findByFromUnitIdAndToUnitId(toUnitId, fromUnitId);
        if (reverse.isPresent()) {
            return qty.divide(reverse.get().getFactor(), 3, RoundingMode.HALF_UP);
        }
        throw new IllegalArgumentException("No conversion path between selected units");
    }

    private Long currentUserId() {
        return userIdentityService.resolveCurrentUserId().orElseGet(() -> userRepository.findIdByUsername("inventory").orElse(1L));
    }

    public record ReceiveBatchRequest(Long ingredientId, String batchNo, BigDecimal quantity, Long unitId, BigDecimal unitCost, LocalDate expiresAt) {
    }

    public record IssueRequest(Long ingredientId, String transactionType, BigDecimal quantity, Long unitId, String lossReasonCode, String note) {
    }

    public record StockCountRequest(Long ingredientId, BigDecimal actualQty, Long actualUnitId) {
    }

    public record StockVarianceView(
        String countNo,
        Long ingredientId,
        BigDecimal expectedQty,
        BigDecimal actualQty,
        BigDecimal variancePercent,
        BigDecimal varianceValue,
        boolean flagged
    ) {
    }
}
