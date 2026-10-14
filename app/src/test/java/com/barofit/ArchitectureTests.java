package com.barofit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
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
}
