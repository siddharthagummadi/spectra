package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_Success() {
        String fullName = "  John Doe  ";
        String email = " John.Doe@Example.com ";
        String rawPassword = "password123";
        String encodedPassword = "encodedPassword123";

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.registerUser(fullName, email, rawPassword);

        assertNotNull(savedUser);
        assertEquals("John Doe", savedUser.getFullName());
        assertEquals("john.doe@example.com", savedUser.getEmail());
        assertEquals(encodedPassword, savedUser.getPassword());
        assertEquals(Role.USER, savedUser.getRole());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_DuplicateEmail_ThrowsException() {
        String email = "existing@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser("Jane Doe", email, "password123")
        );

        assertEquals("Email is already registered", exception.getMessage());
    }

    @Test
    void getUserByEmail_Success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setRole(Role.ADMIN);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(java.util.Optional.of(user));

        User result = userService.getUserByEmail("admin@example.com");
        assertNotNull(result);
        assertEquals(Role.ADMIN, result.getRole());
    }

    @Test
    void updateUserRole_Success() {
        User user = new User();
        user.setId(2L);
        user.setRole(Role.USER);

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserRole(2L, Role.ADMIN);
        assertEquals(Role.ADMIN, result.getRole());
    }
}
