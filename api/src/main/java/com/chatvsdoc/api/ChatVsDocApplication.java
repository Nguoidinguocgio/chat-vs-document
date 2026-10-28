package com.chatvsdoc.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

// Bỏ người dùng mặc định của Spring Security (in mật khẩu ngẫu nhiên ra log); phiên do T04 làm.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ChatVsDocApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(ChatVsDocApplication.class, args);
        if (ctx.getEnvironment().acceptsProfiles(Profiles.of("cli"))) {
            System.exit(SpringApplication.exit(ctx));
        }
    }
}
