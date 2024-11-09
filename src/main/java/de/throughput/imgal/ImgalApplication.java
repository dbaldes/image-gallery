// src/main/java/de/throughput/imgal/ImgalApplication.java
package de.throughput.imgal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class ImgalApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ImgalApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ImgalApplication.class);
    }
}
