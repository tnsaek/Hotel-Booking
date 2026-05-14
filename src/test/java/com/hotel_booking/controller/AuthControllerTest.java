package com.hotel_booking.controller;

import com.hotel_booking.dto.request.LoginRequest;
import com.hotel_booking.dto.request.RegisterRequest;
import com.hotel_booking.dto.response.AuthResponse;
import com.hotel_booking.dto.response.RegisterResponse;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.security.JwtService;
import com.hotel_booking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthService authService;
    @Mock
    private UserRepository userRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationManager, jwtService, authService, userRepository);
    }

    @Test
    void loginAuthenticatesWithOriginalCredentialsFindsNormalizedEmailAndReturnsTokenResponse() {
        LoginRequest request = LoginRequest.builder()
                .email("  JANE.DOE@EXAMPLE.COM  ")
                .password("secret123")
                .build();
        User user = User.builder()
                .id(7L)
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .role(UserRole.CUSTOMER)
                .build();

        when(userRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("jane.doe@example.com")).thenReturn("jwt-token");

        AuthResponse response = controller.login(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        UsernamePasswordAuthenticationToken authentication = tokenCaptor.getValue();
        assertThat(authentication.getPrincipal()).isEqualTo("  JANE.DOE@EXAMPLE.COM  ");
        assertThat(authentication.getCredentials()).isEqualTo("secret123");

        verify(userRepository).findByEmail("jane.doe@example.com");
        verify(jwtService).generateToken("jane.doe@example.com");

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void loginThrowsWhenAuthenticatedEmailDoesNotMatchAnyUser() {
        LoginRequest request = LoginRequest.builder()
                .email("  MISSING@EXAMPLE.COM  ")
                .password("secret123")
                .build();

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("missing@example.com");
        verifyNoInteractions(jwtService, authService);
    }

    @Test
    void loginPropagatesAuthenticationFailureBeforeLoadingUser() {
        LoginRequest request = LoginRequest.builder()
                .email("jane.doe@example.com")
                .password("wrong")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("bad credentials");

        verifyNoInteractions(userRepository, jwtService, authService);
    }

    @Test
    void registerDelegatesToAuthServiceAndReturnsResponse() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .phoneNumber("555-0101")
                .password("secret123")
                .build();
        RegisterResponse expected = RegisterResponse.builder()
                .id(9L)
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .phoneNumber("555-0101")
                .build();

        when(authService.register(request)).thenReturn(expected);

        RegisterResponse response = controller.register(request);

        assertThat(response).isSameAs(expected);
        verify(authService).register(request);
        verifyNoInteractions(authenticationManager, jwtService, userRepository);
    }
}
