package com.example.dbids;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;

@SpringBootApplication
@ConfigurationProperties
public class DbidsApplication {
    public static void main(String[] args) { SpringApplication.run(DbidsApplication.class, args); }
}