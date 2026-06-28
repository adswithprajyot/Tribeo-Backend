package com.example.tribeo.controller;

import com.example.tribeo.config.AppConstants;
import com.example.tribeo.payload.AuthenticationResult;
import com.example.tribeo.payload.ProfileDTO;
import com.example.tribeo.security.request.LoginRequest;
import com.example.tribeo.security.request.SignupRequest;
import com.example.tribeo.security.request.VerifyOtpRequest;
import com.example.tribeo.security.response.MessageResponse;
import com.example.tribeo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        AuthenticationResult result = authService.login(loginRequest);
        return ResponseEntity.ok()
                .headers(cookieHeaders(result.getCookies()))
                .body(result.getResponse());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        AuthenticationResult result = authService.refreshToken(request);
        return ResponseEntity.ok()
                .headers(cookieHeaders(result.getCookies()))
                .body(result.getResponse());
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        return authService.register(signUpRequest);
    }

    @GetMapping("/username")
    public String currentUserName(Authentication authentication){
        if (authentication != null)
            return authentication.getName();
        else
            return "";
    }


    @GetMapping("/user")
    public ResponseEntity<?> getUserDetails(Authentication authentication){
        return ResponseEntity.ok().body(authService.getCurrentUserDetails(authentication));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signoutUser(HttpServletRequest request){
        List<ResponseCookie> cookies = authService.logoutUser(request);
        return ResponseEntity.ok()
                .headers(cookieHeaders(cookies))
                .body(new MessageResponse("You've been signed out!"));
    }

    @GetMapping("/admins")
    public ResponseEntity<?> getAllAdmins(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber) {

        Sort sortByAndOrder = Sort.by(AppConstants.SORT_USERS_BY).descending();
        Pageable pageDetails = PageRequest.of(pageNumber ,
                Integer.parseInt(AppConstants.PAGE_SIZE), sortByAndOrder);

        return ResponseEntity.ok(authService.getAllAdmin(pageDetails));
    }

    @PostMapping("/saveprofile")
    public ResponseEntity<?> saveProfile(@Valid @RequestBody ProfileDTO profileDTO, Authentication authentication) {
        return authService.saveProfile(profileDTO, authentication);
    }

    @PostMapping("/sendotpemail")
    public ResponseEntity<?> saveProfile(Authentication authentication) {
        return authService.sendOtpEmail(authentication);
    }

    @PostMapping({"/verifyotp", "/verify-otp"})
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest verifyOtpRequest,
                                       Authentication authentication) {
        return authService.verifyOtp(verifyOtpRequest, authentication);
    }

    private HttpHeaders cookieHeaders(List<ResponseCookie> cookies) {
        HttpHeaders headers = new HttpHeaders();
        cookies.forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
        return headers;
    }

}
