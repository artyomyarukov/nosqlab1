package com.yarukov.nosql.controller;

import com.yarukov.nosql.dto.*;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.ActionEvent;
import com.yarukov.nosql.model.riak.CachedUserProfile;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import com.yarukov.nosql.service.RiakService;
import com.yarukov.nosql.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RiakService riakService;
    private final UserService userService;


    @GetMapping
    public List<UserListItemResponse> getAllUsers() {
        return userService.getAllUsers();
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUserProfile(@PathVariable Long id, @RequestBody UpdateUserRequest request,
                                               @RequestHeader(value = "X-Session-Token", required = false) String token) {

        return ResponseEntity.ok(userService.updateUserProfile(id, request, token));
    }


    @PostMapping("/{id}/notify")
    public ResponseEntity<?> notifyUser(@PathVariable Long id, @Valid @RequestBody NotificationRequest request,
                                        @RequestHeader(value = "X-Session-Token", required = false) String token) {
       return ResponseEntity.ok(userService.sendNotification(id,request,token));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ActionEvent>> getUserHistory(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserActionHistory(id));
    }
}