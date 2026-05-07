package org.example.when2go;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class When2goApplication {

    public static void main(String[] args) {
        SpringApplication.run(When2goApplication.class, args);
    }

}
