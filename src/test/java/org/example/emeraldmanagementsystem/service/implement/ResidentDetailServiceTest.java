package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.dto.response.ResidentDetailResponse;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentDetailServiceTest {
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ResidentServiceImplement residentService;

    @Test
    void returnsMaskedResidentDetailsWithAccountAndResidencyHistory() {
        Resident resident = new Resident();
        resident.setId("resident-1");
        resident.setFullName("Nguyễn Văn An");
        resident.setBirthDate(LocalDate.of(1985, 9, 20));
        resident.setGender(Gender.MALE);
        resident.setPhone("0901234567");
        resident.setEmail("an.nguyen@example.com");
        resident.setNationalId("01234567789");
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident));

        when(residencyRepository.findCurrentResidencyDetailsByResidentId("resident-1"))
                .thenReturn(Optional.of(currentResidency(
                        "residency-1",
                        "apartment-1",
                        "A-1205",
                        "A",
                        "Tòa A",
                        ResidencyRole.HEAD,
                        null,
                        LocalDate.of(2024, 3, 1)
                )));
        when(residencyRepository.findCurrentHousemates("apartment-1", "resident-1"))
                .thenReturn(List.of(housemate("resident-2", "Nguyễn Thị Hoa", ResidencyRelation.SPOUSE)));
        when(residencyRepository.findResidencyHistoryByResidentId("resident-1"))
                .thenReturn(List.of(
                        history(
                                "residency-1", "apartment-1", "A-1205", "A", "Tòa A",
                                ResidencyRole.HEAD, null, LocalDate.of(2024, 3, 1), null, null
                        ),
                        history(
                                "residency-old", "apartment-old", "B-0201", "B", "Tòa B",
                                ResidencyRole.MEMBER, ResidencyRelation.CHILD, LocalDate.of(2010, 1, 1),
                                LocalDate.of(2015, 1, 1), ResidencyEndReason.MOVED_OUT
                        )
                ));
        User user = new User();
        user.setId("user-1");
        user.setUsername("0901234567");
        user.setStatus("ACTIVE");
        user.setMustChangePassword(false);
        user.setCreatedAt(LocalDateTime.of(2024, 3, 1, 9, 0));
        user.setLastLoginAt(LocalDateTime.of(2026, 11, 27, 8, 15));
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(user));

        ResidentDetailResponse response = residentService.getResidentDetails("resident-1");

        assertEquals("012*****789", response.nationalIdMasked());
        assertEquals(new ResidentDetailResponse.Account(
                "user-1",
                "0901234567",
                "ACTIVE",
                false,
                OffsetDateTime.of(LocalDateTime.of(2024, 3, 1, 9, 0), ZoneOffset.ofHours(7)),
                OffsetDateTime.of(LocalDateTime.of(2026, 11, 27, 8, 15), ZoneOffset.ofHours(7))
        ), response.account());
        assertEquals(ResidencyRole.HEAD, response.currentResidency().role());
        assertNull(response.currentResidency().relation());
        assertEquals("resident-2", response.currentResidency().housemates().getFirst().id());
        assertEquals(ResidencyRelation.SPOUSE, response.currentResidency().housemates().getFirst().relation());
        assertEquals(2, response.residencyHistory().size());
        assertEquals("residency-old", response.residencyHistory().getLast().residencyId());
        assertEquals(ResidencyRelation.CHILD, response.residencyHistory().getLast().relation());
    }

    @Test
    void omitsAccountForCurrentMember() {
        Resident resident = new Resident();
        resident.setId("resident-member");
        resident.setNationalId("12345");
        when(residentRepository.findByIdAndDeletedFalse("resident-member")).thenReturn(Optional.of(resident));
        when(residencyRepository.findCurrentResidencyDetailsByResidentId("resident-member"))
                .thenReturn(Optional.of(currentResidency(
                        "residency-member",
                        "apartment-1",
                        "A-1205",
                        "A",
                        "Tòa A",
                        ResidencyRole.MEMBER,
                        ResidencyRelation.SPOUSE,
                        LocalDate.of(2024, 3, 2)
                )));
        when(residencyRepository.findCurrentHousemates("apartment-1", "resident-member"))
                .thenReturn(List.of());
        when(residencyRepository.findResidencyHistoryByResidentId("resident-member")).thenReturn(List.of());

        ResidentDetailResponse response = residentService.getResidentDetails("resident-member");

        assertNull(response.account());
        assertEquals("*****", response.nationalIdMasked());
        assertEquals(ResidencyRelation.SPOUSE, response.currentResidency().relation());
        verify(userRepository, never()).findByResidentIdAndDeletedFalse("resident-member");
    }

    @Test
    void returnsNotFoundForMissingResident() {
        when(residentRepository.findByIdAndDeletedFalse("missing-resident")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.getResidentDetails("missing-resident")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("RESIDENT_NOT_FOUND", exception.getMessage());
        verify(residencyRepository, never()).findCurrentResidencyDetailsByResidentId("missing-resident");
    }

    private ResidencyRepository.ResidentCurrentResidencyProjection currentResidency(
            String residencyId,
            String apartmentId,
            String apartmentCode,
            String towerCode,
            String towerName,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate
    ) {
        return new ResidencyRepository.ResidentCurrentResidencyProjection() {
            @Override
            public String getResidencyId() {
                return residencyId;
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

            @Override
            public LocalDate getStartDate() {
                return startDate;
            }
        };
    }

    private ResidencyRepository.ResidentResidencyHistoryProjection history(
            String residencyId,
            String apartmentId,
            String apartmentCode,
            String towerCode,
            String towerName,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            LocalDate endDate,
            ResidencyEndReason endReason
    ) {
        return new ResidencyRepository.ResidentResidencyHistoryProjection() {
            @Override
            public String getResidencyId() {
                return residencyId;
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

            @Override
            public LocalDate getStartDate() {
                return startDate;
            }

            @Override
            public LocalDate getEndDate() {
                return endDate;
            }

            @Override
            public ResidencyEndReason getEndReason() {
                return endReason;
            }
        };
    }

    private ResidencyRepository.HousemateProjection housemate(
            String residentId,
            String fullName,
            ResidencyRelation relation
    ) {
        return new ResidencyRepository.HousemateProjection() {
            @Override
            public String getResidentId() {
                return residentId;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public ResidencyRelation getRelation() {
                return relation;
            }
        };
    }
}
