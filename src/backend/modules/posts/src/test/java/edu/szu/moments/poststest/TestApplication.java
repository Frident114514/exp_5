package edu.szu.moments.poststest;

import edu.szu.moments.posts.PostsModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({PostsModuleConfiguration.class, TestPortsConfiguration.class})
public class TestApplication {}

