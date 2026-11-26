package com.spectra.controller;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.DemoService;

@RestController
@RequestMapping("/api/demo")
@Profile("demo")
public class DemoController {

    private final DemoService demoService;
    private final UserRepository userRepository;

    public DemoController(DemoService demoService, UserRepository userRepository) {
        this.demoService = demoService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return userRepository.findByEmail(authentication.getName()).orElseGet(demoService::getOrCreateDemoUser);
        }
        return demoService.getOrCreateDemoUser();
    }

    @PostMapping("/quick-launch")
    public ResponseEntity<Map<String, Object>> quickLaunch(@org.springframework.web.bind.annotation.RequestParam(required = false) String scenario) {
        User user = demoService.getOrCreateDemoUser();
        demoService.seedDemoData(user, scenario);
        String basicCreds = java.util.Base64.getEncoder().encodeToString((user.getEmail() + ":Password123!").getBytes());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "credentials", basicCreds,
                "scenario", scenario != null ? scenario : "DATABASE_CONTENTION",
                "message", "Demo cluster successfully auto-seeded and operator authenticated."
        ));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDemo(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String scenario,
            Authentication authentication) {
        User user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "No active user found to seed demo cluster"
            ));
        }

        demoService.seedDemoData(user, scenario);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Live demo cluster and injected failure incident successfully seeded for " + user.getEmail(),
                "user", user.getEmail(),
                "scenario", scenario != null ? scenario : "DATABASE_CONTENTION"
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetDemo(Authentication authentication) {
        User user = resolveUser(authentication);
        if (user != null) {
            demoService.resetDemoData(user);
        }
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demo cluster data successfully reset and cleaned."
        ));
    }

    @org.springframework.web.bind.annotation.GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDemoStatus(Authentication authentication) {
        User user = resolveUser(authentication);
        boolean hasData = user != null && demoService.hasExistingDemoData(user);
        String scenario = user != null ? demoService.getActiveScenario(user) : "DATABASE_CONTENTION";
        return ResponseEntity.ok(Map.of(
                "hasData", hasData,
                "scenario", scenario,
                "user", user != null ? user.getEmail() : "anonymous"
        ));
    }

    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> tickDemo(Authentication authentication) {
        User user = resolveUser(authentication);
        int generated = demoService.tickDemoMetrics(user);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "metricsGenerated", generated,
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
