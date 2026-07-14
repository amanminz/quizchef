package io.quizchef.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.quizchef")
@EntityScan(basePackages = "io.quizchef")
@EnableJpaRepositories(basePackages = "io.quizchef")
public class QuizChefApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuizChefApplication.class, args);
    }
}
