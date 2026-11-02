package dev.hypermediajava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HypermediaJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HypermediaJavaApplication.class, args);
    }
}
