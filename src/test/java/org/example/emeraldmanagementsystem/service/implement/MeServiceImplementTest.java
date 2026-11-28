package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.dto.response.MeResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceImplementTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @InjectMocks
    private MeServiceImplement meService;

    @Test
    void managerResponseHasNoResident() {
        User user = new User();
        user.setId("user-1");
        user.setRole("MANAGER");
        user.setUsername("manager");
        user.setFullName("Manager Name");
        user.setStatus("ACTIVE");
        when(userRepository.findUserByUsernameAndDeletedFalse("manager")).thenReturn(Optional.of(user));

        MeResponse response = meService.getCurrentUser("manager");

        assertEquals("MANAGER", response.role());
        assertNull(response.apartmentCode());
    }

    @Test
    void residentResponseIncludesCurrentApartment() {
        User user = new User();
        user.setId("user-1");
        user.setRole("RESIDENT");
        user.setUsername("resident");
        user.setResidentId("resident-1");
        when(userRepository.findUserByUsernameAndDeletedFalse("resident")).thenReturn(Optional.of(user));

        Residency residency = new Residency();
        residency.setApartmentId("apartment-1");
        when(residencyRepository.findByResidentIdAndEndDateIsNullAndDeletedFalse("resident-1"))
                .thenReturn(Optional.of(residency));

        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setCode("A-1205");
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));

        MeResponse response = meService.getCurrentUser("resident");

        assertEquals("RESIDENT", response.role());
        assertEquals("A-1205", response.apartmentCode());
    }

    @Test
    void residentWithoutActiveResidencyIsForbidden() {
        User user = new User();
        user.setRole("RESIDENT");
        user.setResidentId("resident-1");
        when(userRepository.findUserByUsernameAndDeletedFalse("resident")).thenReturn(Optional.of(user));
        when(residencyRepository.findByResidentIdAndEndDateIsNullAndDeletedFalse("resident-1"))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> meService.getCurrentUser("resident")
        );

        assertEquals(403, exception.getErrorCode());
    }
}
