package com.neueda.leap.controller;

import com.neueda.leap.model.dto.BlackjackState;
import com.neueda.leap.service.BlackjackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Risk Lab blackjack (traders only, like the rest of /games). Play money: no endpoint here touches an account.
 * Every call answers with the whole table, so the page just shows what comes back.
 */
@RestController
@RequestMapping("/games/blackjack")
public class BlackjackController {
    private final BlackjackService blackjackService;

    public BlackjackController(BlackjackService blackjackService) {
        this.blackjackService = blackjackService;
    }

    /** Request bodies: {"stakeTicker": "NFLX"} to sit down (optional), {"bet": 25} to deal. */
    public record Move(String stakeTicker, BigDecimal bet) {
    }

    @GetMapping
    public ResponseEntity<BlackjackState> state() {
        return ResponseEntity.ok(blackjackService.state());
    }

    @PostMapping("/seat")
    public ResponseEntity<BlackjackState> sitDown(@RequestBody(required = false) Move move) {
        return ResponseEntity.ok(blackjackService.sitDown(move == null ? null : move.stakeTicker()));
    }

    @DeleteMapping("/seat")
    public ResponseEntity<Void> leave() {
        blackjackService.leave();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deal")
    public ResponseEntity<BlackjackState> deal(@RequestBody Move move) {
        return ResponseEntity.ok(blackjackService.deal(move.bet()));
    }

    @PostMapping("/hit")
    public ResponseEntity<BlackjackState> hit() {
        return ResponseEntity.ok(blackjackService.hit());
    }

    @PostMapping("/stand")
    public ResponseEntity<BlackjackState> stand() {
        return ResponseEntity.ok(blackjackService.stand());
    }

    @PostMapping("/double")
    public ResponseEntity<BlackjackState> doubleDown() {
        return ResponseEntity.ok(blackjackService.doubleDown());
    }

    @PostMapping("/split")
    public ResponseEntity<BlackjackState> split() {
        return ResponseEntity.ok(blackjackService.split());
    }
}
