package edu.szu.moments.interaction;

import java.time.Clock;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = InteractionModuleConfiguration.class)
@EntityScan(basePackageClasses = RatingEntity.class)
@EnableJpaRepositories(basePackageClasses = RatingRepository.class)
public class InteractionModuleConfiguration {
    @Bean
    Clock interactionClock() {
        return Clock.systemUTC();
    }
}
