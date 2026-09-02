package com.gscrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GscrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GscrmApplication.class, args);
    }
}
