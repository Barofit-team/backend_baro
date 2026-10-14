package com.barofit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 테스트에서는 barofit.scheduling.enabled=false로 끄고 작업을 직접 호출한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "barofit.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
