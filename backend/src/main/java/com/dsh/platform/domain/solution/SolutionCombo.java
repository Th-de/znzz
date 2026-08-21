package com.dsh.platform.domain.solution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SolutionCombo(String type, BigDecimal score, List<Map<String, Object>> items, String rationale) {
    public SolutionCombo(String type, BigDecimal score, List<Map<String, Object>> items) {
        this(type, score, items, null);
    }
}
