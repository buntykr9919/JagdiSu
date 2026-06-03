package com.jdsu.quiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiExamQuizApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiExamQuizApplication.class, args);
    }
}
