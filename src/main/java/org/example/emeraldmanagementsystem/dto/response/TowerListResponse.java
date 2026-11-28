package org.example.emeraldmanagementsystem.dto.response;

import java.util.List;

public record TowerListResponse(List<TowerItem> items) {
    public record TowerItem(String id, String code, String name) {
    }
}
