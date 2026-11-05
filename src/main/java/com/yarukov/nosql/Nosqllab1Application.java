package com.yarukov.nosql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Nosqllab1Application {

    public static void main(String[] args) {
        SpringApplication.run(Nosqllab1Application.class, args);
    }

}
