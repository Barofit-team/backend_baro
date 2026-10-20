package com.barofit.identity.web;

import com.barofit.identity.profile.CoachingStyle;
import com.barofit.identity.profile.ExerciseGoal;
import com.barofit.identity.profile.ExperienceLevel;
import com.barofit.identity.profile.Gender;
import com.barofit.identity.profile.MyAccount;
import com.barofit.identity.profile.NicknameAvailability;
import com.barofit.identity.profile.OnboardingCommand;
import com.barofit.identity.profile.UserProfile;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

final class UserDtos {

	private UserDtos() {
	}

	/** 신체 수치는 소수점 한 자리까지. 골격근량·체지방률은 선택 */
	record OnboardingRequest(
			@NotNull String nickname,
			@NotNull Gender gender,
			@NotNull @Min(1900) Integer birthYear,
			@NotNull @DecimalMin("100.0") @DecimalMax("250.0") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
			@NotNull @DecimalMin("20.0") @DecimalMax("300.0") @Digits(integer = 3, fraction = 1) BigDecimal weightKg,
			@DecimalMin("5.0") @DecimalMax("150.0") @Digits(integer = 3, fraction = 1) BigDecimal skeletalMuscleKg,
			@DecimalMin("1.0") @DecimalMax("70.0") @Digits(integer = 2, fraction = 1) BigDecimal bodyFatPercent,
			@NotEmpty Set<@NotNull ExerciseGoal> goals,
			@NotNull ExperienceLevel experienceLevel,
			@NotNull @Min(1) @Max(7) Integer weeklyWorkoutDays,
			@NotNull CoachingStyle coachingStyle) {

		OnboardingCommand toCommand() {
			return new OnboardingCommand(nickname, gender, birthYear, heightCm, weightKg, skeletalMuscleKg,
					bodyFatPercent, goals, experienceLevel, weeklyWorkoutDays, coachingStyle);
		}
	}

	record NicknameResponse(String nickname) {
	}

	/** reason: 사용 가능하면 null, 아니면 INVALID_FORMAT 또는 DUPLICATED */
	record NicknameAvailabilityResponse(String nickname, boolean available, NicknameAvailability reason) {

		static NicknameAvailabilityResponse of(String nickname, NicknameAvailability availability) {
			return new NicknameAvailabilityResponse(nickname, availability.available(),
					availability.available() ? null : availability);
		}
	}

	/** profile은 온보딩 전이면 null */
	record MeResponse(
			UUID id,
			String nickname,
			String profileImageUrl,
			UserRole role,
			boolean isOnboarded,
			ProfileResponse profile) {

		static MeResponse from(MyAccount account) {
			User user = account.user();
			return new MeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getRole(),
					user.isOnboarded(), account.profile().map(ProfileResponse::from).orElse(null));
		}
	}

	record ProfileResponse(
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

		static ProfileResponse from(UserProfile profile) {
			return new ProfileResponse(profile.getGender(), profile.getBirthYear(), profile.getHeightCm(),
					profile.getWeightKg(), profile.getSkeletalMuscleKg(), profile.getBodyFatPercent(),
					Set.copyOf(profile.getGoals()), profile.getExperienceLevel(), profile.getWeeklyWorkoutDays(),
					profile.getCoachingStyle());
		}
	}
}
