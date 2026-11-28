package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.dto.response.TowerListResponse;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TowerServiceImplementTest {
    @Mock
    private TowerRepository towerRepository;
    @InjectMocks
    private TowerServiceImplement towerService;

    @Test
    void returnsTowerItemsWithoutEntityAuditFields() {
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        when(towerRepository.findAllByDeletedFalseOrderByCodeAsc()).thenReturn(List.of(tower));

        TowerListResponse response = towerService.getTowers();

        assertEquals(
                List.of(new TowerListResponse.TowerItem("tower-1", "A", "Tòa A")),
                response.items()
        );
        verify(towerRepository).findAllByDeletedFalseOrderByCodeAsc();
    }

    @Test
    void returnsEmptyItemsWhenNoTowersExist() {
        when(towerRepository.findAllByDeletedFalseOrderByCodeAsc()).thenReturn(List.of());

        TowerListResponse response = towerService.getTowers();

        assertEquals(List.of(), response.items());
    }
}
