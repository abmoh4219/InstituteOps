package com.instituteops.store.domain;

import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/store")
    public String page(Model model) {
        model.addAttribute("spus", catalogService.spus());
        model.addAttribute("skus", catalogService.skus());
        model.addAttribute("tiers", catalogService.tiers());
        return "store-catalog";
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

    @ResponseBody
    @GetMapping("/api/store/quote")
    public CatalogService.PriceQuote quote(@RequestParam Long skuId, @RequestParam Integer quantity) {
        return catalogService.quote(skuId, quantity);
    }

    @ResponseBody
    @PostMapping("/api/store/quote")
    public ResponseEntity<CatalogService.PriceQuote> quotePost(@RequestBody QuoteRequest request) {
        return ResponseEntity.ok(catalogService.quote(request.skuId(), request.quantity()));
    }

    public record QuoteRequest(Long skuId, Integer quantity) {
    }
}
