package com.telusko.studymode.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class PromptTemplateConfig {

    @Bean
    @Qualifier("tutorSystemPrompt")
    public PromptTemplate tutorSystemPrompt() throws IOException {  // ✅ Use YOUR PromptTemplate class
        ClassPathResource resource = new ClassPathResource("prompts/tutor-system-prompt.st");
        String promptContent = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        return new PromptTemplate(promptContent);  // ✅ Return YOUR PromptTemplate
    }
}