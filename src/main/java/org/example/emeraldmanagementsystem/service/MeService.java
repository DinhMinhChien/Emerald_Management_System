package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.response.MeResponse;

public interface MeService {
    MeResponse getCurrentUser(String username);
}
