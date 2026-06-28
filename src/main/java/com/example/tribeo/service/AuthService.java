package com.example.tribeo.service;

import com.example.tribeo.payload.AuthenticationResult;
import com.example.tribeo.payload.ProfileDTO;
import com.example.tribeo.payload.UserResponse;
import com.example.tribeo.security.request.LoginRequest;
import com.example.tribeo.security.request.SignupRequest;
import com.example.tribeo.security.request.VerifyOtpRequest;
import com.example.tribeo.security.response.MessageResponse;
import com.example.tribeo.security.response.UserInfoResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface AuthService {

    AuthenticationResult login(LoginRequest loginRequest);

    AuthenticationResult refreshToken(HttpServletRequest request);

    ResponseEntity<MessageResponse> register(SignupRequest signUpRequest);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    List<ResponseCookie> logoutUser(HttpServletRequest request);

    UserResponse getAllAdmin(Pageable pageable);

    ResponseEntity<MessageResponse> saveProfile(ProfileDTO profileDTO, Authentication authentication);

    ResponseEntity<?> sendOtpEmail(Authentication authentication);

    ResponseEntity<?> verifyOtp(VerifyOtpRequest verifyOtpRequest, Authentication authentication);
}
