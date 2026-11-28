package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.dto.response.IncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentSummaryResponse;
import org.example.emeraldmanagementsystem.dto.request.IncidentStartRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCompleteRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCancelRequest;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.example.emeraldmanagementsystem.entity.IncidentAttachment;
import org.example.emeraldmanagementsystem.entity.IncidentEvent;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.repository.IncidentAttachmentRepository;
import org.example.emeraldmanagementsystem.repository.IncidentEventRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.IncidentRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.service.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceImplementTest {
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private TowerRepository towerRepository;
    @Mock
    private IncidentEventRepository incidentEventRepository;
    @Mock
    private IncidentAttachmentRepository incidentAttachmentRepository;
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MinioStorageService minioStorageService;
    @InjectMocks
    private IncidentServiceImplement incidentService;

    @Test
    void mapsIncidentApartmentAndTower() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setTitle("Đèn hành lang hỏng");
        incident.setArea("Hành lang tầng 12");
        incident.setApartmentId("apt-1");
        incident.setReporterName("Nguyễn Thị E");
        incident.setChannel(IncidentChannel.PHONE);
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setAssigneeName("Lê Minh T");
        incident.setCreatedAt(LocalDateTime.now());

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");

        when(incidentRepository.searchIncidents(
                eq("%hỏng%"),
                eq("tower-1"),
                eq(IncidentStatus.IN_PROGRESS),
                eq("apt-1"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(incident)));
        when(apartmentRepository.findAllById(List.of("apt-1")))
                .thenReturn(List.of(apartment));
        when(towerRepository.findAllById(List.of("tower-1")))
                .thenReturn(List.of(tower));

        IncidentListResponse response = incidentService.getIncidents(
                " hỏng ",
                "tower-1",
                "IN_PROGRESS",
                "apt-1",
                0,
                20
        );

        assertEquals(1, response.total());
        assertEquals("A-1205", response.items().getFirst().reporterApartmentCode());
        assertEquals("Tòa A", response.items().getFirst().tower().name());
        assertEquals("A", response.items().getFirst().tower().code());
    }

    @Test
    void rejectsInvalidStatus() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.getIncidents(null, null, "UNKNOWN", null, 0, 20)
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
    }

    @Test
    void rejectsInvalidPage() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.getIncidents(null, null, null, null, -1, 20)
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
    }

    @Test
    void calculatesIncidentSummaryByTower() {
        when(incidentRepository.getIncidentSummary("tower-1"))
                .thenReturn(summary(3, 2, 4, 5, 1));

        IncidentSummaryResponse response = incidentService.getSummary(" tower-1 ");

        assertEquals(15, response.total());
        assertEquals(3, response.newCount());
        assertEquals(2, response.acknowledged());
        assertEquals(4, response.inProgress());
        assertEquals(5, response.closed());
        assertEquals(1, response.cancelled());
    }

    @Test
    void returnsZeroSummaryWhenNoProjectionRowExists() {
        when(incidentRepository.getIncidentSummary(null)).thenReturn(null);

        IncidentSummaryResponse response = incidentService.getSummary(null);

        assertEquals(new IncidentSummaryResponse(0, 0, 0, 0, 0, 0), response);
    }

    @Test
    void returnsIncidentDetailWithTimelineAndAllowedActions() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setTitle("Đèn hành lang hỏng");
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setArea("Hành lang tầng 12");
        incident.setApartmentId("apt-1");
        incident.setReportedBy("res-1");
        incident.setReporterName("Nguyễn Thị E");
        incident.setChannel(IncidentChannel.PHONE);
        incident.setAssigneeName("Lê Minh T");
        incident.setCreatedAt(LocalDateTime.now().minusHours(2));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        Resident resident = new Resident();
        resident.setId("res-1");
        resident.setFullName("Nguyễn Thị E");
        Residency residency = new Residency();
        residency.setRelation(org.example.emeraldmanagementsystem.common.enums.ResidencyRelation.SPOUSE);

        IncidentAttachment attachment = new IncidentAttachment();
        attachment.setId("att-1");
        attachment.setStorageKey("incidents/2026/hanh-lang.jpg");
        IncidentEvent event = new IncidentEvent();
        event.setId("event-1");
        event.setStatus(IncidentStatus.IN_PROGRESS);
        event.setAssigneeName("Lê Minh T");
        event.setCreatedAt(LocalDateTime.now().minusHours(1));
        event.setCreatedBy("manager");

        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));
        when(residentRepository.findByIdAndDeletedFalse("res-1")).thenReturn(Optional.of(resident));
        when(residencyRepository.findByResidentIdAndEndDateIsNullAndDeletedFalse("res-1"))
                .thenReturn(Optional.of(residency));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(attachment));
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(event));

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.getIncidentDetails("inc-1");

        assertEquals("A-1205", response.apartment().code());
        assertEquals("Nguyễn Thị E", response.reporter().fullName());
        assertEquals(1, response.attachments().size());
        assertEquals(1, response.timeline().size());
        assertEquals(List.of("COMPLETE", "REVERT", "CHANGE_ASSIGNEE", "CANCEL"),
                response.allowedActions());
    }

    @Test
    void createsIncidentWithReporterNameAndNoFiles() {
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId("inc-1");
            return incident;
        });
        when(incidentRepository.findById("inc-1")).thenAnswer(invocation -> {
            Incident incident = new Incident();
            incident.setId("inc-1");
            incident.setTitle("Đèn hỏng");
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            incident.setArea("Hành lang");
            incident.setApartmentId("apt-1");
            incident.setReporterName("Nguyễn Văn Khách");
            incident.setChannel(IncidentChannel.PHONE);
            incident.setCreatedAt(LocalDateTime.now());
            return Optional.of(incident);
        });
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(
                new Tower() {{
                    setId("tower-1");
                    setCode("A");
                    setName("Tòa A");
                }}
        ));
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.createIncident(
                        "apt-1",
                        null,
                        "Nguyễn Văn Khách",
                        "PHONE",
                        "Hành lang",
                        "Đèn hỏng",
                        null,
                        List.of()
                );

        assertEquals("inc-1", response.id());
        assertEquals(IncidentStatus.ACKNOWLEDGED, response.status());
        assertEquals("Nguyễn Văn Khách", response.reporterName());
    }

    @Test
    void rejectsTooManyIncidentFiles() {
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));

        List<org.springframework.web.multipart.MultipartFile> files =
                java.util.stream.IntStream.range(0, 6)
                .<org.springframework.web.multipart.MultipartFile>mapToObj(index -> new MockMultipartFile(
                        "files",
                        "image-" + index + ".jpg",
                        "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}
                ))
                .toList();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.createIncident(
                        "apt-1",
                        null,
                        "Khách",
                        "PHONE",
                        "Hành lang",
                        "Đèn hỏng",
                        null,
                        files
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("TOO_MANY_FILES", exception.getMessage());
    }

    @Test
    void rejectsMissingReporter() {
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.createIncident(
                        "apt-1",
                        null,
                        null,
                        "PHONE",
                        "Hành lang",
                        "Đèn hỏng",
                        null,
                        List.of()
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("REPORTER_REQUIRED", exception.getMessage());
    }

    @Test
    void acknowledgesNewIncident() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.NEW);
        incident.setApartmentId("apt-1");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.empty());
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.acknowledgeIncident("inc-1");

        assertEquals(IncidentStatus.ACKNOWLEDGED, response.status());
        org.mockito.Mockito.verify(incidentRepository).saveAndFlush(incident);
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getIncidentId().equals("inc-1")
                                && event.getStatus() == IncidentStatus.ACKNOWLEDGED
                )
        );
    }

    @Test
    void rejectsAcknowledgeForInvalidStatus() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.CLOSED);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.acknowledgeIncident("inc-1")
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @Test
    void startsAcknowledgedIncidentWithAssignee() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setApartmentId("apt-1");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.empty());
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.startIncident(
                        "inc-1",
                        new IncidentStartRequest("Lê Minh T")
                );

        assertEquals(IncidentStatus.IN_PROGRESS, response.status());
        assertEquals("Lê Minh T", response.assigneeName());
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getIncidentId().equals("inc-1")
                                && event.getStatus() == IncidentStatus.IN_PROGRESS
                                && event.getAssigneeName().equals("Lê Minh T")
                )
        );
    }

    @Test
    void rejectsStartWithoutAssignee() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.startIncident(
                        "inc-1",
                        new IncidentStartRequest(" ")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("ASSIGNEE_REQUIRED", exception.getMessage());
    }

    @Test
    void rejectsStartForInvalidStatus() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.NEW);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.startIncident(
                        "inc-1",
                        new IncidentStartRequest("Lê Minh T")
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @Test
    void completesInProgressIncidentWithResultNote() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setApartmentId("apt-1");
        incident.setAssigneeName("Lê Minh T");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.empty());
        IncidentEvent closedEvent = new IncidentEvent();
        closedEvent.setStatus(IncidentStatus.CLOSED);
        closedEvent.setNote("Đã thay bóng đèn mới.");
        closedEvent.setCreatedAt(LocalDateTime.now());
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(closedEvent));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.completeIncident(
                        "inc-1",
                        new IncidentCompleteRequest("Đã thay bóng đèn mới.")
                );

        assertEquals(IncidentStatus.CLOSED, response.status());
        assertEquals("Đã thay bóng đèn mới.", response.resultNote());
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getStatus() == IncidentStatus.CLOSED
                                && event.getNote().equals("Đã thay bóng đèn mới.")
                )
        );
    }

    @Test
    void completesIncidentWithoutResultNote() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setApartmentId("apt-1");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());
        IncidentEvent closedEvent = new IncidentEvent();
        closedEvent.setStatus(IncidentStatus.CLOSED);
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(closedEvent));

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.completeIncident("inc-1", new IncidentCompleteRequest(null));

        assertEquals(IncidentStatus.CLOSED, response.status());
        assertNull(response.resultNote());
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getStatus() == IncidentStatus.CLOSED && event.getNote() == null
                )
        );
    }

    @Test
    void rejectsCompleteForInvalidStatus() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.NEW);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.completeIncident(
                        "inc-1",
                        new IncidentCompleteRequest("Đã xử lý")
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @Test
    void revertsInProgressIncidentToAcknowledged() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setApartmentId("apt-1");
        incident.setAssigneeName("Lê Minh T");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.empty());
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.revertIncident("inc-1");

        assertEquals(IncidentStatus.ACKNOWLEDGED, response.status());
        assertEquals(null, response.assigneeName());
        org.mockito.Mockito.verify(incidentRepository).saveAndFlush(incident);
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getIncidentId().equals("inc-1")
                                && event.getStatus() == IncidentStatus.ACKNOWLEDGED
                                && event.getAssigneeName() == null
                )
        );
    }

    @Test
    void rejectsRevertForInvalidStatus() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.CLOSED);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.revertIncident("inc-1")
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @Test
    void returnsIncidentNotFoundForRevert() {
        when(incidentRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.revertIncident("missing")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("INCIDENT_NOT_FOUND", exception.getMessage());
    }

    @Test
    void cancelsInProgressIncident() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setApartmentId("apt-1");
        incident.setAssigneeName("Lê Minh T");
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.empty());
        IncidentEvent cancelledEvent = new IncidentEvent();
        cancelledEvent.setStatus(IncidentStatus.CANCELLED);
        cancelledEvent.setNote("Trùng yêu cầu");
        cancelledEvent.setCreatedAt(LocalDateTime.now());
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(cancelledEvent));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse response =
                incidentService.cancelIncident(
                        "inc-1",
                        new IncidentCancelRequest("DUPLICATE", "Trùng yêu cầu")
                );

        assertEquals(IncidentStatus.CANCELLED, response.status());
        assertEquals(org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason.DUPLICATE,
                response.cancellation().reason());
        assertEquals("Trùng yêu cầu", response.cancellation().note());
        org.mockito.Mockito.verify(incidentEventRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.getStatus() == IncidentStatus.CANCELLED
                                && event.getNote().equals("Trùng yêu cầu")
                )
        );
    }

    @Test
    void requiresNoteWhenCancelReasonIsOther() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.NEW);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.cancelIncident(
                        "inc-1",
                        new IncidentCancelRequest("OTHER", " ")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("CANCEL_NOTE_REQUIRED", exception.getMessage());
    }

    @Test
    void rejectsCancelForClosedOrCancelledIncident() {
        Incident closed = new Incident();
        closed.setId("inc-1");
        closed.setStatus(IncidentStatus.CLOSED);
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(closed));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.cancelIncident(
                        "inc-1",
                        new IncidentCancelRequest("MISTAKE", null)
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getMessage());
    }

    @Test
    void mapsResidentCancellationActorFromEventAudit() {
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setStatus(IncidentStatus.CANCELLED);
        incident.setApartmentId("apt-1");
        incident.setCreatedAt(LocalDateTime.now());
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(incident));

        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of());

        IncidentEvent cancelledEvent = new IncidentEvent();
        cancelledEvent.setStatus(IncidentStatus.CANCELLED);
        cancelledEvent.setCreatedBy("resident-user");
        when(incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inc-1"))
                .thenReturn(List.of(cancelledEvent));

        User residentUser = new User();
        residentUser.setRole("RESIDENT");
        when(userRepository.findUserByUsernameAndDeletedFalse("resident-user"))
                .thenReturn(Optional.of(residentUser));

        var response = incidentService.getIncidentDetails("inc-1");

        assertNull(response.cancellation().reason());
        assertEquals("RESIDENT", response.cancellation().cancelledBy());
    }

    @Test
    void returnsIncidentNotFound() {
        when(incidentRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> incidentService.getIncidentDetails("missing")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("INCIDENT_NOT_FOUND", exception.getMessage());
    }

    private IncidentRepository.IncidentSummaryProjection summary(
            long newCount,
            long acknowledged,
            long inProgress,
            long closed,
            long cancelled
    ) {
        return new IncidentRepository.IncidentSummaryProjection() {
            public Long getTotal() {
                return newCount + acknowledged + inProgress + closed + cancelled;
            }
            public Long getNewCount() { return newCount; }
            public Long getAcknowledged() { return acknowledged; }
            public Long getInProgress() { return inProgress; }
            public Long getClosed() { return closed; }
            public Long getCancelled() { return cancelled; }
        };
    }
}
