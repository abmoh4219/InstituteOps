package com.instituteops.inventory.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Validated
@RequestMapping
public class InventoryController {

    private final InventoryModuleService inventoryModuleService;

    public InventoryController(InventoryModuleService inventoryModuleService) {
        this.inventoryModuleService = inventoryModuleService;
    }

    @GetMapping("/inventory")
    public String page(Model model) {
        inventoryModuleService.refreshExpiryAndLowStockSignals();
        model.addAttribute("units", inventoryModuleService.units());
        model.addAttribute("conversions", inventoryModuleService.conversions());
        model.addAttribute("ingredients", inventoryModuleService.ingredients());
        model.addAttribute("batches", inventoryModuleService.batches());
        model.addAttribute("transactions", inventoryModuleService.transactions());
        model.addAttribute("stockCounts", inventoryModuleService.stockCounts());
        model.addAttribute("varianceRows", inventoryModuleService.stockVarianceViews());
        model.addAttribute("stockByIngredient", inventoryModuleService.currentStockByIngredient());
        return "inventory-dashboard";
    }

    @PostMapping("/inventory/unit")
    public String createUnit(@ModelAttribute UnitForm form) {
        inventoryModuleService.createUnit(form.unitCode(), form.unitName(), form.unitType(), form.baseUnit());
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/conversion")
    public String createConversion(@ModelAttribute ConversionForm form) {
        inventoryModuleService.createConversion(form.fromUnitId(), form.toUnitId(), form.factor());
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/ingredient")
    public String createIngredient(@ModelAttribute IngredientForm form) {
        inventoryModuleService.createIngredient(form.ingredientCode(), form.ingredientName(), form.defaultUnitId());
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/batch")
    public String receive(@ModelAttribute BatchForm form) {
        inventoryModuleService.receiveBatch(new InventoryModuleService.ReceiveBatchRequest(
            form.ingredientId(),
            form.batchNo(),
            form.quantity(),
            form.unitId(),
            form.unitCost(),
            form.expiresAt()
        ));
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/issue")
    public String issue(@ModelAttribute IssueForm form) {
        inventoryModuleService.issueFifo(new InventoryModuleService.IssueRequest(
            form.ingredientId(),
            form.transactionType(),
            form.quantity(),
            form.unitId(),
            form.lossReasonCode(),
            form.note()
        ));
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/stock-count")
    public String stockCount(@ModelAttribute StockCountForm form) {
        inventoryModuleService.recordStockCount(new InventoryModuleService.StockCountRequest(
            form.ingredientId(),
            form.actualQty(),
            form.actualUnitId()
        ));
        return "redirect:/inventory";
    }

    @ResponseBody
    @GetMapping("/api/inventory/stock")
    public Object stockApi() {
        return inventoryModuleService.currentStockByIngredient();
    }

    @ResponseBody
    @PostMapping("/api/inventory/issue")
    public ResponseEntity<?> issueApi(@RequestBody @Validated IssueApiRequest request) {
        return ResponseEntity.ok(inventoryModuleService.issueFifo(new InventoryModuleService.IssueRequest(
            request.ingredientId(),
            request.transactionType(),
            request.quantity(),
            request.unitId(),
            request.lossReasonCode(),
            request.note()
        )));
    }

    public record UnitForm(String unitCode, String unitName, String unitType, boolean baseUnit) {
    }

    public record ConversionForm(Long fromUnitId, Long toUnitId, BigDecimal factor) {
    }

    public record IngredientForm(String ingredientCode, String ingredientName, Long defaultUnitId) {
    }

    public record BatchForm(Long ingredientId, String batchNo, BigDecimal quantity, Long unitId, BigDecimal unitCost, LocalDate expiresAt) {
    }

    public record IssueForm(Long ingredientId, String transactionType, BigDecimal quantity, Long unitId, String lossReasonCode, String note) {
    }

    public record StockCountForm(Long ingredientId, BigDecimal actualQty, Long actualUnitId) {
    }

    public record IssueApiRequest(
        @NotNull Long ingredientId,
        @NotBlank String transactionType,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull Long unitId,
        String lossReasonCode,
        String note
    ) {
    }
}
