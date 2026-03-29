package com.instituteops.recommender.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecommenderEventRepository extends JpaRepository<RecommenderEventEntity, Long> {

    List<RecommenderEventEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(LocalDateTime from, LocalDateTime to);

    List<RecommenderEventEntity> findByProcessedFalseOrderByOccurredAtAsc();

    @Modifying
    @Query("update RecommenderEventEntity e set e.processed = true, e.processedAt = :at where e.id in :ids")
    int markProcessed(@Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);
}

interface RecommenderModelRepository extends JpaRepository<RecommenderModelEntity, Long> {

    Optional<RecommenderModelEntity> findByModelCode(String modelCode);

    Optional<RecommenderModelEntity> findFirstByActiveTrueOrderByIdAsc();
}

interface RecommenderModelVersionRepository extends JpaRepository<RecommenderModelVersionEntity, Long> {

    List<RecommenderModelVersionEntity> findByModelIdOrderByVersionNoDesc(Long modelId);

    Optional<RecommenderModelVersionEntity> findFirstByModelIdAndTrainingStatusOrderByVersionNoDesc(Long modelId, String trainingStatus);

    Optional<RecommenderModelVersionEntity> findByModelIdAndVersionNo(Long modelId, Integer versionNo);
}

interface RecommenderRecommendationRepository extends JpaRepository<RecommenderRecommendationEntity, Long> {

    @Modifying
    @Query("delete from RecommenderRecommendationEntity r where r.modelVersionId = :modelVersionId")
    int deleteByModelVersionId(@Param("modelVersionId") Long modelVersionId);

    List<RecommenderRecommendationEntity> findByModelVersionIdAndStudentIdOrderByRankNoAsc(Long modelVersionId, Long studentId);

    List<RecommenderRecommendationEntity> findByModelVersionIdOrderByStudentIdAscRankNoAsc(Long modelVersionId);
}

interface RecommenderIncrementalUpdateRepository extends JpaRepository<RecommenderIncrementalUpdateEntity, Long> {

    boolean existsByModelVersionIdAndEventId(Long modelVersionId, Long eventId);
}
