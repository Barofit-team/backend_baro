package com.barofit.identity.web;

import com.barofit.identity.profile.CoachingStyle;
import com.barofit.identity.profile.DiscomfortArea;
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
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class UserDtos {

	private UserDtos() {
	}

	/** 키·몸무게는 소수점 한 자리까지. 불편 부위가 없으면 빈 배열 */
	record OnboardingRequest(
			@NotNull String nickname,
			@NotNull Gender gender,
			@NotNull @Min(14) @Max(100) Integer age,
			@NotNull @DecimalMin("100.0") @DecimalMax("250.0") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
			@NotNull @DecimalMin("20.0") @DecimalMax("300.0") @Digits(integer = 3, fraction = 1) BigDecimal weightKg,
			@NotNull ExerciseGoal goal,
			@NotNull ExperienceLevel experienceLevel,
			@NotNull @Min(2) @Max(6) Integer weeklyWorkoutDays,
			@NotNull Set<@NotNull DiscomfortArea> discomfortAreas,
			@NotNull CoachingStyle coachingStyle) {

		OnboardingCommand toCommand() {
			return new OnboardingCommand(nickname, gender, age, heightCm, weightKg, goal, experienceLevel,
					weeklyWorkoutDays, discomfortAreas, coachingStyle);
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
			ProfileResponse profile = account.profile()
					.map(p -> ProfileResponse.from(p, account.age().orElseThrow()))
					.orElse(null);
			return new MeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getRole(),
					user.isOnboarded(), profile);
		}
	}

	/** discomfortAreas는 enum 선언 순서로 정렬한다 */
	record ProfileResponse(
			Gender gender,
			int age,
			BigDecimal heightCm,
			BigDecimal weightKg,
			ExerciseGoal goal,
			ExperienceLevel experienceLevel,
			int weeklyWorkoutDays,
			List<DiscomfortArea> discomfortAreas,
			CoachingStyle coachingStyle) {

		static ProfileResponse from(UserProfile profile, int age) {
			return new ProfileResponse(profile.getGender(), age, profile.getHeightCm(), profile.getWeightKg(),
					profile.getGoal(), profile.getExperienceLevel(), profile.getWeeklyWorkoutDays(),
					profile.getDiscomfortAreas().stream().sorted().toList(), profile.getCoachingStyle());
		}
	}
}
