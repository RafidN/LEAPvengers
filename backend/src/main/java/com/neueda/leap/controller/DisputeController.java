package com.neueda.leap.controller;

import com.neueda.leap.model.dto.DisputeRequest;
import com.neueda.leap.model.dto.DisputeResult;
import com.neueda.leap.service.DisputeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A trader's disputes of their own orders (traders only, like the rest of /orders). Operations works them
 * through /internal/audit/disputes.
 */
@RestController
@RequestMapping("/orders")
public class DisputeController {
    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** POST /orders/{id}/disputes {"reason": "SHOULD_HAVE_FILLED", "message": "..."}; 403 for someone else's order. */
    @PostMapping("/{id}/disputes")
    public ResponseEntity<DisputeResult> raise(@PathVariable("id") Integer orderId, @RequestBody DisputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.raise(orderId, request));
    }

    /** GET /orders/disputes: the signed-in trader's disputes, newest first. */
    @GetMapping("/disputes")
    public ResponseEntity<List<DisputeResult>> mine() {
        return ResponseEntity.ok(disputeService.forCurrentClient());
    }
}
