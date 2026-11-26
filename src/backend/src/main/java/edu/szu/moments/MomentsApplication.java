package edu.szu.moments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("edu.szu.moments")
@EnableJpaRepositories(basePackages = "edu.szu.moments.auth")
public class MomentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MomentsApplication.class, args);
    }
}
