package com.spectra.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void register_ValidRequest_ReturnsCreated() throws Exception {
        User user = new User();
        user.setId(10L);
        user.setFullName("Alice Bob");
        user.setEmail("alice@example.com");
        user.setRole(Role.USER);

        when(userService.registerUser(anyString(), anyString(), anyString())).thenReturn(user);

        String jsonPayload = """
                {
                    "fullName": "Alice Bob",
                    "email": "alice@example.com",
                    "password": "securePassword123"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Bob"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_InvalidEmail_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "fullName": "Alice Bob",
                    "email": "not-an-email",
                    "password": "securePassword123"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_ShortPassword_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "fullName": "Alice Bob",
                    "email": "alice@example.com",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_DuplicateEmail_ReturnsConflict() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Email is already registered"));

        String jsonPayload = """
                {
                    "fullName": "Alice Bob",
                    "email": "alice@example.com",
                    "password": "securePassword123"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void getCurrentUser_ReturnsOk() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setFullName("Admin User");
        user.setEmail("admin@example.com");
        user.setRole(Role.ADMIN);

        when(userService.getUserByEmail("admin@example.com")).thenReturn(user);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin@example.com", null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateUserRole_ReturnsOk() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setFullName("Operator");
        user.setEmail("operator@example.com");
        user.setRole(Role.ADMIN);

        when(userService.updateUserRole(2L, Role.ADMIN)).thenReturn(user);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/2/role")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
