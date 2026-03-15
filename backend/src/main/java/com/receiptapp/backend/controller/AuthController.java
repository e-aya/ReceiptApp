package com.receiptapp.backend.controller;

import com.receiptapp.backend.dto.AuthRequest;
import com.receiptapp.backend.dto.AuthResponse;
import com.receiptapp.backend.entity.User;
import com.receiptapp.backend.repository.UserRepository;
import com.receiptapp.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // ★ メール登録
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "このメールアドレスは既に登録されています"));
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setName(req.name() != null ? req.name() : req.email());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(),
                user.getEmail(), user.getName(), user.getPlanId()));
    }

    // ★ メールログイン
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        Optional<User> userOpt = userRepository.findByEmail(req.email());
        if (userOpt.isEmpty() ||
                !passwordEncoder.matches(req.password(), userOpt.get().getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "メールアドレスまたはパスワードが間違っています"));
        }
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(),
                user.getEmail(), user.getName(), user.getPlanId()));
    }

    // ★ Googleログイン（フロントからgoogleIdTokenを受け取る）
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        String googleId    = body.get("googleId");
        String email       = body.get("email");
        String name        = body.get("name");

        if (googleId == null || email == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "googleId と email は必須です"));
        }

        // 既存ユーザー検索（googleId or email）
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setGoogleId(googleId);
                    newUser.setName(name != null ? name : email);
                    return newUser;
                });

        // googleId が未設定なら更新
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
        }
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(),
                user.getEmail(), user.getName(), user.getPlanId()));
    }
}