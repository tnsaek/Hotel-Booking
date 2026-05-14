package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.RegisterRequest;
import com.hotel_booking.dto.response.RegisterResponse;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.service.AuthService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .name(request.getName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .phoneNumber(savedUser.getPhoneNumber())
                .build();
    }
}
