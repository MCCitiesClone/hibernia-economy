package io.paradaux.treasuryrestapi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@MapperScan("io.paradaux.treasuryrestapi.mapper")
@EnableScheduling
public class TreasuryRestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreasuryRestApiApplication.class, args);
    }

}
