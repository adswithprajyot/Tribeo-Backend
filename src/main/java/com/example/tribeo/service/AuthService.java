package com.example.tribeo.service;

import com.example.tribeo.payload.AuthenticationResult;
import com.example.tribeo.payload.ProfileDTO;
import com.example.tribeo.payload.UserResponse;
import com.example.tribeo.security.request.LoginRequest;
import com.example.tribeo.security.request.SignupRequest;
import com.example.tribeo.security.response.MessageResponse;
import com.example.tribeo.security.response.UserInfoResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

public interface AuthService {

    AuthenticationResult login(LoginRequest loginRequest);

    ResponseEntity<MessageResponse> register(SignupRequest signUpRequest);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    ResponseCookie logoutUser();

    UserResponse getAllAdmin(Pageable pageable);

    ResponseEntity<MessageResponse> saveProfile(ProfileDTO profileDTO, Authentication authentication);

    ResponseEntity<?> sendOtpEmail(Authentication authentication);
}
