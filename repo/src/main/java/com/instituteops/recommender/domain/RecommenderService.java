package com.instituteops.recommender.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instituteops.security.UserIdentityService;
import com.instituteops.security.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecommenderService {

    private static final int DEFAULT_TOP_K = 5;

    private final RecommenderEventRepository eventRepository;
    private final RecommenderModelRepository modelRepository;
    private final RecommenderModelVersionRepository versionRepository;
    private final RecommenderRecommendationRepository recommendationRepository;
    private final RecommenderIncrementalUpdateRepository incrementalUpdateRepository;
    private final UserIdentityService userIdentityService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommenderService(
        RecommenderEventRepository eventRepository,
        RecommenderModelRepository modelRepository,
        RecommenderModelVersionRepository versionRepository,
        RecommenderRecommendationRepository recommendationRepository,
        RecommenderIncrementalUpdateRepository incrementalUpdateRepository,
        UserIdentityService userIdentityService,
        UserRepository userRepository,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.recommendationRepository = recommendationRepository;
        this.incrementalUpdateRepository = incrementalUpdateRepository;
        this.userIdentityService = userIdentityService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommenderEventEntity recordEvent(RecordEventRequest request) {
        if (!StringUtils.hasText(request.eventType()) || !StringUtils.hasText(request.itemType()) || request.itemId() == null) {
            throw new IllegalArgumentException("eventType, itemType and itemId are required");
        }
        RecommenderEventEntity event = new RecommenderEventEntity();
        event.setEventType(request.eventType().trim().toUpperCase(Locale.ROOT));
        event.setStudentId(request.studentId());
        event.setItemType(request.itemType().trim().toUpperCase(Locale.ROOT));
        event.setItemId(request.itemId());
        event.setEventValue(request.eventValue() == null ? BigDecimal.ONE : request.eventValue());
        event.setOccurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        event.setSource(StringUtils.hasText(request.source()) ? request.source().trim().toUpperCase(Locale.ROOT) : "LOCAL");
        event.setProcessed(false);
        return eventRepository.save(event);
    }

    @Transactional
    public TrainingResult trainNewVersion(String modelCode, Integer topK) {
        RecommenderModelEntity model = modelRepository.findByModelCode(modelCode)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelCode));

        RecommenderModelVersionEntity version = new RecommenderModelVersionEntity();
        version.setModelId(model.getId());
        version.setVersionNo(nextVersionNo(model.getId()));
        version.setTrainedFrom(LocalDateTime.now().minusDays(180));
        version.setTrainedTo(LocalDateTime.now());
        version.setConfigJson(writeJson(Map.of(
            "algorithmFamily", model.getAlgorithmFamily(),
            "similarityMetric", model.getSimilarityMetric(),
            "timeDecayHalfLifeDays", model.getTimeDecayHalfLifeDays(),
            "popularityPenalty", model.getPopularityPenalty(),
            "topK", topK == null || topK < 1 ? DEFAULT_TOP_K : topK
        )));
        version.setTrainingStatus("RUNNING");
        version.setCreatedBy(currentOperatorId());
        version.setCreatedAt(LocalDateTime.now());
        version = versionRepository.save(version);

        try {
            BuildResult build = buildRecommendations(model, version, null);
            recommendationRepository.deleteByModelVersionId(version.getId());
            recommendationRepository.saveAll(build.recommendations());
            version.setTrainingStatus("COMPLETED");
            version.setQualityMetricsJson(writeJson(build.metrics()));
            version.setTrainedTo(LocalDateTime.now());
            versionRepository.save(version);
            markPendingEventsProcessed(version.getId(), null);
            return new TrainingResult(model.getModelCode(), version.getVersionNo(), build.recommendations().size(), build.metrics());
        } catch (RuntimeException ex) {
            version.setTrainingStatus("FAILED");
            version.setQualityMetricsJson(writeJson(Map.of("error", ex.getMessage())));
            versionRepository.save(version);
            throw ex;
        }
    }

    @Transactional
    public IncrementalResult applyIncrementalUpdates(String modelCode) {
        RecommenderModelEntity model = modelRepository.findByModelCode(modelCode)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelCode));
        RecommenderModelVersionEntity version = servingVersion(model.getId())
            .orElseThrow(() -> new IllegalStateException("No completed model version available"));

        List<RecommenderEventEntity> pending = eventRepository.findByProcessedFalseOrderByOccurredAtAsc();
        if (pending.isEmpty()) {
            return new IncrementalResult(modelCode, version.getVersionNo(), 0, 0);
        }

        Set<Long> affectedStudents = pending.stream()
            .map(RecommenderEventEntity::getStudentId)
            .filter(v -> v != null)
            .collect(Collectors.toSet());

        int applied = 0;
        for (RecommenderEventEntity event : pending) {
            if (incrementalUpdateRepository.existsByModelVersionIdAndEventId(version.getId(), event.getId())) {
                continue;
            }
            RecommenderIncrementalUpdateEntity u = new RecommenderIncrementalUpdateEntity();
            u.setModelVersionId(version.getId());
            u.setEventId(event.getId());
            u.setAppliedAt(LocalDateTime.now());
            u.setStatus("APPLIED");
            u.setDetails(writeJson(Map.of("itemType", event.getItemType(), "itemId", event.getItemId())));
            incrementalUpdateRepository.save(u);
            applied++;
        }

        BuildResult build = buildRecommendations(model, version, affectedStudents);
        replaceRecommendationsForStudents(version.getId(), affectedStudents, build.recommendations());
        version.setTrainedTo(LocalDateTime.now());
        version.setQualityMetricsJson(writeJson(build.metrics()));
        versionRepository.save(version);
        markPendingEventsProcessed(version.getId(), pending.stream().map(RecommenderEventEntity::getId).toList());

        return new IncrementalResult(modelCode, version.getVersionNo(), applied, affectedStudents.size());
    }

    @Transactional
    public RollbackResult rollbackModelVersion(String modelCode, Integer targetVersionNo) {
        RecommenderModelEntity model = modelRepository.findByModelCode(modelCode)
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelCode));
        RecommenderModelVersionEntity target = versionRepository.findByModelIdAndVersionNo(model.getId(), targetVersionNo)
            .orElseThrow(() -> new IllegalArgumentException("Target version not found"));
        if (!Set.of("COMPLETED", "ROLLED_BACK").contains(target.getTrainingStatus())) {
            throw new IllegalStateException("Target version must be completed or previously rolled-back");
        }

        RecommenderModelVersionEntity rollbackVersion = new RecommenderModelVersionEntity();
        rollbackVersion.setModelId(model.getId());
        rollbackVersion.setVersionNo(nextVersionNo(model.getId()));
        rollbackVersion.setTrainedFrom(target.getTrainedFrom());
        rollbackVersion.setTrainedTo(LocalDateTime.now());
        rollbackVersion.setConfigJson(target.getConfigJson());
        rollbackVersion.setQualityMetricsJson(target.getQualityMetricsJson());
        rollbackVersion.setTrainingStatus("ROLLED_BACK");
        rollbackVersion.setRollbackOfVersion(target.getId());
        rollbackVersion.setCreatedBy(currentOperatorId());
        rollbackVersion.setCreatedAt(LocalDateTime.now());
        rollbackVersion = versionRepository.save(rollbackVersion);

        List<RecommenderRecommendationEntity> oldRecs = recommendationRepository.findByModelVersionIdOrderByStudentIdAscRankNoAsc(target.getId());
        List<RecommenderRecommendationEntity> cloned = new ArrayList<>();
        for (RecommenderRecommendationEntity old : oldRecs) {
            RecommenderRecommendationEntity r = new RecommenderRecommendationEntity();
            r.setModelVersionId(rollbackVersion.getId());
            r.setStudentId(old.getStudentId());
            r.setItemType(old.getItemType());
            r.setItemId(old.getItemId());
            r.setScore(old.getScore());
            r.setRankNo(old.getRankNo());
            r.setGeneratedAt(LocalDateTime.now());
            cloned.add(r);
        }
        recommendationRepository.deleteByModelVersionId(rollbackVersion.getId());
        recommendationRepository.saveAll(cloned);
        return new RollbackResult(modelCode, targetVersionNo, rollbackVersion.getVersionNo(), cloned.size());
    }

    public List<RecommendationView> recommendationsForCurrentUser(int limit) {
        Optional<RecommenderModelEntity> modelOpt = modelRepository.findFirstByActiveTrueOrderByIdAsc();
        if (modelOpt.isEmpty()) {
            return List.of();
        }
        Optional<RecommenderModelVersionEntity> versionOpt = servingVersion(modelOpt.get().getId());
        if (versionOpt.isEmpty()) {
            return List.of();
        }
        Long studentId = resolveCurrentStudentId().orElse(null);
        if (studentId == null) {
            return List.of();
        }
        int top = limit < 1 ? DEFAULT_TOP_K : limit;
        return recommendationRepository.findByModelVersionIdAndStudentIdOrderByRankNoAsc(versionOpt.get().getId(), studentId)
            .stream()
            .limit(top)
            .map(r -> new RecommendationView(r.getItemType(), r.getItemId(), r.getRankNo(), r.getScore(), lookupItemLabel(r.getItemType(), r.getItemId())))
            .toList();
    }

    public AdminView adminView() {
        List<RecommenderModelEntity> models = modelRepository.findAll();
        List<ModelCard> cards = models.stream().map(m -> {
            List<RecommenderModelVersionEntity> versions = versionRepository.findByModelIdOrderByVersionNoDesc(m.getId());
            RecommenderModelVersionEntity serving = versions.stream()
                .filter(v -> Set.of("COMPLETED", "ROLLED_BACK").contains(v.getTrainingStatus()))
                .findFirst()
                .orElse(null);
            int recCount = serving == null ? 0 : recommendationRepository.findByModelVersionIdOrderByStudentIdAscRankNoAsc(serving.getId()).size();
            return new ModelCard(m, versions, serving, recCount);
        }).toList();
        return new AdminView(cards);
    }

    private BuildResult buildRecommendations(
        RecommenderModelEntity model,
        RecommenderModelVersionEntity version,
        Set<Long> restrictStudents
    ) {
        Map<String, Object> cfg = parseJson(version.getConfigJson());
        int topK = ((Number) cfg.getOrDefault("topK", DEFAULT_TOP_K)).intValue();
        int halfLife = ((Number) cfg.getOrDefault("timeDecayHalfLifeDays", model.getTimeDecayHalfLifeDays())).intValue();
        BigDecimal popularityPenalty = toBigDecimal(cfg.getOrDefault("popularityPenalty", model.getPopularityPenalty()));

        List<RecommenderEventEntity> events = eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(version.getTrainedFrom(), LocalDateTime.now());
        Map<Long, Map<ItemKey, Double>> studentVectors = new HashMap<>();
        Map<ItemKey, Set<Long>> itemUsers = new HashMap<>();
        Map<Long, Double> studentMeans = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (RecommenderEventEntity event : events) {
            if (event.getStudentId() == null) {
                continue;
            }
            ItemKey key = new ItemKey(event.getItemType(), event.getItemId());
            double value = event.getEventValue().doubleValue();
            double days = Math.max(0.0, Duration.between(event.getOccurredAt(), now).toHours() / 24.0);
            double decay = Math.pow(0.5, days / Math.max(1, halfLife));
            double weighted = value * decay;
            studentVectors.computeIfAbsent(event.getStudentId(), k -> new HashMap<>()).merge(key, weighted, Double::sum);
            itemUsers.computeIfAbsent(key, k -> new HashSet<>()).add(event.getStudentId());
        }

        for (Map.Entry<Long, Map<ItemKey, Double>> entry : studentVectors.entrySet()) {
            double avg = entry.getValue().values().stream().mapToDouble(v -> v).average().orElse(0.0);
            studentMeans.put(entry.getKey(), avg);
        }

        Set<Long> targets = restrictStudents == null || restrictStudents.isEmpty() ? studentVectors.keySet() : restrictStudents;
        List<RecommenderRecommendationEntity> recs = new ArrayList<>();
        for (Long studentId : targets) {
            Map<ItemKey, Double> scores;
            if ("ITEM_CF".equalsIgnoreCase(model.getAlgorithmFamily())) {
                scores = itemCfScores(studentId, studentVectors, studentMeans, itemUsers, model.getSimilarityMetric());
            } else {
                scores = userCfScores(studentId, studentVectors, studentMeans, itemUsers, model.getSimilarityMetric());
            }
            Map<ItemKey, Double> own = studentVectors.getOrDefault(studentId, Map.of());
            own.keySet().forEach(scores::remove);

            List<Map.Entry<ItemKey, Double>> ranked = scores.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), applyPopularityPenalty(e.getValue(), itemUsers.getOrDefault(e.getKey(), Set.of()).size(), popularityPenalty)))
                .sorted(Map.Entry.<ItemKey, Double>comparingByValue().reversed())
                .limit(topK)
                .toList();

            int rank = 1;
            for (Map.Entry<ItemKey, Double> e : ranked) {
                RecommenderRecommendationEntity r = new RecommenderRecommendationEntity();
                r.setModelVersionId(version.getId());
                r.setStudentId(studentId);
                r.setItemType(e.getKey().itemType());
                r.setItemId(e.getKey().itemId());
                r.setScore(BigDecimal.valueOf(e.getValue()).setScale(6, RoundingMode.HALF_UP));
                r.setRankNo(rank++);
                r.setGeneratedAt(LocalDateTime.now());
                recs.add(r);
            }
        }

        double avgScore = recs.stream().map(RecommenderRecommendationEntity::getScore).mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("studentsScored", targets.size());
        metrics.put("recommendations", recs.size());
        metrics.put("avgScore", BigDecimal.valueOf(avgScore).setScale(6, RoundingMode.HALF_UP));
        metrics.put("algorithmFamily", model.getAlgorithmFamily());
        metrics.put("similarityMetric", model.getSimilarityMetric());
        return new BuildResult(recs, metrics);
    }

    private Map<ItemKey, Double> userCfScores(
        Long target,
        Map<Long, Map<ItemKey, Double>> studentVectors,
        Map<Long, Double> studentMeans,
        Map<ItemKey, Set<Long>> itemUsers,
        String metric
    ) {
        Map<ItemKey, Double> score = new HashMap<>();
        Map<ItemKey, Double> denom = new HashMap<>();
        Map<ItemKey, Double> targetVector = studentVectors.getOrDefault(target, Map.of());
        for (Map.Entry<Long, Map<ItemKey, Double>> neighbor : studentVectors.entrySet()) {
            if (neighbor.getKey().equals(target)) {
                continue;
            }
            double sim = similarity(targetVector, neighbor.getValue(), metric, studentMeans.getOrDefault(target, 0.0), studentMeans.getOrDefault(neighbor.getKey(), 0.0));
            if (sim == 0.0) {
                continue;
            }
            for (Map.Entry<ItemKey, Double> item : neighbor.getValue().entrySet()) {
                score.merge(item.getKey(), sim * item.getValue(), Double::sum);
                denom.merge(item.getKey(), Math.abs(sim), Double::sum);
                itemUsers.computeIfAbsent(item.getKey(), k -> new HashSet<>()).add(neighbor.getKey());
            }
        }
        return finalizeScores(score, denom);
    }

    private Map<ItemKey, Double> itemCfScores(
        Long targetStudentId,
        Map<Long, Map<ItemKey, Double>> studentVectors,
        Map<Long, Double> studentMeans,
        Map<ItemKey, Set<Long>> itemUsers,
        String metric
    ) {
        Map<ItemKey, Double> result = new HashMap<>();
        Map<ItemKey, Double> targetVector = studentVectors.getOrDefault(targetStudentId, Map.of());
        Set<ItemKey> allItems = itemUsers.keySet();
        for (ItemKey candidate : allItems) {
            double numer = 0.0;
            double denom = 0.0;
            for (Map.Entry<ItemKey, Double> interacted : targetVector.entrySet()) {
                Map<Long, Double> a = itemVector(candidate, studentVectors);
                Map<Long, Double> b = itemVector(interacted.getKey(), studentVectors);
                double sim = similarity(a, b, metric, studentMeans, candidate, interacted.getKey());
                if (sim == 0.0) {
                    continue;
                }
                numer += sim * interacted.getValue();
                denom += Math.abs(sim);
            }
            if (denom > 0.0) {
                result.put(candidate, numer / denom);
            }
        }
        return result;
    }

    private Map<Long, Double> itemVector(ItemKey item, Map<Long, Map<ItemKey, Double>> studentVectors) {
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, Map<ItemKey, Double>> e : studentVectors.entrySet()) {
            if (e.getValue().containsKey(item)) {
                out.put(e.getKey(), e.getValue().get(item));
            }
        }
        return out;
    }

    private Map<ItemKey, Double> finalizeScores(Map<ItemKey, Double> score, Map<ItemKey, Double> denom) {
        Map<ItemKey, Double> out = new HashMap<>();
        for (Map.Entry<ItemKey, Double> e : score.entrySet()) {
            double d = denom.getOrDefault(e.getKey(), 0.0);
            if (d > 0.0) {
                out.put(e.getKey(), e.getValue() / d);
            }
        }
        return out;
    }

    private double applyPopularityPenalty(double rawScore, int popularity, BigDecimal penalty) {
        double p = penalty == null ? 0.0 : penalty.doubleValue();
        return rawScore / (1.0 + (p * Math.max(1, popularity)));
    }

    private double similarity(Map<?, Double> a, Map<?, Double> b, String metric, double meanA, double meanB) {
        Set<Object> keys = new HashSet<>(a.keySet());
        keys.retainAll(b.keySet());
        if (keys.isEmpty()) {
            return 0.0;
        }
        String m = metric == null ? "COSINE" : metric.toUpperCase(Locale.ROOT);
        return switch (m) {
            case "JACCARD" -> jaccard(new HashSet<>(a.keySet()), new HashSet<>(b.keySet()));
            case "ADJUSTED_COSINE" -> adjustedCosine(a, b, keys, meanA, meanB);
            default -> cosine(a, b, keys);
        };
    }

    private double similarity(
        Map<Long, Double> a,
        Map<Long, Double> b,
        String metric,
        Map<Long, Double> userMeans,
        ItemKey itemA,
        ItemKey itemB
    ) {
        Set<Long> common = new HashSet<>(a.keySet());
        common.retainAll(b.keySet());
        if (common.isEmpty()) {
            return 0.0;
        }
        String m = metric == null ? "COSINE" : metric.toUpperCase(Locale.ROOT);
        if ("JACCARD".equals(m)) {
            return jaccard(new HashSet<>(a.keySet()), new HashSet<>(b.keySet()));
        }
        if ("ADJUSTED_COSINE".equals(m)) {
            double dot = 0.0;
            double na = 0.0;
            double nb = 0.0;
            for (Long user : common) {
                double mean = userMeans.getOrDefault(user, 0.0);
                double va = a.get(user) - mean;
                double vb = b.get(user) - mean;
                dot += va * vb;
                na += va * va;
                nb += vb * vb;
            }
            if (na == 0 || nb == 0) {
                return 0.0;
            }
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (Long user : common) {
            double va = a.get(user);
            double vb = b.get(user);
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double cosine(Map<?, Double> a, Map<?, Double> b, Set<Object> common) {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (Object key : common) {
            double va = a.get(key);
            double vb = b.get(key);
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double adjustedCosine(Map<?, Double> a, Map<?, Double> b, Set<Object> common, double meanA, double meanB) {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (Object key : common) {
            double va = a.get(key) - meanA;
            double vb = b.get(key) - meanB;
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double jaccard(Set<?> a, Set<?> b) {
        Set<Object> inter = new HashSet<>(a.stream().map(Object.class::cast).toList());
        inter.retainAll(b);
        Set<Object> union = new HashSet<>(a.stream().map(Object.class::cast).toList());
        union.addAll(b);
        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) inter.size() / union.size();
    }

    private int nextVersionNo(Long modelId) {
        return versionRepository.findByModelIdOrderByVersionNoDesc(modelId).stream().findFirst().map(v -> v.getVersionNo() + 1).orElse(1);
    }

    private Optional<RecommenderModelVersionEntity> servingVersion(Long modelId) {
        List<RecommenderModelVersionEntity> versions = versionRepository.findByModelIdOrderByVersionNoDesc(modelId);
        return versions.stream().filter(v -> Set.of("COMPLETED", "ROLLED_BACK").contains(v.getTrainingStatus())).findFirst();
    }

    private void replaceRecommendationsForStudents(Long modelVersionId, Collection<Long> students, List<RecommenderRecommendationEntity> allFresh) {
        if (students == null || students.isEmpty()) {
            recommendationRepository.deleteByModelVersionId(modelVersionId);
            recommendationRepository.saveAll(allFresh);
            return;
        }
        List<RecommenderRecommendationEntity> existing = recommendationRepository.findByModelVersionIdOrderByStudentIdAscRankNoAsc(modelVersionId);
        List<RecommenderRecommendationEntity> keep = existing.stream().filter(r -> !students.contains(r.getStudentId())).toList();
        recommendationRepository.deleteByModelVersionId(modelVersionId);
        List<RecommenderRecommendationEntity> merged = new ArrayList<>(keep);
        merged.addAll(allFresh);
        recommendationRepository.saveAll(merged);
    }

    private void markPendingEventsProcessed(Long modelVersionId, List<Long> specificIds) {
        List<RecommenderEventEntity> pending = eventRepository.findByProcessedFalseOrderByOccurredAtAsc();
        List<Long> ids = pending.stream().map(RecommenderEventEntity::getId).toList();
        if (specificIds != null) {
            ids = ids.stream().filter(specificIds::contains).toList();
        }
        if (!ids.isEmpty()) {
            eventRepository.markProcessed(ids, LocalDateTime.now());
            for (Long id : ids) {
                if (!incrementalUpdateRepository.existsByModelVersionIdAndEventId(modelVersionId, id)) {
                    RecommenderIncrementalUpdateEntity u = new RecommenderIncrementalUpdateEntity();
                    u.setModelVersionId(modelVersionId);
                    u.setEventId(id);
                    u.setAppliedAt(LocalDateTime.now());
                    u.setStatus("APPLIED");
                    u.setDetails("{}");
                    incrementalUpdateRepository.save(u);
                }
            }
        }
    }

    private Optional<Long> resolveCurrentStudentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        List<Long> rows = jdbcTemplate.query(
            "SELECT id FROM students WHERE UPPER(student_no)=UPPER(?) AND deleted_at IS NULL LIMIT 1",
            (rs, rowNum) -> rs.getLong("id"),
            username
        );
        if (!rows.isEmpty()) {
            return Optional.of(rows.get(0));
        }
        return Optional.empty();
    }

    private Long currentOperatorId() {
        return userIdentityService.resolveCurrentUserId().orElseGet(() -> userRepository.findIdByUsername("sysadmin").orElse(1L));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String lookupItemLabel(String itemType, Long itemId) {
        String type = itemType == null ? "" : itemType.toUpperCase(Locale.ROOT);
        if ("SKU".equals(type)) {
            List<String> labels = jdbcTemplate.query(
                "SELECT sku_code FROM sku_catalog WHERE id = ?",
                (rs, rowNum) -> rs.getString("sku_code"),
                itemId
            );
            return labels.isEmpty() ? "SKU #" + itemId : labels.get(0);
        }
        if ("CLASS".equals(type)) {
            List<String> labels = jdbcTemplate.query(
                "SELECT class_code FROM classes WHERE id = ?",
                (rs, rowNum) -> rs.getString("class_code"),
                itemId
            );
            return labels.isEmpty() ? "CLASS #" + itemId : labels.get(0);
        }
        return type + " #" + itemId;
    }

    private record ItemKey(String itemType, Long itemId) {
    }

    private record BuildResult(List<RecommenderRecommendationEntity> recommendations, Map<String, Object> metrics) {
    }

    public record RecordEventRequest(
        String eventType,
        Long studentId,
        String itemType,
        Long itemId,
        BigDecimal eventValue,
        LocalDateTime occurredAt,
        String source
    ) {
    }

    public record RecommendationView(String itemType, Long itemId, Integer rank, BigDecimal score, String itemLabel) {
    }

    public record TrainingResult(String modelCode, Integer versionNo, int recommendationsGenerated, Map<String, Object> metrics) {
    }

    public record IncrementalResult(String modelCode, Integer servingVersionNo, int eventsApplied, int affectedStudents) {
    }

    public record RollbackResult(String modelCode, Integer rolledBackToVersion, Integer newRollbackVersion, int restoredRecommendations) {
    }

    public record ModelCard(
        RecommenderModelEntity model,
        List<RecommenderModelVersionEntity> versions,
        RecommenderModelVersionEntity servingVersion,
        int servingRecommendationCount
    ) {
    }

    public record AdminView(List<ModelCard> models) {
    }
}
