package com.barofit.identity.profile;

import java.math.BigDecimal;
import java.util.Set;

/** 온보딩 입력. 형식 검증은 web 요청 DTO, 값 사이 검증은 OnboardingService가 한다 */
public record OnboardingCommand(
		String nickname,
		Gender gender,
		int birthYear,
		BigDecimal heightCm,
		BigDecimal weightKg,
		BigDecimal skeletalMuscleKg,
		BigDecimal bodyFatPercent,
		Set<ExerciseGoal> goals,
		ExperienceLevel experienceLevel,
		int weeklyWorkoutDays,
		CoachingStyle coachingStyle) {
}
