package com.barofit.identity.profile;

import java.util.Set;

/** 프로필 부분 수정 입력. null인 항목은 바꾸지 않는다. 불편 부위 "해당 없음"은 빈 목록 */
public record ProfileUpdateCommand(
		String nickname,
		Gender gender,
		Integer age,
		Integer heightCm,
		Integer weightKg,
		ExerciseGoal goal,
		ExperienceLevel experienceLevel,
		Integer weeklyWorkoutDays,
		Set<DiscomfortArea> discomfortAreas,
		CoachingStyle coachingStyle) {

	public boolean isEmpty() {
		return nickname == null && gender == null && age == null && heightCm == null && weightKg == null
				&& goal == null && experienceLevel == null && weeklyWorkoutDays == null && discomfortAreas == null
				&& coachingStyle == null;
	}
}
