package com.instituteops.store.domain;

import com.instituteops.recommender.domain.RecommenderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping
public class CatalogController {

    private final CatalogService catalogService;
    private final RecommenderService recommenderService;

    public CatalogController(CatalogService catalogService, RecommenderService recommenderService) {
        this.catalogService = catalogService;
        this.recommenderService = recommenderService;
    }

    @GetMapping("/store")
    public String managerPage(Model model) {
        model.addAttribute("vm", catalogService.managerView());
        return "store-manager";
    }

    @GetMapping("/store/student")
    public String studentPage(Model model) {
        model.addAttribute("vm", catalogService.studentView());
        model.addAttribute("recommendations", recommenderService.recommendationsForCurrentUser(5));
        return "store-student";
    }

    @PostMapping("/store/spu")
    public String createSpu(@RequestParam String spuCode, @RequestParam String name, @RequestParam(required = false) String description) {
        catalogService.createSpu(spuCode, name, description);
        return "redirect:/store";
    }

    @PostMapping("/store/sku")
    public String createSku(
        @RequestParam Long spuId,
        @RequestParam String skuCode,
        @RequestParam String name,
        @RequestParam(required = false) String specs,
        @RequestParam(defaultValue = "5") Integer purchaseLimitPerStudent
    ) {
        catalogService.createSku(spuId, skuCode, name, specs, purchaseLimitPerStudent);
        return "redirect:/store";
    }

    @PostMapping("/store/tier")
    public String createTier(
        @RequestParam Long skuId,
        @RequestParam Integer minQty,
        @RequestParam BigDecimal unitPrice,
        @RequestParam(defaultValue = "USD") String currencyCode
    ) {
        catalogService.createTier(skuId, minQty, unitPrice, currencyCode);
        return "redirect:/store";
    }

    @PostMapping("/store/campaign")
    public String createCampaign(
        @RequestParam String campaignCode,
        @RequestParam Long skuId,
        @RequestParam String title,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
        @RequestParam(defaultValue = "21:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime cutoffTime,
        @RequestParam(defaultValue = "10") Integer requiredParticipants,
        @RequestParam(defaultValue = "72") Integer formationWindowHours
    ) {
        catalogService.createCampaign(campaignCode, skuId, title, startsAt, endsAt, cutoffTime, requiredParticipants, formationWindowHours);
        return "redirect:/store";
    }

    @PostMapping("/store/group/{groupId}/confirm")
    public String confirmGroup(@PathVariable Long groupId) {
        catalogService.confirmGroupOrders(groupId);
        return "redirect:/store";
    }

    @PostMapping("/store/refresh")
    public String refreshRules() {
        catalogService.refreshCampaignStates();
        return "redirect:/store";
    }

    @PostMapping("/store/student/order")
    public String placeOrder(
        @RequestParam Long campaignId,
        @RequestParam(required = false) String groupCode,
        @RequestParam(defaultValue = "1") Integer quantity
    ) {
        catalogService.placeOrder(new CatalogService.PlaceOrderRequest(campaignId, groupCode, quantity));
        return "redirect:/store/student";
    }

    @ResponseBody
    @GetMapping("/api/store/quote")
    public CatalogService.PriceQuote quote(@RequestParam Long skuId, @RequestParam Integer quantity) {
        return catalogService.quote(skuId, quantity);
    }

    @ResponseBody
    @PostMapping("/api/store/quote")
    public ResponseEntity<CatalogService.PriceQuote> quotePost(@RequestBody CatalogService.QuoteRequest request) {
        return ResponseEntity.ok(catalogService.quote(request.skuId(), request.quantity()));
    }

    @ResponseBody
    @GetMapping("/api/store/campaigns")
    public CatalogService.StudentView campaigns() {
        return catalogService.studentView();
    }

    @ResponseBody
    @PostMapping("/api/store/order")
    public ResponseEntity<?> placeOrderApi(@RequestBody CatalogService.PlaceOrderRequest request) {
        return ResponseEntity.ok(catalogService.placeOrder(request));
    }
}
