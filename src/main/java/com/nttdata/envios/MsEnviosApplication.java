package com.nttdata.envios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** RutaExpress - ms-envios. */
@SpringBootApplication
// TODO 2: habilita EnviosProperties con @EnableConfigurationProperties(EnviosProperties.class)
@EnableConfigurationProperties(EnviosProperties.class)
public class MsEnviosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsEnviosApplication.class, args);
    }
}
