package com.example.screenplay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NovelScreenplayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelScreenplayApplication.class, args);
    }
}
