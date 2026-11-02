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

	/** 나이로 입력받아 출생연도로 저장한다. 해가 바뀌면 나이가 따라 오른다 */
	@Column(nullable = false)
	private int birthYear;

	@Column(nullable = false)
	private int heightCm;

	@Column(nullable = false)
	private int weightKg;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExerciseGoal goal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExperienceLevel experienceLevel;

	@Column(nullable = false)
	private int weeklyWorkoutDays;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(schema = "identity", name = "user_profile_discomfort_areas",
			joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "area", nullable = false)
	private Set<DiscomfortArea> discomfortAreas = EnumSet.noneOf(DiscomfortArea.class);

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CoachingStyle coachingStyle;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static UserProfile create(UUID userId, OnboardingCommand command, int currentYear, Instant now) {
		UserProfile profile = new UserProfile();
		profile.userId = userId;
		profile.gender = command.gender();
		profile.birthYear = currentYear - command.age();
		profile.heightCm = command.heightCm();
		profile.weightKg = command.weightKg();
		profile.goal = command.goal();
		profile.experienceLevel = command.experienceLevel();
		profile.weeklyWorkoutDays = command.weeklyWorkoutDays();
		profile.discomfortAreas = command.discomfortAreas().isEmpty()
				? EnumSet.noneOf(DiscomfortArea.class)
				: EnumSet.copyOf(command.discomfortAreas());
		profile.coachingStyle = command.coachingStyle();
		profile.createdAt = now;
		profile.updatedAt = now;
		return profile;
	}

	/** 부분 수정. null인 항목은 그대로 둔다 */
	void update(ProfileUpdateCommand command, int currentYear, Instant now) {
		if (command.gender() != null) {
			gender = command.gender();
		}
		if (command.age() != null) {
			birthYear = currentYear - command.age();
		}
		if (command.heightCm() != null) {
			heightCm = command.heightCm();
		}
		if (command.weightKg() != null) {
			weightKg = command.weightKg();
		}
		if (command.goal() != null) {
			goal = command.goal();
		}
		if (command.experienceLevel() != null) {
			experienceLevel = command.experienceLevel();
		}
		if (command.weeklyWorkoutDays() != null) {
			weeklyWorkoutDays = command.weeklyWorkoutDays();
		}
		if (command.discomfortAreas() != null) {
			// 컬렉션을 바꿔 끼우지 않고 내용만 바꿔야 Hibernate가 변경을 추적한다
			discomfortAreas.clear();
			discomfortAreas.addAll(command.discomfortAreas());
		}
		if (command.coachingStyle() != null) {
			coachingStyle = command.coachingStyle();
		}
		updatedAt = now;
	}

	public int ageIn(int year) {
		return year - birthYear;
	}
}
