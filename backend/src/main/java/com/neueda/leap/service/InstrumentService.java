package com.neueda.leap.service;

import com.neueda.leap.model.dto.Instruments;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstrumentService {

    public List<Instruments> getInstruments() {
        
        return List.of(); // Return an empty list for now
    }

}
