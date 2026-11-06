package com.sgb.assessment.dto;

import java.util.List;

public record CircleRecommendation(
        Long circleId,
        String name,
        String description,
        String topic,
        String format,
        int memberCount,
        String joinPolicy,
        double score,
        List<String> reasons
) {
}
