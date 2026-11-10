package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.*;
import com.example.bbcinema.model.user.User;
import com.example.bbcinema.model.user.Role;
import com.example.bbcinema.enums.UserStatus;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.*;
import com.example.bbcinema.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuthFlowTest.Config.class)
@WebAppConfiguration
class AuthFlowTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, CustomUserDetailsService.class,
        AuthService.class, AuthController.class, UserController.class, RolePageController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    }
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(users, roles);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    User account(String roleName) {
        Role role = new Role(); role.setRoleName(roleName);
        User user = new User(); user.setId(1L); user.setEmail("test@example.com"); user.setFullName("Test");
        user.setRole(role); user.setStatus(UserStatus.ACTIVE); user.setPasswordHash(encoder.encode("Password123"));
        when(users.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        return user;
    }
    @Test void loginSessionRolesAndLogout() throws Exception {
        for (String role : new String[]{"Customer", "Admin", "Manager", "Staff"}) {
            account(role);
            var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"TEST@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.toUpperCase()))
                .andExpect(jsonPath("$.redirectUrl").value(switch (role) {
                    case "Admin" -> "/admin.html";
                    case "Manager" -> "/cinema-management.html";
                    default -> null;
                })).andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk());
            mvc.perform(get("/api/" + role.toLowerCase() + "/dashboard").session(session)).andExpect(status().isOk());
            String other = role.equals("Admin") ? "customer" : "admin";
            mvc.perform(get("/api/" + other + "/dashboard").session(session)).andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void badPasswordDisabledAndCsrf() throws Exception {
        User user = account("Customer");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        user.setStatus(UserStatus.values()[1]);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void normalizedRegistrationAndDuplicatePhone() throws Exception {
        Role role = new Role(); role.setRoleName("Customer");
        when(roles.findByRoleNameIgnoreCase("Customer")).thenReturn(Optional.of(role));
        String body = "{\"fullName\":\" Test User \",\"email\":\" TEST@Example.com \",\"phone\":\" 0901234567 \",\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isCreated());
        verify(users).save(argThat(u -> u.getEmail().equals("test@example.com")
            && u.getFullName().equals("Test User") && u.getPhone().equals("0901234567")
            && u.getStatus() == UserStatus.ACTIVE && u.getCurrentPoints() == 0));
        when(users.existsByPhone("0901234567")).thenReturn(true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isConflict());
    }
    @Test void rejectsMalformedJsonAndOversizedUtf8Password() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        String password = "あ".repeat(25);
        String login = "{\"email\":\"test@example.com\",\"password\":\"" + password + "\"}";
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(login))
            .andExpect(status().isBadRequest());
        String registration = "{\"fullName\":\"Test\",\"email\":\"test@example.com\",\"phone\":\"0901234567\",\"password\":\"" + password + "\",\"confirmPassword\":\"" + password + "\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(registration))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }
    @Test void registrationValidationAndDuplicate() throws Exception {
        Role role = new Role(); role.setRoleName("Customer");
        when(roles.findByRoleNameIgnoreCase("Customer")).thenReturn(Optional.of(role));
        String body = "{\"fullName\":\"Test\",\"email\":\"test@example.com\",\"phone\":\"0901234567\",\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
        verify(users).save(argThat(u -> u.getRole() == role && encoder.matches("Password123", u.getPasswordHash())));
        when(users.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content(body.replace("\"confirmPassword\":\"Password123\"", "\"confirmPassword\":\"different\""))).andExpect(status().isBadRequest());
    }
}
