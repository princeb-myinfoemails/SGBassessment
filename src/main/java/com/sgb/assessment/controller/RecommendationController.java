package com.sgb.assessment.controller;

import com.sgb.assessment.dto.RecommendationResponse;
import com.sgb.assessment.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(
            RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/recommendations")
    public RecommendationResponse getRecommendations(
            @PathVariable Long userId,

            @RequestParam(required = false)
            @Min(1) @Max(20) Integer limit) {

        return service.recommend(userId, limit);
    }
}
