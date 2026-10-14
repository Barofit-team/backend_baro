package com.barofit.common.migration;

/** 모듈이 소유한 DB schema. 마이그레이션은 classpath:db/migration/{name} */
public record ModuleSchema(String name) {

	public String migrationLocation() {
		return "classpath:db/migration/" + name;
	}
}
