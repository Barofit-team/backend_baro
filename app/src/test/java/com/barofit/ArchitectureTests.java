package com.barofit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.barofit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	@ArchTest
	static final ArchRule common은_도메인_모듈에_의존하지_않는다 = noClasses()
			.that().resideInAPackage("com.barofit.common..")
			.should().dependOnClassesThat(
					resideInAPackage("com.barofit..").and(resideOutsideOfPackage("com.barofit.common..")));

	// *-api 모듈은 서비스 분리 후 언어 독립 계약으로 바뀔 수 있도록 JDK 타입만 쓴다
	@ArchTest
	static final ArchRule api_모듈은_JDK와_다른_api_모듈에만_의존한다 = classes()
			.that().resideInAPackage("com.barofit.*.api..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.barofit.*.api..");
}
