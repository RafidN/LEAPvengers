package com.neueda.leap.model.dto;

import java.util.List;
import java.util.ArrayList;
import com.neueda.leap.model.dto.Instrument;
import com.neueda.leap.model.dto.Client;

public class AuditResponse {
    private AuditData data;

    public AuditResponse(AuditData data) {
        this.data = data;
    }

    public AuditData getData() {
        return data;
    }

    public void setData(AuditData data) {
        this.data = data;
    }
}
