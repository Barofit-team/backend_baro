package com.barofit.workout;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CoachingMessageCatalogTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sameAssessmentCodeProducesDifferentStyleText() {
        var event = json.readTree("{\"feedbackCode\":\"KNEE_IN\"}");
        var snapshot = json.readTree("{}");
        String friendly = CoachingMessageCatalog.text(CoachingStyle.FRIENDLY, "REP_FEEDBACK", event, snapshot);
        String calm = CoachingMessageCatalog.text(CoachingStyle.CALM, "REP_FEEDBACK", event, snapshot);
        String sparta = CoachingMessageCatalog.text(CoachingStyle.SPARTA, "REP_FEEDBACK", event, snapshot);
        String analytical = CoachingMessageCatalog.text(CoachingStyle.ANALYTICAL, "REP_FEEDBACK", event, snapshot);
        assertThat(friendly).contains("무릎", "발끝");
        assertThat(calm).contains("무릎", "발끝");
        assertThat(sparta).contains("무릎", "발끝");
        assertThat(analytical).contains("무릎", "발끝");
        assertThat(new java.util.HashSet<>(java.util.List.of(friendly, calm, sparta, analytical))).hasSize(4);
        assertThat(CoachingMessageCatalog.text(CoachingStyle.CALM, "SET_FEEDBACK", event, snapshot))
            .startsWith("한 세트를 마쳤습니다.");
        assertThat(CoachingMessageCatalog.text(CoachingStyle.CALM, "REP_FEEDBACK",
            json.readTree("{\"feedbackCode\":\"UNKNOWN\"}"), snapshot)).isNull();
    }

    @Test
    void introUsesTrustedSnapshotAndAddsStyle() {
        var snapshot = json.readTree("""
            {"exercises":[{"exerciseId":"push-up","introTtsText":"손을 어깨 너비로 짚으세요."}]}
            """);
        var event = json.readTree("{\"exerciseId\":\"push-up\"}");
        assertThat(CoachingMessageCatalog.text(CoachingStyle.FRIENDLY, "EXERCISE_START", event, snapshot))
            .contains("함께 준비해요", "손을 어깨 너비로 짚으세요");
        assertThat(CoachingMessageCatalog.text(CoachingStyle.SPARTA, "EXERCISE_START", event, snapshot))
            .contains("자세를 잡으세요", "손을 어깨 너비로 짚으세요");
        assertThat(CoachingMessageCatalog.text(CoachingStyle.CALM, "EXERCISE_START",
            json.readTree("{\"exerciseId\":\"unknown\"}"), snapshot)).isNull();
    }
}
