package com.barofit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

@AnalyzeClasses(packages = "com.barofit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	private static final String ROOT_PACKAGE = "com.barofit.";

	@ArchTest
	static final ArchRule common은_도메인_모듈에_의존하지_않는다 = noClasses()
			.that().resideInAPackage("com.barofit.common..")
			.should().dependOnClassesThat(
					resideInAPackage("com.barofit..").and(resideOutsideOfPackage("com.barofit.common..")));

	// api 패키지는 서비스 분리 후 언어 독립 계약으로 바뀔 수 있도록 JDK 타입만 쓴다
	@ArchTest
	static final ArchRule api_패키지는_JDK와_다른_api_패키지에만_의존한다 = classes()
			.that().resideInAPackage("com.barofit.*.api..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.barofit.*.api..");

	// 도메인 모듈(modules/)은 com.barofit.{도메인} 패키지를 쓴다.
	// 다른 도메인(app 포함)은 그 도메인의 api 패키지만 참조할 수 있다. Gradle 모듈로 나누지 않으므로 여기서 경계를 지킨다
	@ArchTest
	static final ArchRule 다른_도메인은_api_패키지만_참조한다 = classes()
			.that().resideInAPackage("com.barofit..")
			.should(accessOtherDomainsOnlyThroughApi());

	private static ArchCondition<JavaClass> accessOtherDomainsOnlyThroughApi() {
		return new ArchCondition<>("access other domains only through their api package") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				Optional<String> originDomain = domainOf(origin);
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					JavaClass target = dependency.getTargetClass();
					Optional<String> targetDomain = domainOf(target);
					if (targetDomain.isEmpty() || targetDomain.equals(originDomain)) {
						continue;
					}
					String apiPackage = ROOT_PACKAGE + targetDomain.get() + ".api";
					String targetPackage = target.getPackageName();
					if (!targetPackage.equals(apiPackage) && !targetPackage.startsWith(apiPackage + ".")) {
						events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
					}
				}
			}
		};
	}

	/** modules/ 아래 도메인 모듈의 클래스면 도메인 이름(com.barofit 다음 패키지)을 돌려준다 */
	private static Optional<String> domainOf(JavaClass javaClass) {
		String packageName = javaClass.getPackageName();
		if (!packageName.startsWith(ROOT_PACKAGE)) {
			return Optional.empty();
		}
		boolean inDomainModule = javaClass.getSource()
				.map(source -> source.getUri().toString().contains("/modules/"))
				.orElse(false);
		if (!inDomainModule) {
			return Optional.empty();
		}
		String rest = packageName.substring(ROOT_PACKAGE.length());
		int dot = rest.indexOf('.');
		return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
	}
}
