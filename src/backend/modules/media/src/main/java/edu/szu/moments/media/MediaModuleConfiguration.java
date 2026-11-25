package edu.szu.moments.media;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = MediaModuleConfiguration.class)
@EntityScan(basePackageClasses = MediaEntity.class)
@EnableJpaRepositories(basePackageClasses = MediaRepository.class)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "mediaClock")
    Clock mediaClock() {
        return Clock.systemUTC();
    }
}
