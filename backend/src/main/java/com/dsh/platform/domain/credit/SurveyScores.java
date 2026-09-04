package com.dsh.platform.domain.credit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 互评只取总体星级（1～5）。旧问卷若无 overall/q4，则回退为四题均分。
 */
public final class SurveyScores {

    private SurveyScores() {}

    public static Double overallStars(String scoresJson, ObjectMapper mapper) {
        if (scoresJson == null || scoresJson.isBlank() || mapper == null) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(scoresJson);
            if (n.has("overall")) {
                return clampStar(n.path("overall").asDouble());
            }
            if (n.has("stars")) {
                return clampStar(n.path("stars").asDouble());
            }
            if (n.has("q4")) {
                return clampStar(n.path("q4").asDouble());
            }
            double sum = 0;
            int c = 0;
            for (String k : new String[] {"q1", "q2", "q3"}) {
                if (n.has(k)) {
                    sum += n.path(k).asDouble();
                    c++;
                }
            }
            return c == 0 ? null : clampStar(sum / c);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double clampStar(double v) {
        if (v < 1 || v > 5) {
            return null;
        }
        return v;
    }
}
