package com.omes.dgw.modscan.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModscanScanResult {

    private boolean success;
    private String message;
    private long latencyMs;
    private Integer unitId;
    private String connectionTarget;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private int offset;
        private String address;
        private String legacyAddress;
        private String binaryValue;
    }

    public static ModscanScanResult fail(String message) {
        ModscanScanResult r = new ModscanScanResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
