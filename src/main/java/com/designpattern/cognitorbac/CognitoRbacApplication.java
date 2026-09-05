package com.designpattern.cognitorbac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class CognitoRbacApplication {

    public static void main(String[] args) {
        SpringApplication.run(CognitoRbacApplication.class, args);
    }

}
