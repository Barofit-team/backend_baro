package com.barofit;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** 두 서비스를 함께 조립하는 회귀 테스트 전용 애플리케이션. 배포하지 않는다. */
@SpringBootApplication
@ComponentScan(basePackages = "com.barofit", excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = org.springframework.boot.context.TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {IdentityApplication.class, RoutineApplication.class})
})
public class BaroApplication {
}
