package com.spectra.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
class DemoProfileActivationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("When 'demo' profile is active, unauthenticated access to quick-launch is still rejected with HTTP 401")
    void demoQuickLaunch_WhenDemoProfileActive_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/demo/quick-launch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@spectra.local", roles = {"ADMIN"})
    @DisplayName("When 'demo' profile is active and user is authenticated as ADMIN, quick-launch returns HTTP 200")
    void demoQuickLaunch_WhenDemoProfileActive_AdminAuthenticated_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/demo/quick-launch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("When 'demo' profile is active and user is authenticated as USER, status check returns HTTP 200")
    void demoStatus_WhenDemoProfileActive_UserAuthenticated_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/demo/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").exists());
    }
}
