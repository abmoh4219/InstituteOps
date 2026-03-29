package com.instituteops.store.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpuCatalogRepository extends JpaRepository<SpuCatalogEntity, Long> {

    Optional<SpuCatalogEntity> findBySpuCode(String spuCode);
}

interface SkuCatalogRepository extends JpaRepository<SkuCatalogEntity, Long> {

    Optional<SkuCatalogEntity> findBySkuCode(String skuCode);

    List<SkuCatalogEntity> findBySpuId(Long spuId);
}

interface SkuPricingTierRepository extends JpaRepository<SkuPricingTierEntity, Long> {

    List<SkuPricingTierEntity> findBySkuIdOrderByMinQtyAsc(Long skuId);
}

interface GroupBuyCampaignRepository extends JpaRepository<GroupBuyCampaignEntity, Long> {
}

interface GroupBuyGroupRepository extends JpaRepository<GroupBuyGroupEntity, Long> {
}

interface GroupBuyGroupMemberRepository extends JpaRepository<GroupBuyGroupMemberEntity, Long> {
}

interface GroupBuyOrderRepository extends JpaRepository<GroupBuyOrderEntity, Long> {
}

interface InventoryLockRepository extends JpaRepository<InventoryLockEntity, Long> {
}
