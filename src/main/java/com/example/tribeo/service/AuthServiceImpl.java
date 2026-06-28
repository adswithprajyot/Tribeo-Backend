package com.example.tribeo.service;

import com.example.tribeo.model.AppRole;
import com.example.tribeo.model.Profile;
import com.example.tribeo.model.RefreshToken;
import com.example.tribeo.model.Role;
import com.example.tribeo.model.User;
import com.example.tribeo.payload.AuthenticationResult;
import com.example.tribeo.payload.ProfileDTO;
import com.example.tribeo.payload.UserDTO;
import com.example.tribeo.payload.UserResponse;
import com.example.tribeo.repositories.ProfileRepository;
import com.example.tribeo.repositories.RefreshTokenRepository;
import com.example.tribeo.repositories.RoleRepository;
import com.example.tribeo.repositories.UserRepository;
import com.example.tribeo.security.jwt.JwtUtils;
import com.example.tribeo.security.request.LoginRequest;
import com.example.tribeo.security.request.SignupRequest;
import com.example.tribeo.security.request.VerifyOtpRequest;
import com.example.tribeo.security.response.MessageResponse;
import com.example.tribeo.security.response.UserInfoResponse;
import com.example.tribeo.security.services.UserDetailsImpl;
import com.example.tribeo.utils.SendMailUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {
    private static final int OTP_VALIDITY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String OTP_KEY_PREFIX = "otp:user:";
    private static final String OTP_ATTEMPTS_KEY_PREFIX = "otp:attempts:user:";

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    ProfileRepository profileRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    SendMailUtils sendMailUtils;

    @Override
    public AuthenticationResult login(LoginRequest loginRequest) {

        return authenicateUser(loginRequest.getUsername(), loginRequest.getPassword());
    }

    @Override
    public AuthenticationResult refreshToken(HttpServletRequest request) {
        String refreshToken = jwtUtils.getRefreshJwtFromCookies(request);
        if (!StringUtils.hasText(refreshToken) || !jwtUtils.validateRefreshJwtToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        String tokenHash = hashToken(refreshToken);
        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is not recognized."));

        if (storedRefreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedRefreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired.");
        }

        User user = storedRefreshToken.getUser();
        refreshTokenRepository.delete(storedRefreshToken);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        return createAuthenticationResult(userDetails, user);
    }

    @Override
    public ResponseEntity<MessageResponse> register(SignupRequest signUpRequest) {
        if (userRepository.existsByUserName(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getName(),
                signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);

                        break;
                    case "seller":
                        Role modRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(modRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
        }

        user.setRoles(roles);
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));

    }

    @Override
    public UserInfoResponse getCurrentUserDetails(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),
                userDetails.getUsername(), roles);

        return response;
    }

    @Override
    public List<ResponseCookie> logoutUser(HttpServletRequest request) {
        String refreshToken = jwtUtils.getRefreshJwtFromCookies(request);
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenRepository.deleteByTokenHash(hashToken(refreshToken));
        }

        return List.of(jwtUtils.getCleanJwtCookie(), jwtUtils.getCleanRefreshJwtCookie());
    }

    @Override
    public UserResponse getAllAdmin(Pageable pageable) {
        Page<User> allUsers = userRepository.findByRoleName(AppRole.ROLE_ADMIN, pageable);
        List<UserDTO> userDtos = allUsers.getContent()
                .stream()
                .map(p -> modelMapper.map(p, UserDTO.class))
                .collect(Collectors.toList());

        UserResponse response = new UserResponse();
        response.setContent(userDtos);
        response.setPageNumber(allUsers.getNumber());
        response.setPageSize(allUsers.getSize());
        response.setTotalElements(allUsers.getTotalElements());
        response.setTotalPages(allUsers.getTotalPages());
        response.setLastPage(allUsers.isLast());
        return response;
    }

    @Override
    public ResponseEntity<MessageResponse> saveProfile(ProfileDTO profileDTO, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: User is not authenticated."));
        }

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User is not found."));

        Profile profile = user.getProfile() != null ? user.getProfile() : new Profile();
        profile.setGender(profileDTO.getGender());
        profile.setDateOfBirth(profileDTO.getDateOfBirth());
        profile.setUser(user);
        user.setProfile(profile);

        profileRepository.save(profile);
        return ResponseEntity.ok(new MessageResponse("Profile saved successfully!"));
    }

    @Override
    public ResponseEntity<?> sendOtpEmail(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: User is not authenticated."));
        }

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User is not found."));

        String otp = generateOtp();
        String otpKey = otpKey(user.getUserId());
        String attemptsKey = otpAttemptsKey(user.getUserId());
        redisTemplate.opsForValue().set(otpKey, encoder.encode(otp), Duration.ofMinutes(OTP_VALIDITY_MINUTES));
        redisTemplate.delete(attemptsKey);

        boolean emailSent = sendMailUtils.sendemail(userDetails.getEmail(), otp);
        if (!emailSent) {
            redisTemplate.delete(otpKey);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("OTP could not be sent. Please try again later."));
        }

        return ResponseEntity.ok(new MessageResponse("OTP sent successfully."));
    }

    @Override
    public ResponseEntity<?> verifyOtp(VerifyOtpRequest verifyOtpRequest, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: User is not authenticated."));
        }

        String otpKey = otpKey(userDetails.getId());
        String attemptsKey = otpAttemptsKey(userDetails.getId());
        String otpHash = redisTemplate.opsForValue().get(otpKey);

        if (!StringUtils.hasText(otpHash)) {
            redisTemplate.delete(attemptsKey);
            return ResponseEntity.badRequest().body(new MessageResponse("OTP has expired or was not requested. Please request a new OTP."));
        }

        int attempts = getOtpAttempts(attemptsKey);
        if (attempts >= MAX_OTP_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new MessageResponse("Too many invalid OTP attempts. Please request a new OTP."));
        }

        if (!encoder.matches(verifyOtpRequest.getOtp(), otpHash)) {
            incrementOtpAttempts(attemptsKey);
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid OTP."));
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        return ResponseEntity.ok(new MessageResponse("OTP verified successfully."));
    }


    public AuthenticationResult authenicateUser(String username, String password) {
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not found."));

        return createAuthenticationResult(userDetails, user);
    }

    private AuthenticationResult createAuthenticationResult(UserDetailsImpl userDetails, User user) {
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        String refreshToken = jwtUtils.generateRefreshTokenFromUsername(userDetails.getUsername());
        saveRefreshToken(user, refreshToken);
        ResponseCookie refreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),
                userDetails.getUsername(), roles, userDetails.getEmail());

        return new AuthenticationResult(response, jwtCookie, refreshCookie);
    }

    private void saveRefreshToken(User user, String refreshToken) {
        RefreshToken token = new RefreshToken(
                hashToken(refreshToken),
                jwtUtils.getExpirationInstantFromJwtToken(refreshToken),
                user
        );

        refreshTokenRepository.save(token);
    }

    private String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    private String otpKey(Long userId) {
        return OTP_KEY_PREFIX + userId;
    }

    private String otpAttemptsKey(Long userId) {
        return OTP_ATTEMPTS_KEY_PREFIX + userId;
    }

    private int getOtpAttempts(String attemptsKey) {
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        if (!StringUtils.hasText(attempts)) {
            return 0;
        }

        return Integer.parseInt(attempts);
    }

    private void incrementOtpAttempts(String attemptsKey) {
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(OTP_VALIDITY_MINUTES));
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

}
