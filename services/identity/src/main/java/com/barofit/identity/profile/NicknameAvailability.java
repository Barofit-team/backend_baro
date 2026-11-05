package com.barofit.identity.profile;

public enum NicknameAvailability {
	AVAILABLE,
	INVALID_FORMAT,
	DUPLICATED;

	public boolean available() {
		return this == AVAILABLE;
	}
}
