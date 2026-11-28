package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.example.emeraldmanagementsystem.entity.IncidentEvent;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.IncidentAttachmentRepository;
import org.example.emeraldmanagementsystem.repository.IncidentEventRepository;
import org.example.emeraldmanagementsystem.repository.IncidentRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceItemRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.example.emeraldmanagementsystem.config.MinioProperties;
import org.example.emeraldmanagementsystem.service.MinioStorageService;
import org.example.emeraldmanagementsystem.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentPortalServiceImplementTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private TowerRepository towerRepository;
    @Mock
    private BillingPeriodRepository billingPeriodRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private IncidentAttachmentRepository incidentAttachmentRepository;
    @Mock
    private IncidentEventRepository incidentEventRepository;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private MinioStorageService minioStorageService;
    @InjectMocks
    private ResidentPortalServiceImplement residentPortalService;

    @Test
    void residentCancellationHasNoReasonAndReportsResidentAsActor() {
        User user = new User();
        user.setUsername("resident-user");
        user.setRole("RESIDENT");
        user.setResidentId("resident-1");
        when(userRepository.findUserByUsernameAndDeletedFalse("resident-user"))
                .thenReturn(Optional.of(user));

        Residency residency = new Residency();
        residency.setApartmentId("apt-1");
        when(residencyRepository.findByResidentIdAndEndDateIsNullAndDeletedFalse("resident-1"))
                .thenReturn(Optional.of(residency));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));

        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setApartmentId("apt-1");
        incident.setStatus(IncidentStatus.NEW);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        IncidentEvent cancelledEvent = new IncidentEvent();
        cancelledEvent.setStatus(IncidentStatus.CANCELLED);
        cancelledEvent.setCreatedBy("resident-user");
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(cancelledEvent));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getUsername).thenReturn("resident-user");

            var response = residentPortalService.cancelIncident("inc-1");

            assertNull(response.cancellation().reason());
            assertEquals("RESIDENT", response.cancellation().cancelledBy());
            assertNull(incident.getCancelReason());
        }
    }
}
