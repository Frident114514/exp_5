package edu.szu.moments.posts;

import java.time.Clock;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PostsModuleConfiguration.class)
@EntityScan(basePackageClasses = PostEntity.class)
@EnableJpaRepositories(basePackageClasses = PostRepository.class)
public class PostsModuleConfiguration {
    @Bean
    Clock postsClock() {
        return Clock.systemUTC();
    }
}

