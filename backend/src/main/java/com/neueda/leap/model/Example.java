package com.neueda.leap.model;

/**
 * Example model class.
 * Remove this file and create your own models.
 */
public class Example {
    
    private Long id;
    private String name;
    
    public Example() {}
    
    public Example(String name) {
        this.name = name;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
}
