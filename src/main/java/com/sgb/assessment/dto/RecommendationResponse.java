package com.sgb.assessment.dto;

import java.util.List;

public record RecommendationResponse(
        Long userId,
        int totalRecommendations,
        String strategy,
        List<CircleRecommendation> recommendations
) {
}
