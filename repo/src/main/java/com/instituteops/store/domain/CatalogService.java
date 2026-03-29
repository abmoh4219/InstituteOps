package com.instituteops.store.domain;

import com.instituteops.security.UserIdentityService;
import com.instituteops.security.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private final SpuCatalogRepository spuCatalogRepository;
    private final SkuCatalogRepository skuCatalogRepository;
    private final SkuPricingTierRepository skuPricingTierRepository;
    private final UserIdentityService userIdentityService;
    private final UserRepository userRepository;

    public CatalogService(
        SpuCatalogRepository spuCatalogRepository,
        SkuCatalogRepository skuCatalogRepository,
        SkuPricingTierRepository skuPricingTierRepository,
        UserIdentityService userIdentityService,
        UserRepository userRepository
    ) {
        this.spuCatalogRepository = spuCatalogRepository;
        this.skuCatalogRepository = skuCatalogRepository;
        this.skuPricingTierRepository = skuPricingTierRepository;
        this.userIdentityService = userIdentityService;
        this.userRepository = userRepository;
    }

    public List<SpuCatalogEntity> spus() {
        return spuCatalogRepository.findAll();
    }

    public List<SkuCatalogEntity> skus() {
        return skuCatalogRepository.findAll();
    }

    public List<SkuPricingTierEntity> tiers() {
        return skuPricingTierRepository.findAll();
    }

    @Transactional
    public SpuCatalogEntity createSpu(String spuCode, String name, String description) {
        if (!StringUtils.hasText(spuCode) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("spuCode and name are required");
        }
        SpuCatalogEntity spu = new SpuCatalogEntity();
        spu.setSpuCode(spuCode.trim().toUpperCase());
        spu.setName(name.trim());
        spu.setDescription(description);
        spu.setActive(true);
        return spuCatalogRepository.save(spu);
    }

    @Transactional
    public SkuCatalogEntity createSku(Long spuId, String skuCode, String name, String specsJson, Integer purchaseLimitPerStudent) {
        if (spuId == null || !StringUtils.hasText(skuCode) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("spuId, skuCode and name are required");
        }
        SkuCatalogEntity sku = new SkuCatalogEntity();
        sku.setSpuId(spuId);
        sku.setSkuCode(skuCode.trim().toUpperCase());
        sku.setName(name.trim());
        sku.setSpecs(StringUtils.hasText(specsJson) ? specsJson : "{}");
        sku.setPurchaseLimitPerStudent(purchaseLimitPerStudent == null ? 5 : purchaseLimitPerStudent);
        sku.setActive(true);
        return skuCatalogRepository.save(sku);
    }

    @Transactional
    public SkuPricingTierEntity createTier(Long skuId, Integer minQty, BigDecimal unitPrice, String currencyCode) {
        if (skuId == null || minQty == null || minQty < 1 || unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("skuId/minQty/unitPrice are required");
        }
        SkuPricingTierEntity tier = new SkuPricingTierEntity();
        tier.setSkuId(skuId);
        tier.setMinQty(minQty);
        tier.setUnitPrice(unitPrice);
        tier.setCurrencyCode(StringUtils.hasText(currencyCode) ? currencyCode.toUpperCase() : "USD");
        tier.setValidFrom(LocalDateTime.now());
        tier.setValidTo(null);
        return skuPricingTierRepository.save(tier);
    }

    public PriceQuote quote(Long skuId, Integer quantity) {
        SkuCatalogEntity sku = skuCatalogRepository.findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found"));
        int qty = quantity == null ? 1 : Math.max(1, quantity);
        List<SkuPricingTierEntity> tiers = skuPricingTierRepository.findBySkuIdOrderByMinQtyAsc(skuId);
        SkuPricingTierEntity selected = tiers.stream()
            .filter(t -> t.getMinQty() <= qty)
            .reduce((a, b) -> b)
            .orElseThrow(() -> new IllegalArgumentException("No pricing tiers configured for SKU"));
        BigDecimal total = selected.getUnitPrice().multiply(BigDecimal.valueOf(qty));
        return new PriceQuote(sku.getSkuCode(), qty, selected.getUnitPrice(), total, selected.getCurrencyCode(), sku.getPurchaseLimitPerStudent());
    }

    @Transactional
    public void touchOperatorLog() {
        userIdentityService.resolveCurrentUserId().orElseGet(() -> userRepository.findIdByUsername("store").orElse(1L));
    }

    public record PriceQuote(String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total, String currency, Integer purchaseLimitPerStudent) {
    }
}
