package com.dsh.platform.domain.coverage;

import java.time.LocalDateTime;
import java.util.List;

public record CoverageView(
        Long demandId,
        LocalDateTime intentionEndAt,
        long remainDays,
        List<ProcessCoverage> processes,
        boolean allSatisfied
) {}
