package com.instituteops.store.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpuCatalogRepository extends JpaRepository<SpuCatalogEntity, Long> {

    Optional<SpuCatalogEntity> findBySpuCode(String spuCode);
}

interface SkuCatalogRepository extends JpaRepository<SkuCatalogEntity, Long> {

    Optional<SkuCatalogEntity> findBySkuCode(String skuCode);

    List<SkuCatalogEntity> findBySpuId(Long spuId);

    List<SkuCatalogEntity> findByActiveTrueOrderByNameAsc();
}

interface SkuPricingTierRepository extends JpaRepository<SkuPricingTierEntity, Long> {

    List<SkuPricingTierEntity> findBySkuIdOrderByMinQtyAsc(Long skuId);
}

interface GroupBuyCampaignRepository extends JpaRepository<GroupBuyCampaignEntity, Long> {

    Optional<GroupBuyCampaignEntity> findByCampaignCode(String campaignCode);

    List<GroupBuyCampaignEntity> findByStatusInOrderByStartsAtAsc(List<String> statuses);
}

interface GroupBuyGroupRepository extends JpaRepository<GroupBuyGroupEntity, Long> {

    List<GroupBuyGroupEntity> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    Optional<GroupBuyGroupEntity> findByCampaignIdAndGroupCode(Long campaignId, String groupCode);

    List<GroupBuyGroupEntity> findByStatusIn(List<String> statuses);
}

interface GroupBuyGroupMemberRepository extends JpaRepository<GroupBuyGroupMemberEntity, Long> {

    boolean existsByGroupIdAndStudentId(Long groupId, Long studentId);

    long countByGroupIdAndStatus(Long groupId, String status);

    List<GroupBuyGroupMemberEntity> findByGroupIdAndStatus(Long groupId, String status);
}

interface GroupBuyOrderRepository extends JpaRepository<GroupBuyOrderEntity, Long> {

    List<GroupBuyOrderEntity> findByGroupIdOrderByPlacedAtAsc(Long groupId);

    List<GroupBuyOrderEntity> findByStudentIdOrderByPlacedAtDesc(Long studentId);

    @Query("""
        select coalesce(sum(o.quantity), 0)
        from GroupBuyOrderEntity o
        where o.studentId = :studentId
          and o.campaignId = :campaignId
          and o.orderStatus in ('PENDING_GROUP', 'INVENTORY_LOCKED', 'CONFIRMED')
        """)
    Integer sumCommittedQtyForStudent(@Param("studentId") Long studentId, @Param("campaignId") Long campaignId);
}

interface InventoryLockRepository extends JpaRepository<InventoryLockEntity, Long> {

    boolean existsByGroupBuyOrderId(Long groupBuyOrderId);

    List<InventoryLockEntity> findByGroupBuyOrderId(Long groupBuyOrderId);
}
