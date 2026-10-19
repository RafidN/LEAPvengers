package com.neueda.leap.controller;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.BlackjackPlayRequest;
import com.neueda.leap.model.dto.BlackjackRoundResult;
import com.neueda.leap.security.SecurityContextHelper;
import com.neueda.leap.service.BlackjackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for trader blackjack rounds.
 */
@RestController
@RequestMapping("/games/blackjack")
public class BlackjackController {

    private final BlackjackService blackjackService;
    private final SecurityContextHelper securityContextHelper;

    public BlackjackController(BlackjackService blackjackService,
                               SecurityContextHelper securityContextHelper) {
        this.blackjackService = blackjackService;
        this.securityContextHelper = securityContextHelper;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody BlackjackPlayRequest request) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            BlackjackRoundResult result = blackjackService.startRound(userId, request);
            return ResponseEntity.ok(result);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/{roundId}/hit")
    public ResponseEntity<?> hit(@PathVariable String roundId) {
        return runAction(() -> blackjackService.hit(securityContextHelper.getUserIdFromContext(), roundId));
    }

    @PostMapping("/{roundId}/stand")
    public ResponseEntity<?> stand(@PathVariable String roundId) {
        return runAction(() -> blackjackService.stand(securityContextHelper.getUserIdFromContext(), roundId));
    }

    @PostMapping("/{roundId}/split")
    public ResponseEntity<?> split(@PathVariable String roundId) {
        return runAction(() -> blackjackService.split(securityContextHelper.getUserIdFromContext(), roundId));
    }

    private ResponseEntity<?> runAction(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @FunctionalInterface
    private interface Action {
        BlackjackRoundResult run();
    }
}