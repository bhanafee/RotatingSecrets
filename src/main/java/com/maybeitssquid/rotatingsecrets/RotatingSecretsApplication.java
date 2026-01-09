package com.maybeitssquid.rotatingsecrets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RotatingSecretsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RotatingSecretsApplication.class, args);
    }

}
