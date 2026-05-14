package com.hotel_booking.security;

import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsernameReturnsAuthUserFromRepositoryUser() {
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .password("encoded-secret")
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        when(userRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername("jane.doe@example.com");

        assertThat(userDetails).isInstanceOf(AuthUser.class);
        assertThat(((AuthUser) userDetails).getUser()).isSameAs(user);
        assertThat(userDetails.getUsername()).isEqualTo("jane.doe@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-secret");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ADMIN");
        verify(userRepository).findByEmail("jane.doe@example.com");
    }

    @Test
    void loadUserByUsernameThrowsWhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("user not found");

        verify(userRepository).findByEmail("missing@example.com");
    }
}
