package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.AuthPageController;
import com.example.bbcinema.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(AuthPageTest.Config.class)
@WebAppConfiguration
class AuthPageTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, AuthPageController.class})
    static class Config implements WebMvcConfigurer {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void rootAlwaysOpensLoginEvenWithAnExistingAuthenticatedSession() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/login.html"));
    }

    @Test
    void loginAndRegisterRoutesArePublicAndForwardToTheStaticForms() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/login.html"));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(forwardedUrl("/register.html"));
    }

    @Test
    void anonymousBrowserCanLoadBothFormsAndTheirAssets() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/register.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/auth.css")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/auth.js")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
