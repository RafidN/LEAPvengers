package com.neueda.leap.controller;

import com.neueda.leap.model.Example;
import com.neueda.leap.service.ExampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Example REST controller.
 * Remove this file and create your own controllers.
 */
@RestController
@RequestMapping("/examples")
public class ExampleController {
    
    @Autowired
    private ExampleService service;
    
    @GetMapping
    public ResponseEntity<List<Example>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Example> getById(@PathVariable Long id) {
        Optional<Example> example = service.findById(id);
        return example.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Example> create(@RequestBody Example example) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.save(example));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Example> update(@PathVariable Long id, @RequestBody Example example) {
        if (!service.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        example.setId(id);
        return ResponseEntity.ok(service.save(example));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
}
