package com.gserp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GserpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GserpApplication.class, args);
    }
}
