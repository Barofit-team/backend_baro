package com.barofit.common.migration;

/**
 * 도메인 모듈이 소유한 DB schema. 모듈은 이 타입의 Bean을 등록하고, app이 schema마다 Flyway를 실행한다.
 * 마이그레이션 파일은 classpath:db/migration/{name} 에 둔다.
 */
public record ModuleSchema(String name) {

	public String migrationLocation() {
		return "classpath:db/migration/" + name;
	}
}
