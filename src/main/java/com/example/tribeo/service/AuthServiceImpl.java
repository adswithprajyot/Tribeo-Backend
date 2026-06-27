package com.example.tribeo.service;

import com.example.tribeo.model.AppRole;
import com.example.tribeo.model.Profile;
import com.example.tribeo.model.Role;
import com.example.tribeo.model.User;
import com.example.tribeo.payload.AuthenticationResult;
import com.example.tribeo.payload.ProfileDTO;
import com.example.tribeo.payload.UserDTO;
import com.example.tribeo.payload.UserResponse;
import com.example.tribeo.repositories.ProfileRepository;
import com.example.tribeo.repositories.RoleRepository;
import com.example.tribeo.repositories.UserRepository;
import com.example.tribeo.security.jwt.JwtUtils;
import com.example.tribeo.security.request.LoginRequest;
import com.example.tribeo.security.request.SignupRequest;
import com.example.tribeo.security.response.MessageResponse;
import com.example.tribeo.security.response.UserInfoResponse;
import com.example.tribeo.security.services.UserDetailsImpl;
import com.example.tribeo.utils.SendMailUtils;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

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
        AuthenticationResult res = authenicateUser(signUpRequest.getUsername(),signUpRequest.getPassword());
        if (res != null)
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        else
            return ResponseEntity.ok(new MessageResponse("some wrong"));

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
    public ResponseCookie logoutUser() {
        return jwtUtils.getCleanJwtCookie();
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

        String email = userDetails.getEmail();
        sendMailUtils.sendemail(email);
        return ResponseEntity.ok(new MessageResponse("OTP send successfully successfully!"));
    }


    public AuthenticationResult authenicateUser(String username, String password){
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),
                userDetails.getUsername(), roles, userDetails.getEmail(), jwtCookie.toString());

        return new AuthenticationResult(response,jwtCookie);
    }



}
