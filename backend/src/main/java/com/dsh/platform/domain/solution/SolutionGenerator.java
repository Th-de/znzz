package com.dsh.platform.domain.solution;

import com.dsh.platform.entity.Demand;

import java.util.List;

public interface SolutionGenerator {
    List<SolutionCombo> generate(Demand demand);
}
