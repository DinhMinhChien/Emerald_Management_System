package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.dto.request.ResidentUpdateRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentDetailResponse;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentProfileUpdateServiceTest {
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private Keycloak keycloak;
    @InjectMocks
    private ResidentServiceImplement residentService;

    @Test
    void updatesResidentAndPreservesNationalIdWhenRequestOmitsIt() {
        Resident resident = resident();
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident));
        when(residencyRepository.findCurrentResidencyDetailsByResidentId("resident-1")).thenReturn(Optional.empty());
        when(residencyRepository.findResidencyHistoryByResidentId("resident-1")).thenReturn(List.of());
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.empty());

        ResidentDetailResponse response = residentService.updateResident(
                "resident-1",
                new ResidentUpdateRequest(
                        "Nguyễn Văn Bình",
                        LocalDate.of(1987, 2, 10),
                        Gender.MALE,
                        "0912345678",
                        "binh.nguyen@example.com",
                        null
                )
        );

        assertEquals("Nguyễn Văn Bình", resident.getFullName());
        assertEquals(LocalDate.of(1987, 2, 10), resident.getBirthDate());
        assertEquals(Gender.MALE, resident.getGender());
        assertEquals("0912345678", resident.getPhone());
        assertEquals("binh.nguyen@example.com", resident.getEmail());
        assertEquals("012345678789", resident.getNationalId());
        assertEquals("012******789", response.nationalIdMasked());
        assertEquals("Nguyễn Văn Bình", response.fullName());
        verify(residentRepository).saveAndFlush(resident);
    }

    @Test
    void clearsOptionalFieldsAndKeepsNationalIdWhenRequestUsesBlankValue() {
        Resident resident = resident();
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident));
        when(residencyRepository.findCurrentResidencyDetailsByResidentId("resident-1")).thenReturn(Optional.empty());
        when(residencyRepository.findResidencyHistoryByResidentId("resident-1")).thenReturn(List.of());
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.empty());

        residentService.updateResident(
                "resident-1",
                new ResidentUpdateRequest("Nguyễn Văn Bình", null, null, null, null, " ")
        );

        assertNull(resident.getBirthDate());
        assertNull(resident.getGender());
        assertNull(resident.getPhone());
        assertNull(resident.getEmail());
        assertEquals("012345678789", resident.getNationalId());
        verify(residentRepository).saveAndFlush(resident);
    }

    @Test
    void returnsNotFoundWhenResidentDoesNotExist() {
        when(residentRepository.findByIdAndDeletedFalse("missing-resident")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.updateResident(
                        "missing-resident",
                        new ResidentUpdateRequest("Tên cư dân", null, null, null, null, null)
                )
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("RESIDENT_NOT_FOUND", exception.getMessage());
        verify(residentRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Resident.class));
    }

    private Resident resident() {
        Resident resident = new Resident();
        resident.setId("resident-1");
        resident.setFullName("Nguyễn Văn An");
        resident.setBirthDate(LocalDate.of(1985, 9, 20));
        resident.setGender(Gender.OTHER);
        resident.setPhone("0901234567");
        resident.setEmail("an.nguyen@example.com");
        resident.setNationalId("012345678789");
        return resident;
    }
}
