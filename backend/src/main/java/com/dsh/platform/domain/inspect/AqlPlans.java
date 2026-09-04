package com.dsh.platform.domain.inspect;

/**
 * GB/T 2828.1 一次正常抽样、一般检验水平 II。
 * 主表箭头已在检索时折算；若方案 n 大于本批实交则全检本批且 Ac=0。
 */
public final class AqlPlans {

    public record Plan(int n, int ac, int re) {}

    private static final int DOWN = -1;
    private static final int UP = -2;
    private static final int[] SIZES = {2, 3, 5, 8, 13, 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250};
    /** 列：0.65 / 1.0 / 1.5 / 2.5，值为 Ac，DOWN/UP 为箭头。 */
    private static final int[][] AC = {
            {DOWN, DOWN, DOWN, DOWN},
            {DOWN, DOWN, DOWN, DOWN},
            {DOWN, DOWN, DOWN, 0},
            {DOWN, DOWN, 0, 0},
            {DOWN, 0, 0, 1},
            {0, 0, 1, 1},
            {0, 1, 1, 2},
            {1, 1, 2, 3},
            {1, 2, 3, 5},
            {2, 3, 5, 7},
            {3, 5, 7, 10},
            {5, 7, 10, 14},
            {7, 10, 14, 21},
            {10, 14, 21, UP},
            {14, 21, 21, UP},
    };

    private AqlPlans() {}

    public static Plan plan(int lotSize, String aql) {
        int lot = Math.max(0, lotSize);
        if (lot <= 0) {
            return new Plan(0, 0, 1);
        }
        int col = column(aql);
        int idx = startIndex(lot);
        idx = followArrows(idx, col);
        int n = SIZES[idx];
        int ac = AC[idx][col];
        if (ac < 0) {
            ac = 0;
        }
        if (n > lot) {
            return new Plan(lot, 0, 1);
        }
        return new Plan(n, ac, ac + 1);
    }

    private static int column(String aql) {
        String s = aql == null ? "1.0" : aql.trim();
        if (s.startsWith("0.65") || s.startsWith(".65")) {
            return 0;
        }
        if (s.startsWith("1.5")) {
            return 2;
        }
        if (s.startsWith("2.5")) {
            return 3;
        }
        return 1;
    }

    /** 水平 II 字码对应的主表行。 */
    private static int startIndex(int lot) {
        if (lot <= 8) {
            return 0;
        }
        if (lot <= 15) {
            return 1;
        }
        if (lot <= 25) {
            return 2;
        }
        if (lot <= 50) {
            return 3;
        }
        if (lot <= 90) {
            return 4;
        }
        if (lot <= 150) {
            return 5;
        }
        if (lot <= 280) {
            return 6;
        }
        if (lot <= 500) {
            return 7;
        }
        if (lot <= 1200) {
            return 8;
        }
        if (lot <= 3200) {
            return 9;
        }
        if (lot <= 10000) {
            return 10;
        }
        if (lot <= 35000) {
            return 11;
        }
        if (lot <= 150000) {
            return 12;
        }
        if (lot <= 500000) {
            return 13;
        }
        return 14;
    }

    private static int followArrows(int idx, int col) {
        int guard = 0;
        while (guard++ < SIZES.length) {
            int v = AC[idx][col];
            if (v == DOWN) {
                if (idx >= SIZES.length - 1) {
                    break;
                }
                idx++;
                continue;
            }
            if (v == UP) {
                if (idx <= 0) {
                    break;
                }
                idx--;
                continue;
            }
            break;
        }
        return idx;
    }
}
