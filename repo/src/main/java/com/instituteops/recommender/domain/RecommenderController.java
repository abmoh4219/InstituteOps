package com.instituteops.recommender.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping
public class RecommenderController {

    private final RecommenderService recommenderService;

    public RecommenderController(RecommenderService recommenderService) {
        this.recommenderService = recommenderService;
    }

    @GetMapping("/admin/recommender")
    public String adminPage(Model model) {
        model.addAttribute("vm", recommenderService.adminView());
        return "recommender-admin";
    }

    @PostMapping("/admin/recommender/train")
    public String train(@RequestParam String modelCode, @RequestParam(defaultValue = "5") Integer topK) {
        recommenderService.trainNewVersion(modelCode, topK);
        return "redirect:/admin/recommender";
    }

    @PostMapping("/admin/recommender/incremental")
    public String incremental(@RequestParam String modelCode) {
        recommenderService.applyIncrementalUpdates(modelCode);
        return "redirect:/admin/recommender";
    }

    @PostMapping("/admin/recommender/rollback")
    public String rollback(@RequestParam String modelCode, @RequestParam Integer targetVersionNo) {
        recommenderService.rollbackModelVersion(modelCode, targetVersionNo);
        return "redirect:/admin/recommender";
    }

    @ResponseBody
    @GetMapping("/api/recommender/me")
    public List<RecommenderService.RecommendationView> myRecommendations(@RequestParam(defaultValue = "5") Integer limit) {
        return recommenderService.recommendationsForCurrentUser(limit == null ? 5 : limit);
    }

    @ResponseBody
    @PostMapping("/api/recommender/events")
    public ResponseEntity<?> event(@RequestBody RecordEventRequest body) {
        RecommenderService.RecordEventRequest req = new RecommenderService.RecordEventRequest(
            body.eventType(),
            body.studentId(),
            body.itemType(),
            body.itemId(),
            body.eventValue(),
            body.occurredAt(),
            body.source()
        );
        return ResponseEntity.ok(recommenderService.recordEvent(req));
    }

    @ResponseBody
    @PostMapping("/api/recommender/train/{modelCode}")
    public ResponseEntity<RecommenderService.TrainingResult> trainApi(
        @PathVariable String modelCode,
        @RequestParam(defaultValue = "5") Integer topK
    ) {
        return ResponseEntity.ok(recommenderService.trainNewVersion(modelCode, topK));
    }

    @ResponseBody
    @PostMapping("/api/recommender/incremental/{modelCode}")
    public ResponseEntity<RecommenderService.IncrementalResult> incrementalApi(@PathVariable String modelCode) {
        return ResponseEntity.ok(recommenderService.applyIncrementalUpdates(modelCode));
    }

    @ResponseBody
    @PostMapping("/api/recommender/rollback/{modelCode}")
    public ResponseEntity<RecommenderService.RollbackResult> rollbackApi(
        @PathVariable String modelCode,
        @RequestParam Integer targetVersionNo
    ) {
        return ResponseEntity.ok(recommenderService.rollbackModelVersion(modelCode, targetVersionNo));
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
}
