package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.dto.response.TowerListResponse;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.service.TowerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TowerServiceImplement implements TowerService {
    private final TowerRepository towerRepository;

    @Override
    @Transactional(readOnly = true)
    public TowerListResponse getTowers() {
        return new TowerListResponse(
                towerRepository.findAllByDeletedFalseOrderByCodeAsc().stream()
                        .map(tower -> new TowerListResponse.TowerItem(
                                tower.getId(),
                                tower.getCode(),
                                tower.getName()
                        ))
                        .toList()
        );
    }
}
