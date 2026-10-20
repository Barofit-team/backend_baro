package com.barofit.identity.profile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 온보딩에서 받은 사용자 정보. users와 1:1 */
@Entity
@Table(schema = "identity", name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

	@Id
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Gender gender;

	@Column(nullable = false)
	private int birthYear;

	@Column(nullable = false, precision = 4, scale = 1)
	private BigDecimal heightCm;

	@Column(nullable = false, precision = 4, scale = 1)
	private BigDecimal weightKg;

	@Column(precision = 4, scale = 1)
	private BigDecimal skeletalMuscleKg;

	@Column(precision = 3, scale = 1)
	private BigDecimal bodyFatPercent;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(schema = "identity", name = "user_profile_goals", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "goal", nullable = false)
	private Set<ExerciseGoal> goals = EnumSet.noneOf(ExerciseGoal.class);

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExperienceLevel experienceLevel;

	@Column(nullable = false)
	private int weeklyWorkoutDays;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CoachingStyle coachingStyle;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static UserProfile create(UUID userId, OnboardingCommand command, Instant now) {
		UserProfile profile = new UserProfile();
		profile.userId = userId;
		profile.gender = command.gender();
		profile.birthYear = command.birthYear();
		profile.heightCm = command.heightCm();
		profile.weightKg = command.weightKg();
		profile.skeletalMuscleKg = command.skeletalMuscleKg();
		profile.bodyFatPercent = command.bodyFatPercent();
		profile.goals = EnumSet.copyOf(command.goals());
		profile.experienceLevel = command.experienceLevel();
		profile.weeklyWorkoutDays = command.weeklyWorkoutDays();
		profile.coachingStyle = command.coachingStyle();
		profile.createdAt = now;
		profile.updatedAt = now;
		return profile;
	}
}
