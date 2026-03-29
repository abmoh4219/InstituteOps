package com.instituteops.store.domain;

import com.instituteops.recommender.domain.RecommenderService;
import com.instituteops.security.UserIdentityService;
import com.instituteops.security.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private final SpuCatalogRepository spuCatalogRepository;
    private final SkuCatalogRepository skuCatalogRepository;
    private final SkuPricingTierRepository skuPricingTierRepository;
    private final GroupBuyCampaignRepository groupBuyCampaignRepository;
    private final GroupBuyGroupRepository groupBuyGroupRepository;
    private final GroupBuyGroupMemberRepository groupBuyGroupMemberRepository;
    private final GroupBuyOrderRepository groupBuyOrderRepository;
    private final InventoryLockRepository inventoryLockRepository;
    private final UserIdentityService userIdentityService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RecommenderService recommenderService;

    public CatalogService(
        SpuCatalogRepository spuCatalogRepository,
        SkuCatalogRepository skuCatalogRepository,
        SkuPricingTierRepository skuPricingTierRepository,
        GroupBuyCampaignRepository groupBuyCampaignRepository,
        GroupBuyGroupRepository groupBuyGroupRepository,
        GroupBuyGroupMemberRepository groupBuyGroupMemberRepository,
        GroupBuyOrderRepository groupBuyOrderRepository,
        InventoryLockRepository inventoryLockRepository,
        UserIdentityService userIdentityService,
        UserRepository userRepository,
        JdbcTemplate jdbcTemplate,
        RecommenderService recommenderService
    ) {
        this.spuCatalogRepository = spuCatalogRepository;
        this.skuCatalogRepository = skuCatalogRepository;
        this.skuPricingTierRepository = skuPricingTierRepository;
        this.groupBuyCampaignRepository = groupBuyCampaignRepository;
        this.groupBuyGroupRepository = groupBuyGroupRepository;
        this.groupBuyGroupMemberRepository = groupBuyGroupMemberRepository;
        this.groupBuyOrderRepository = groupBuyOrderRepository;
        this.inventoryLockRepository = inventoryLockRepository;
        this.userIdentityService = userIdentityService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.recommenderService = recommenderService;
    }

    @Transactional
    public SpuCatalogEntity createSpu(String spuCode, String name, String description) {
        if (!StringUtils.hasText(spuCode) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("spuCode and name are required");
        }
        SpuCatalogEntity spu = new SpuCatalogEntity();
        spu.setSpuCode(spuCode.trim().toUpperCase(Locale.ROOT));
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
        sku.setSkuCode(skuCode.trim().toUpperCase(Locale.ROOT));
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
        tier.setCurrencyCode(StringUtils.hasText(currencyCode) ? currencyCode.toUpperCase(Locale.ROOT) : "USD");
        tier.setValidFrom(LocalDateTime.now());
        tier.setValidTo(null);
        return skuPricingTierRepository.save(tier);
    }

    @Transactional
    public GroupBuyCampaignEntity createCampaign(
        String campaignCode,
        Long skuId,
        String title,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalTime cutoffTime,
        Integer requiredParticipants,
        Integer formationWindowHours
    ) {
        if (!StringUtils.hasText(campaignCode) || skuId == null || !StringUtils.hasText(title) || startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("campaignCode, skuId, title, startsAt and endsAt are required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        GroupBuyCampaignEntity campaign = new GroupBuyCampaignEntity();
        campaign.setCampaignCode(campaignCode.trim().toUpperCase(Locale.ROOT));
        campaign.setSkuId(skuId);
        campaign.setTitle(title.trim());
        campaign.setStartsAt(startsAt);
        campaign.setEndsAt(endsAt);
        campaign.setCutoffTime(cutoffTime == null ? LocalTime.of(21, 0) : cutoffTime);
        campaign.setRequiredParticipants(requiredParticipants == null ? 10 : Math.max(2, requiredParticipants));
        campaign.setFormationWindowHours(formationWindowHours == null ? 72 : Math.max(1, formationWindowHours));
        campaign.setStatus("ACTIVE");
        campaign.setCreatedBy(currentOperatorId());
        campaign.setCreatedAt(LocalDateTime.now());
        return groupBuyCampaignRepository.save(campaign);
    }

    public PriceQuote quote(Long skuId, Integer quantity) {
        SkuCatalogEntity sku = skuCatalogRepository.findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("SKU not found"));
        int qty = quantity == null ? 1 : Math.max(1, quantity);
        List<SkuPricingTierEntity> tiers = activeTiersForSku(skuId);
        SkuPricingTierEntity selected = selectTier(tiers, qty);
        BigDecimal total = selected.getUnitPrice().multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        return new PriceQuote(sku.getSkuCode(), qty, selected.getUnitPrice(), total, selected.getCurrencyCode(), sku.getPurchaseLimitPerStudent());
    }

    public ManagerView managerView() {
        refreshCampaignStates();
        List<GroupBuyCampaignEntity> campaigns = groupBuyCampaignRepository.findAll().stream()
            .sorted(Comparator.comparing(GroupBuyCampaignEntity::getStartsAt).reversed())
            .toList();
        List<GroupBuyGroupEntity> groups = groupBuyGroupRepository.findAll().stream()
            .sorted(Comparator.comparing(GroupBuyGroupEntity::getCreatedAt).reversed())
            .toList();
        List<GroupBuyOrderEntity> orders = groupBuyOrderRepository.findAll().stream()
            .sorted(Comparator.comparing(GroupBuyOrderEntity::getPlacedAt).reversed())
            .toList();
        return new ManagerView(
            spuCatalogRepository.findAll(),
            skuCatalogRepository.findAll(),
            skuPricingTierRepository.findAll(),
            campaigns,
            groups,
            orders,
            campaignCards(campaigns)
        );
    }

    public StudentView studentView() {
        refreshCampaignStates();
        Long studentId = currentStudentId();
        List<GroupBuyCampaignEntity> campaigns = groupBuyCampaignRepository.findByStatusInOrderByStartsAtAsc(List.of("ACTIVE", "DRAFT"));
        List<StudentCampaignCard> cards = new ArrayList<>();
        for (GroupBuyCampaignEntity campaign : campaigns) {
            SkuCatalogEntity sku = skuCatalogRepository.findById(campaign.getSkuId()).orElse(null);
            if (sku == null || !sku.isActive()) {
                continue;
            }
            List<GroupBuyGroupEntity> groups = groupBuyGroupRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId());
            PriceQuote quote = quote(campaign.getSkuId(), 1);
            cards.add(new StudentCampaignCard(
                campaign,
                sku,
                quote,
                groups,
                countdown(campaign),
                studentCommittedQty(studentId, campaign.getId())
            ));
        }
        return new StudentView(cards, groupBuyOrderRepository.findByStudentIdOrderByPlacedAtDesc(studentId));
    }

    @Transactional
    public GroupBuyOrderEntity placeOrder(PlaceOrderRequest request) {
        refreshCampaignStates();
        GroupBuyCampaignEntity campaign = groupBuyCampaignRepository.findById(request.campaignId())
            .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        if (!"ACTIVE".equals(campaign.getStatus())) {
            throw new IllegalStateException("Campaign is not active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartsAt()) || now.isAfter(campaign.getEndsAt())) {
            throw new IllegalStateException("Campaign is outside order window");
        }
        if (now.toLocalTime().isAfter(campaign.getCutoffTime())) {
            throw new IllegalStateException("Daily campaign cutoff reached");
        }

        int qty = request.quantity() == null ? 1 : Math.max(1, request.quantity());
        SkuCatalogEntity sku = skuCatalogRepository.findById(campaign.getSkuId()).orElseThrow(() -> new IllegalArgumentException("SKU not found"));
        Integer committed = studentCommittedQty(currentStudentId(), campaign.getId());
        int limit = sku.getPurchaseLimitPerStudent() == null ? 5 : sku.getPurchaseLimitPerStudent();
        if (committed + qty > limit) {
            throw new IllegalStateException("Purchase limit exceeded for this campaign");
        }

        GroupBuyGroupEntity group = resolveGroupForOrder(campaign, request.groupCode());
        ensureMembership(group, currentStudentId());

        PriceQuote quote = quote(campaign.getSkuId(), qty);
        GroupBuyOrderEntity order = new GroupBuyOrderEntity();
        order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        order.setCampaignId(campaign.getId());
        order.setGroupId(group.getId());
        order.setStudentId(currentStudentId());
        order.setOrderStatus("PENDING_GROUP");
        order.setQuantity(qty);
        order.setUnitPrice(quote.unitPrice());
        order.setTotalAmount(quote.total());
        order.setPaymentCaptured(false);
        order.setPlacedAt(LocalDateTime.now());
        GroupBuyOrderEntity saved = groupBuyOrderRepository.save(order);

        recommenderService.recordEvent(new RecommenderService.RecordEventRequest(
            "ORDER",
            currentStudentId(),
            "SKU",
            campaign.getSkuId(),
            BigDecimal.valueOf(qty),
            LocalDateTime.now(),
            "STORE_ORDER"
        ));

        updateGroupProgress(group.getId());
        refreshCampaignStates();
        return saved;
    }

    @Transactional
    public void confirmGroupOrders(Long groupId) {
        GroupBuyGroupEntity group = groupBuyGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!"FORMED".equals(group.getStatus())) {
            throw new IllegalStateException("Group must be FORMED before confirmation");
        }
        for (GroupBuyOrderEntity order : groupBuyOrderRepository.findByGroupIdOrderByPlacedAtAsc(groupId)) {
            if (!"INVENTORY_LOCKED".equals(order.getOrderStatus())) {
                continue;
            }
            order.setOrderStatus("CONFIRMED");
            groupBuyOrderRepository.save(order);
            for (InventoryLockEntity lock : inventoryLockRepository.findByGroupBuyOrderId(order.getId())) {
                lock.setLockStatus("CONSUMED");
                inventoryLockRepository.save(lock);
            }
        }
    }

    @Transactional
    public void refreshCampaignStates() {
        LocalDateTime now = LocalDateTime.now();
        for (GroupBuyCampaignEntity campaign : groupBuyCampaignRepository.findAll()) {
            if ("CLOSED".equals(campaign.getStatus())) {
                continue;
            }
            if (now.isAfter(campaign.getEndsAt())) {
                campaign.setStatus("CLOSED");
                groupBuyCampaignRepository.save(campaign);
            } else if (now.isBefore(campaign.getStartsAt()) && !"DRAFT".equals(campaign.getStatus())) {
                campaign.setStatus("DRAFT");
                groupBuyCampaignRepository.save(campaign);
            } else if (!now.isBefore(campaign.getStartsAt()) && !"ACTIVE".equals(campaign.getStatus())) {
                campaign.setStatus("ACTIVE");
                groupBuyCampaignRepository.save(campaign);
            }
        }

        for (GroupBuyGroupEntity group : groupBuyGroupRepository.findByStatusIn(List.of("FORMING", "FORMED"))) {
            GroupBuyCampaignEntity campaign = groupBuyCampaignRepository.findById(group.getCampaignId()).orElse(null);
            if (campaign == null) {
                continue;
            }
            if ("FORMING".equals(group.getStatus())) {
                long members = groupBuyGroupMemberRepository.countByGroupIdAndStatus(group.getId(), "JOINED");
                if (members >= group.getParticipantsTarget()) {
                    group.setParticipantsCurrent((int) members);
                    group.setStatus("FORMED");
                    group.setFormedAt(LocalDateTime.now());
                    groupBuyGroupRepository.save(group);
                    lockGroupOrders(group.getId(), campaign.getSkuId());
                    continue;
                }
                if (LocalDateTime.now().isAfter(group.getExpiresAt()) || LocalDateTime.now().isAfter(campaign.getEndsAt())) {
                    group.setParticipantsCurrent((int) members);
                    group.setStatus("FAILED");
                    groupBuyGroupRepository.save(group);
                    voidGroupOrders(group.getId(), "Group formation failed before expiry");
                }
            }
            if ("FORMED".equals(group.getStatus()) && LocalDateTime.now().isAfter(campaign.getEndsAt())) {
                group.setStatus("CLOSED");
                groupBuyGroupRepository.save(group);
            }
        }
    }

    private void lockGroupOrders(Long groupId, Long skuId) {
        for (GroupBuyOrderEntity order : groupBuyOrderRepository.findByGroupIdOrderByPlacedAtAsc(groupId)) {
            if (!"PENDING_GROUP".equals(order.getOrderStatus())) {
                continue;
            }
            if (!inventoryLockRepository.existsByGroupBuyOrderId(order.getId())) {
                InventoryLockEntity lock = new InventoryLockEntity();
                lock.setGroupBuyOrderId(order.getId());
                lock.setSkuId(skuId);
                lock.setLockedQty(BigDecimal.valueOf(order.getQuantity()));
                lock.setLockStatus("LOCKED");
                lock.setLockReason("GROUP_FORMED_INVENTORY_LOCK");
                lock.setLockedAt(LocalDateTime.now());
                inventoryLockRepository.save(lock);
            }
            order.setOrderStatus("INVENTORY_LOCKED");
            groupBuyOrderRepository.save(order);
        }
    }

    private void voidGroupOrders(Long groupId, String reason) {
        for (GroupBuyOrderEntity order : groupBuyOrderRepository.findByGroupIdOrderByPlacedAtAsc(groupId)) {
            if (!List.of("PENDING_GROUP", "INVENTORY_LOCKED").contains(order.getOrderStatus())) {
                continue;
            }
            order.setOrderStatus("VOID");
            order.setVoidReason(reason);
            groupBuyOrderRepository.save(order);

            for (InventoryLockEntity lock : inventoryLockRepository.findByGroupBuyOrderId(order.getId())) {
                if (!"CONSUMED".equals(lock.getLockStatus())) {
                    lock.setLockStatus("RELEASED");
                    lock.setReleasedAt(LocalDateTime.now());
                    inventoryLockRepository.save(lock);
                }
            }
        }
    }

    private GroupBuyGroupEntity resolveGroupForOrder(GroupBuyCampaignEntity campaign, String groupCode) {
        if (StringUtils.hasText(groupCode)) {
            GroupBuyGroupEntity existing = groupBuyGroupRepository.findByCampaignIdAndGroupCode(campaign.getId(), groupCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Group code not found in this campaign"));
            if (!"FORMING".equals(existing.getStatus())) {
                throw new IllegalStateException("Only FORMING groups can accept new joins");
            }
            return existing;
        }

        GroupBuyGroupEntity group = new GroupBuyGroupEntity();
        group.setCampaignId(campaign.getId());
        group.setGroupCode("GRP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        group.setInitiatorStudentId(currentStudentId());
        group.setStatus("FORMING");
        group.setParticipantsTarget(campaign.getRequiredParticipants());
        group.setParticipantsCurrent(1);
        group.setCreatedAt(LocalDateTime.now());

        LocalDateTime capByCampaign = campaign.getEndsAt();
        LocalDateTime byWindow = LocalDateTime.now().plusHours(campaign.getFormationWindowHours());
        group.setExpiresAt(byWindow.isBefore(capByCampaign) ? byWindow : capByCampaign);
        return groupBuyGroupRepository.save(group);
    }

    private void ensureMembership(GroupBuyGroupEntity group, Long studentId) {
        if (groupBuyGroupMemberRepository.existsByGroupIdAndStudentId(group.getId(), studentId)) {
            return;
        }
        GroupBuyGroupMemberEntity member = new GroupBuyGroupMemberEntity();
        member.setGroupId(group.getId());
        member.setStudentId(studentId);
        member.setJoinedAt(LocalDateTime.now());
        member.setStatus("JOINED");
        groupBuyGroupMemberRepository.save(member);
    }

    private void updateGroupProgress(Long groupId) {
        GroupBuyGroupEntity group = groupBuyGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        long members = groupBuyGroupMemberRepository.countByGroupIdAndStatus(groupId, "JOINED");
        group.setParticipantsCurrent((int) members);
        groupBuyGroupRepository.save(group);
    }

    private SkuPricingTierEntity selectTier(List<SkuPricingTierEntity> tiers, int qty) {
        return tiers.stream()
            .filter(t -> t.getMinQty() <= qty)
            .reduce((a, b) -> b)
            .orElseThrow(() -> new IllegalArgumentException("No pricing tiers configured for SKU"));
    }

    private List<SkuPricingTierEntity> activeTiersForSku(Long skuId) {
        LocalDateTime now = LocalDateTime.now();
        return skuPricingTierRepository.findBySkuIdOrderByMinQtyAsc(skuId)
            .stream()
            .filter(t -> !t.getValidFrom().isAfter(now) && (t.getValidTo() == null || !t.getValidTo().isBefore(now)))
            .toList();
    }

    private Countdown countdown(GroupBuyCampaignEntity campaign) {
        LocalDateTime now = LocalDateTime.now();
        long toStart = now.isBefore(campaign.getStartsAt()) ? Duration.between(now, campaign.getStartsAt()).toSeconds() : 0;
        long toEnd = now.isBefore(campaign.getEndsAt()) ? Duration.between(now, campaign.getEndsAt()).toSeconds() : 0;
        LocalDateTime todayCutoff = now.with(campaign.getCutoffTime());
        if (todayCutoff.isBefore(now)) {
            todayCutoff = todayCutoff.plusDays(1);
        }
        long toCutoff = Duration.between(now, todayCutoff).toSeconds();
        return new Countdown(Math.max(0, toStart), Math.max(0, toEnd), Math.max(0, toCutoff));
    }

    private Integer studentCommittedQty(Long studentId, Long campaignId) {
        Integer committed = groupBuyOrderRepository.sumCommittedQtyForStudent(studentId, campaignId);
        return committed == null ? 0 : committed;
    }

    private Long currentOperatorId() {
        return userIdentityService.resolveCurrentUserId().orElseGet(() -> userRepository.findIdByUsername("store").orElse(1L));
    }

    private Long currentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        if (StringUtils.hasText(username)) {
            List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM students WHERE UPPER(student_no) = UPPER(?) AND deleted_at IS NULL LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                username
            );
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }

        List<Long> fallback = jdbcTemplate.query(
            "SELECT id FROM students WHERE deleted_at IS NULL ORDER BY id ASC LIMIT 1",
            (rs, rowNum) -> rs.getLong("id")
        );
        if (fallback.isEmpty()) {
            throw new IllegalStateException("No student profile available for ordering");
        }
        return fallback.get(0);
    }

    private List<CampaignCard> campaignCards(List<GroupBuyCampaignEntity> campaigns) {
        return campaigns.stream().map(c -> {
            SkuCatalogEntity sku = skuCatalogRepository.findById(c.getSkuId()).orElse(null);
            int groups = groupBuyGroupRepository.findByCampaignIdOrderByCreatedAtDesc(c.getId()).size();
            return new CampaignCard(c, sku, groups, countdown(c));
        }).toList();
    }

    public record QuoteRequest(Long skuId, Integer quantity) {
    }

    public record PlaceOrderRequest(Long campaignId, String groupCode, Integer quantity) {
    }

    public record PriceQuote(String skuCode, int quantity, BigDecimal unitPrice, BigDecimal total, String currency, Integer purchaseLimitPerStudent) {
    }

    public record Countdown(long secondsToStart, long secondsToEnd, long secondsToCutoff) {
    }

    public record CampaignCard(GroupBuyCampaignEntity campaign, SkuCatalogEntity sku, int groupsCount, Countdown countdown) {
    }

    public record StudentCampaignCard(
        GroupBuyCampaignEntity campaign,
        SkuCatalogEntity sku,
        PriceQuote sampleQuote,
        List<GroupBuyGroupEntity> groups,
        Countdown countdown,
        int committedQty
    ) {
    }

    public record ManagerView(
        List<SpuCatalogEntity> spus,
        List<SkuCatalogEntity> skus,
        List<SkuPricingTierEntity> tiers,
        List<GroupBuyCampaignEntity> campaigns,
        List<GroupBuyGroupEntity> groups,
        List<GroupBuyOrderEntity> orders,
        List<CampaignCard> campaignCards
    ) {
    }

    public record StudentView(List<StudentCampaignCard> campaigns, List<GroupBuyOrderEntity> myOrders) {
    }
}
