package com.hotel_booking.controller;

import com.hotel_booking.dto.response.AuthResponse;
import com.hotel_booking.dto.request.LoginRequest;
import com.hotel_booking.dto.request.RegisterRequest;
import com.hotel_booking.dto.response.RegisterResponse;
import com.hotel_booking.entity.User;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.security.JwtService;
import com.hotel_booking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest loginRequest){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(), loginRequest.getPassword()));
        String normalizedEmail = loginRequest.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.builder()
                .id(user.getId())
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest){
        return authService.register(registerRequest);
    }
}
