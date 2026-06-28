package com.example.tribeo.payload;

import com.example.tribeo.security.response.UserInfoResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseCookie;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthenticationResult {
    private final UserInfoResponse response;
    private final ResponseCookie jwtCookie;
    private final ResponseCookie refreshCookie;

    public List<ResponseCookie> getCookies() {
        return List.of(jwtCookie, refreshCookie);
    }
}
