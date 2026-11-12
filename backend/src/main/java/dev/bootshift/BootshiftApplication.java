package dev.bootshift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BootshiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootshiftApplication.class, args);
    }
}
