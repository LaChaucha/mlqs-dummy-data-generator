package com.waterworks.mlqsdummydatagenerator;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The `SpGatewayServiceApplication` class serves as the entry point for the Spring Boot
 * application. It is annotated with `@SpringBootApplication`, indicating that it is the main
 * application class for the Spring Boot application.
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@SpringBootApplication
@EnableEncryptableProperties
public class MlqsDummyDataGeneratorApplication {

  /**
   * The main method that starts the Spring Boot application.
   *
   * @param args Command-line arguments provided when starting the application.
   */
  public static void main(String[] args) {
    SpringApplication.run(MlqsDummyDataGeneratorApplication.class, args);
  }
}
