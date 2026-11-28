package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.dto.response.ResidentListResponse;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentServiceImplementTest {
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ResidentServiceImplement residentService;

    @Test
    void searchesResidentsAndMapsCurrentApartmentAndAccountStatus() {
        ResidentRepository.ResidentListProjection head = resident(
                "resident-head",
                "Nguyễn Văn An",
                "0901111111",
                "apartment-1",
                "A-1205",
                "A",
                "Tòa A",
                ResidencyRole.HEAD,
                null
        );
        ResidentRepository.ResidentListProjection member = resident(
                "resident-member",
                "Nguyễn Thị Hoa",
                null,
                "apartment-1",
                "A-1205",
                "A",
                "Tòa A",
                ResidencyRole.MEMBER,
                ResidencyRelation.SPOUSE
        );
        ResidentRepository.ResidentListProjection formerResident = resident(
                "resident-former",
                "Trần Văn Bình",
                "0902222222",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(residentRepository.searchResidents(
                eq("%hoa%"),
                eq("tower-1"),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(head, member, formerResident), PageRequest.of(0, 20), 410));
        when(userRepository.findResidentAccountStatuses(List.of(
                "resident-head",
                "resident-member",
                "resident-former"
        ))).thenReturn(List.of(
                accountStatus("resident-head", "ACTIVE"),
                accountStatus("resident-former", "LOCKED")
        ));

        ResidentListResponse response = residentService.getResidents(" Hoa ", "tower-1", false, 0, 20);

        assertEquals(410, response.total());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(new ResidentListResponse.Apartment("apartment-1", "A-1205", "A", "Tòa A"),
                response.items().getFirst().apartment());
        assertEquals("ACTIVE", response.items().getFirst().accountStatus());
        assertNull(response.items().getFirst().relation());
        assertEquals(ResidencyRelation.SPOUSE, response.items().get(1).relation());
        assertEquals("NONE", response.items().get(1).accountStatus());
        assertNull(response.items().getLast().apartment());
        assertNull(response.items().getLast().role());
        assertEquals("LOCKED", response.items().getLast().accountStatus());
    }

    @Test
    void rejectsInvalidPagination() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.getResidents(null, null, true, 0, 101)
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
        verify(residentRepository, never()).searchResidents(any(), any(), eq(true), any());
    }

    private ResidentRepository.ResidentListProjection resident(
            String id,
            String fullName,
            String phone,
            String apartmentId,
            String apartmentCode,
            String towerCode,
            String towerName,
            ResidencyRole role,
            ResidencyRelation relation
    ) {
        return new ResidentRepository.ResidentListProjection() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public String getApartmentId() {
                return apartmentId;
            }

            @Override
            public String getApartmentCode() {
                return apartmentCode;
            }

            @Override
            public String getTowerCode() {
                return towerCode;
            }

            @Override
            public String getTowerName() {
                return towerName;
            }

            @Override
            public ResidencyRole getRole() {
                return role;
            }

            @Override
            public ResidencyRelation getRelation() {
                return relation;
            }
        };
    }

    private UserRepository.ResidentAccountStatusProjection accountStatus(String residentId, String status) {
        return new UserRepository.ResidentAccountStatusProjection() {
            @Override
            public String getResidentId() {
                return residentId;
            }

            @Override
            public String getStatus() {
                return status;
            }
        };
    }
}
