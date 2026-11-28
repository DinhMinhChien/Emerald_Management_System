package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.emeraldmanagementsystem.dto.request.ChangePasswordRequest;
import org.example.emeraldmanagementsystem.dto.request.TokenRefreshRequest;
import org.example.emeraldmanagementsystem.dto.request.UserLoginRequest;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.service.AuthService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImplement implements AuthService {
    private final UserRepository userRepository ;
    private final Keycloak keycloak ;

    @Value("${keycloak.realm}")
    private String realm ;

    @Value("${keycloak.auth-server-url}")
    private String serverUrl ;

    private final WebClient.Builder webClientBuilder;

    @Value("${keycloak.resource}")
    private String clientId ;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret ;

    @Override
    public Map<String,Object> login(UserLoginRequest request) {
        String username = request.getUsername() ;
        String password = request.getPassword();

        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new BusinessException("Tài khoản có tên đăng nhập " + username +" không tồn tại !"));
        if ("LOCKED".equals(user.getStatus())) {
            throw new BusinessException("Tài khoản người dùng có tên đăng nhập " + username + " hiện đang bị khóa");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);

        // 2. Gọi API chuẩn với Content-Type phù hợp
        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        return response;
    }

    @Override
    public Map<String, Object> refreshToken(TokenRefreshRequest refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret); // Bắt buộc truyền client secret
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken.getRefreshToken());

        // Gọi API Keycloak
        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        return response;
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token không được để trống");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        try {
            webClientBuilder.build()
                    .post()
                    .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (WebClientResponseException.BadRequest e) {
            // Refresh token hết hạn / session đã đóng -> coi như đã logout
            log.warn("Session Keycloak đã hết hạn hoặc đã đóng: {}", e.getResponseBodyAsString());

        } catch (WebClientResponseException | WebClientRequestException e) {
            log.error("Lỗi khi logout trên Keycloak: {}", e.getMessage(), e);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
    }

    @Override
    @Transactional
    public void changePassword(String username, String keycloakUserId , ChangePasswordRequest request) {
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy tài khoản người dùng"));

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException(422, "SAME_PASSWORD");
        }

        Map<String, Object> verificationTokens;
        try {
            UserLoginRequest userLogin = new UserLoginRequest(username, request.getCurrentPassword());
            verificationTokens = this.login(userLogin);
        } catch (WebClientResponseException.BadRequest e) {
            throw new BusinessException(422, "WRONG_CURRENT_PASSWORD");
        } catch (WebClientResponseException | WebClientRequestException e) {
            log.error("Không thể xác thực mật khẩu hiện tại trên Keycloak: {}", e.getMessage(), e);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }

        Object refreshToken = verificationTokens == null ? null : verificationTokens.get("refresh_token");
        if (!(refreshToken instanceof String token) || token.isBlank()) {
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
        logout(token);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(false);

        try {
            keycloak.realm(realm)
                    .users()
                    .get(keycloakUserId)
                    .resetPassword(credential);
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 400) {
                throw new BusinessException(422, "WEAK_PASSWORD");
            }
            log.error("Không thể đổi mật khẩu trên Keycloak: {}", e.getMessage(), e);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }

        user.setMustChangePassword(false);
        userRepository.save(user);
    }

}
