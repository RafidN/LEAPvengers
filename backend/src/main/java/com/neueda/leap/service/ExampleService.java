package com.neueda.leap.service;

import com.neueda.leap.model.Example;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Example service.
 * Remove this file and create your own services.
 */
@Service
public class ExampleService {
    
    private List<Example> examples = new ArrayList<>();
    
    public List<Example> findAll() {
        return examples;
    }
    
    public Optional<Example> findById(Long id) {
        return examples.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }
    
    public Example save(Example example) {
        if (example.getId() == null) {
            example.setId((long) (examples.size() + 1));
        }
        examples.add(example);
        return example;
    }
    
    public void deleteById(Long id) {
        examples.removeIf(e -> e.getId().equals(id));
    }
    
}
