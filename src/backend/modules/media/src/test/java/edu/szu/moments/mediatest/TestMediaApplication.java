package edu.szu.moments.mediatest;

import edu.szu.moments.media.MediaModuleConfiguration;
import edu.szu.moments.posts.PostsModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({PostsModuleConfiguration.class, MediaModuleConfiguration.class, TestMediaCollaboratorsConfig.class})
public class TestMediaApplication {}
