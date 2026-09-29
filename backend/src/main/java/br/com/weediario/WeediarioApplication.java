package br.com.weediario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WeediarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeediarioApplication.class, args);
    }
}
