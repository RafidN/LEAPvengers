package com.neueda.leap.controller;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.FreeMoneyBattleStartResult;
import com.neueda.leap.model.dto.FreeMoneyHitRequest;
import com.neueda.leap.model.dto.FreeMoneyHitResult;
import com.neueda.leap.model.dto.FreeMoneyWinResult;
import com.neueda.leap.security.SecurityContextHelper;
import com.neueda.leap.service.FreeMoneyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Free Money game endpoints that mutate holdings based on gameplay events. */
@RestController
@RequestMapping("/games/free-money")
public class FreeMoneyController {

    private final FreeMoneyService freeMoneyService;
    private final SecurityContextHelper securityContextHelper;

    public FreeMoneyController(FreeMoneyService freeMoneyService,
                               SecurityContextHelper securityContextHelper) {
        this.freeMoneyService = freeMoneyService;
        this.securityContextHelper = securityContextHelper;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startBattle(@RequestBody FreeMoneyHitRequest request) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            FreeMoneyBattleStartResult result = freeMoneyService.startBattle(userId, request);
            return ResponseEntity.ok(result);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }

    @PostMapping("/hit")
    public ResponseEntity<?> registerHit(@RequestBody FreeMoneyHitRequest request) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            FreeMoneyHitResult result = freeMoneyService.applyHit(userId, request);
            return ResponseEntity.ok(result);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }

    @PostMapping("/win")
    public ResponseEntity<?> registerBossDefeat(@RequestBody FreeMoneyHitRequest request) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            FreeMoneyWinResult result = freeMoneyService.applyBossDefeat(userId, request);
            return ResponseEntity.ok(result);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }

    @PostMapping("/forfeit")
    public ResponseEntity<?> forfeitBattle(@RequestBody FreeMoneyHitRequest request) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            FreeMoneyHitResult result = freeMoneyService.forfeitBattle(userId, request);
            return ResponseEntity.ok(result);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error");
        }
    }
}
