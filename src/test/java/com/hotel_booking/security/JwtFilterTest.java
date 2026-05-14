package com.hotel_booking.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private FilterChain filterChain;

    private JwtFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtFilter(jwtService, customUserDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterContinuesWithoutAuthenticationWhenAuthorizationHeaderIsMissing() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, customUserDetailsService);
    }

    @Test
    void doFilterContinuesWithoutAuthenticationWhenAuthorizationHeaderIsNotBearer() throws Exception {
        request.addHeader("Authorization", "Basic abc123");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, customUserDetailsService);
    }

    @Test
    void doFilterContinuesWithoutAuthenticationWhenTokenHasNoUsername() throws Exception {
        request.addHeader("Authorization", "Bearer token");

        when(jwtService.getUserName("token")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).getUserName("token");
        verifyNoInteractions(customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterDoesNotLoadUserWhenAuthenticationAlreadyExists() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        Authentication existingAuthentication =
                new UsernamePasswordAuthenticationToken("existing@example.com", null);
        SecurityContextHolder.getContext().setAuthentication(existingAuthentication);

        when(jwtService.getUserName("token")).thenReturn("jane.doe@example.com");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existingAuthentication);
        verify(jwtService).getUserName("token");
        verifyNoInteractions(customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterDoesNotAuthenticateWhenTokenIsInvalidForUser() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        UserDetails userDetails = userDetails();

        when(jwtService.getUserName("token")).thenReturn("jane.doe@example.com");
        when(customUserDetailsService.loadUserByUsername("jane.doe@example.com")).thenReturn(userDetails);
        when(jwtService.validateToken("token", userDetails)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).validateToken("token", userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterAuthenticatesWhenBearerTokenIsValid() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        UserDetails userDetails = userDetails();

        when(jwtService.getUserName("token")).thenReturn("jane.doe@example.com");
        when(customUserDetailsService.loadUserByUsername("jane.doe@example.com")).thenReturn(userDetails);
        when(jwtService.validateToken("token", userDetails)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("CUSTOMER");
        assertThat(authentication.getDetails()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterClearsContextAndContinuesWhenJwtServiceThrowsJwtException() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing@example.com", null));

        when(jwtService.getUserName("token")).thenThrow(new JwtException("bad token"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).getUserName("token");
        verifyNoInteractions(customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterClearsContextAndContinuesWhenJwtServiceThrowsIllegalArgumentException() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing@example.com", null));

        when(jwtService.getUserName("token")).thenThrow(new IllegalArgumentException("empty token"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).getUserName("token");
        verifyNoInteractions(customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterClearsContextAndContinuesWhenUserDetailsServiceThrowsUsernameNotFound() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        SecurityContextHolder.getContext().setAuthentication(null);

        when(jwtService.getUserName("token")).thenReturn("jane.doe@example.com");
        when(customUserDetailsService.loadUserByUsername("jane.doe@example.com"))
                .thenThrow(new UsernameNotFoundException("missing"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).getUserName("token");
        verify(customUserDetailsService).loadUserByUsername("jane.doe@example.com");
        verify(jwtService, never()).validateToken("token", userDetails());
        verify(filterChain).doFilter(request, response);
    }

    private UserDetails userDetails() {
        return User.withUsername("jane.doe@example.com")
                .password("secret")
                .authorities("CUSTOMER")
                .build();
    }
}
