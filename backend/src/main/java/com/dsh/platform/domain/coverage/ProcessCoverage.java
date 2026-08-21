package com.dsh.platform.domain.coverage;

public record ProcessCoverage(
        Integer processNo,
        String processName,
        int need,
        int covered,
        boolean satisfied
) {}
