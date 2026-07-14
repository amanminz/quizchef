package io.quizchef.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.quizchef")
public class QuizChefApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuizChefApplication.class, args);
    }
}
