package edu.szu.moments.poststest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@TestConfiguration(proxyBeanMethods = false)
public class TestPortsConfiguration {
    @Bean
    TestPorts testPorts(JdbcTemplate jdbc) {
        return new TestPorts(jdbc);
    }
}

