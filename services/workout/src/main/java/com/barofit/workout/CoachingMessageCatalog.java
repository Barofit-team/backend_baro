package com.barofit.workout;

import tools.jackson.databind.JsonNode;

/** 판정 코드는 유지하고 사용자에게 읽어 줄 문장만 온보딩 스타일에 맞게 선택한다. */
public final class CoachingMessageCatalog {
    private CoachingMessageCatalog() { }

    /** LLM에는 기기 자유 텍스트 대신 서버에서 정의한 판정 의미만 전달한다. */
    public static String finding(String code) {
        return switch (code) {
            case "HIP_DROP" -> "골반이 아래로 처짐. 몸통 정렬 유지 안내";
            case "ELBOW_FLARE" -> "팔꿈치가 바깥으로 벌어짐. 몸통 가까이 유지 안내";
            case "SHALLOW_RANGE" -> "동작 범위가 얕음. 가능한 범위에서 조금 더 깊게 움직이도록 안내";
            case "KNEE_IN" -> "무릎이 안쪽으로 모임. 무릎을 발끝 방향에 맞추도록 안내";
            case "GOOD_FORM" -> "자세가 안정적임. 현재 자세 유지 격려";
            default -> null;
        };
    }

    public static String text(CoachingStyle style, String event, JsonNode message, JsonNode snapshot) {
        return switch (event) {
            case "WORKOUT_START" -> switch (style) {
                case FRIENDLY -> "좋아요! 오늘 운동을 함께 시작해 볼까요? 준비 자세를 확인해 주세요.";
                case CALM -> "운동을 시작합니다. 준비 자세를 확인해 주세요.";
                case SPARTA -> "운동 시작! 준비 자세를 잡고 집중하세요.";
                case ANALYTICAL -> "운동을 시작합니다. 첫 동작의 자세와 목표를 확인하세요.";
            };
            case "EXERCISE_START" -> exerciseIntro(style, message.path("exerciseId").asText(), snapshot);
            case "REP_FEEDBACK" -> feedback(style, message.path("feedbackCode").asText());
            case "SET_FEEDBACK" -> setFeedback(style, message.path("feedbackCode").asText());
            case "WORKOUT_FINISH" -> switch (style) {
                case FRIENDLY -> "오늘 운동 끝! 정말 수고했어요.";
                case CALM -> "오늘 운동을 마쳤습니다. 수고하셨습니다.";
                case SPARTA -> "오늘 운동 완료! 끝까지 해냈습니다.";
                case ANALYTICAL -> "오늘 운동을 마쳤습니다. 수행 기록을 확인해 보세요.";
            };
            default -> null;
        };
    }

    private static String exerciseIntro(CoachingStyle style, String exerciseId, JsonNode snapshot) {
        if (exerciseId.equals("pushup")) exerciseId = "push-up";
        for (JsonNode exercise : snapshot.path("exercises")) {
            if (!exerciseId.equals(exercise.path("exerciseId").asText())) continue;
            String intro = exercise.path("introTtsText").asText("");
            if (intro.isBlank()) return null;
            String prefix = switch (style) {
                case FRIENDLY -> "좋아요, 다음 동작을 함께 준비해요. ";
                case CALM -> "다음 동작을 준비합니다. ";
                case SPARTA -> "다음 동작입니다. 자세를 잡으세요. ";
                case ANALYTICAL -> "다음 동작의 준비 자세와 수행 방법입니다. ";
            };
            String text = prefix + intro;
            return text.substring(0, Math.min(text.length(), 300));
        }
        return null;
    }

    private static String setFeedback(CoachingStyle style, String code) {
        String completion = switch (style) {
            case FRIENDLY -> "좋아요! 한 세트 끝냈어요. ";
            case CALM -> "한 세트를 마쳤습니다. ";
            case SPARTA -> "한 세트 완료! ";
            case ANALYTICAL -> "한 세트를 완료했습니다. ";
        };
        String correction = feedback(style, code);
        if (correction == null || code.equals("GOOD_FORM")) return completion.trim();
        return completion + correction;
    }

    private static String feedback(CoachingStyle style, String code) {
        return switch (code) {
            case "HIP_DROP" -> switch (style) {
                case FRIENDLY -> "골반이 조금 내려갔어요. 몸통을 곧게 유지해 볼까요?";
                case CALM -> "골반이 아래로 처졌어요. 몸통을 곧게 유지해 주세요.";
                case SPARTA -> "골반을 올리세요. 몸통을 곧게 유지하세요!";
                case ANALYTICAL -> "골반 처짐이 감지됐습니다. 어깨부터 발목까지 정렬을 유지하세요.";
            };
            case "ELBOW_FLARE" -> switch (style) {
                case FRIENDLY -> "팔꿈치를 몸통 가까이 두면 더 좋아요.";
                case CALM -> "팔꿈치를 몸통 가까이 유지해 주세요.";
                case SPARTA -> "팔꿈치를 벌리지 마세요. 몸통 가까이!";
                case ANALYTICAL -> "팔꿈치 벌어짐이 감지됐습니다. 팔꿈치를 몸통 가까이 유지하세요.";
            };
            case "SHALLOW_RANGE" -> switch (style) {
                case FRIENDLY -> "좋아요. 동작 범위를 조금만 더 넓혀 볼까요?";
                case CALM -> "조금 더 깊게 움직여 주세요.";
                case SPARTA -> "동작이 얕습니다. 조금 더 깊게!";
                case ANALYTICAL -> "동작 범위가 짧게 감지됐습니다. 가능한 범위에서 조금 더 깊게 움직이세요.";
            };
            case "KNEE_IN" -> switch (style) {
                case FRIENDLY -> "무릎이 안쪽으로 모이고 있어요. 발끝 방향에 맞춰 볼까요?";
                case CALM -> "무릎이 안쪽으로 모이고 있어요. 발끝과 같은 방향으로 맞춰 주세요.";
                case SPARTA -> "무릎 방향을 바로잡으세요. 발끝과 같은 방향으로!";
                case ANALYTICAL -> "무릎 안쪽 이동이 감지됐습니다. 무릎과 발끝 방향을 맞추세요.";
            };
            case "GOOD_FORM" -> switch (style) {
                case FRIENDLY -> "좋아요! 지금 자세를 유지해요.";
                case CALM -> "좋습니다. 지금 자세를 유지해 주세요.";
                case SPARTA -> "좋습니다! 이 자세를 유지하세요.";
                case ANALYTICAL -> "현재 자세가 안정적입니다. 같은 정렬을 유지하세요.";
            };
            default -> null;
        };
    }
}
