package com.barofit.identity.social;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocialLoginProperties.class)
class SocialLoginConfig {
}
