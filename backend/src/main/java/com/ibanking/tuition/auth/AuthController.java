package com.ibanking.tuition.auth;

import com.ibanking.tuition.security.JwtService;
import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, CustomerRepository customerRepository, PasswordEncoder passwordEncoder, UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails.getUsername());
        Customer c = customerRepository.findByUsernameIgnoreCase(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "token", token,
                "fullName", c.getFullName(),
                "phone", c.getPhone(),
                "email", c.getEmail(),
                "balance", c.getBalance()
        ));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        Customer c = customerRepository.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "fullName", c.getFullName(),
                "phone", c.getPhone(),
                "email", c.getEmail(),
                "balance", c.getBalance()
        ));
    }
}


