package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.RegisterRequest;
import com.hotel_booking.dto.response.RegisterResponse;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder);
    }

    @Test
    void registerNormalizesInputSavesCustomerAndReturnsSavedUser() {
        RegisterRequest request = RegisterRequest.builder()
                .name("  Jane Doe  ")
                .email("  JANE.DOE@EXAMPLE.COM  ")
                .phoneNumber("  555-0101  ")
                .password("secret123")
                .build();

        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        RegisterResponse response = service.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(savedUser.getName()).isEqualTo("Jane Doe");
        assertThat(savedUser.getPhoneNumber()).isEqualTo("555-0101");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-secret");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedUser.isActive()).isTrue();

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getPhoneNumber()).isEqualTo("555-0101");
    }

    @Test
    void registerThrowsWhenNormalizedEmailAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("  JANE.DOE@EXAMPLE.COM  ")
                .phoneNumber("555-0101")
                .password("secret123")
                .build();

        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verifyNoInteractions(passwordEncoder);
    }
}
