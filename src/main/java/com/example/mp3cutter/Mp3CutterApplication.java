package com.example.mp3cutter;

import com.example.mp3cutter.config.AudioStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AudioStorageProperties.class)
public class Mp3CutterApplication {

    public static void main(String[] args) {
        SpringApplication.run(Mp3CutterApplication.class, args);
    }
}
