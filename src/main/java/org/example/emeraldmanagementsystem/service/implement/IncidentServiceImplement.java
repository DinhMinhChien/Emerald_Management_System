package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.dto.request.IncidentStartRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCompleteRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCancelRequest;
import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.utils.SecurityUtils;
import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.dto.response.IncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse;
import org.example.emeraldmanagementsystem.entity.IncidentEvent;
import org.example.emeraldmanagementsystem.entity.IncidentAttachment;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.repository.IncidentEventRepository;
import org.example.emeraldmanagementsystem.repository.IncidentAttachmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.service.MinioStorageService;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.IncidentRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.service.IncidentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentServiceImplement implements IncidentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> VALID_STATUSES = Set.of(
            "NEW",
            "ACKNOWLEDGED",
            "IN_PROGRESS",
            "CLOSED",
            "CANCELLED"
    );

    private final IncidentRepository incidentRepository;
    private final ApartmentRepository apartmentRepository;
    private final TowerRepository towerRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAttachmentRepository incidentAttachmentRepository;
    private final ResidentRepository residentRepository;
    private final ResidencyRepository residencyRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;

    @Override
    @Transactional(readOnly = true)
    public IncidentListResponse getIncidents(
            String query,
            String towerId,
            String status,
            String apartmentId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        IncidentStatus incidentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                incidentStatus = IncidentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(400, "VALIDATION_ERROR");
            }
            if (!VALID_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException(400, "VALIDATION_ERROR");
            }
        }
        String normalizedQuery = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedTowerId = towerId == null || towerId.isBlank() ? null : towerId.trim();
        String normalizedApartmentId = apartmentId == null || apartmentId.isBlank()
                ? null
                : apartmentId.trim();

        Page<Incident> incidentPage = incidentRepository.searchIncidents(
                normalizedQuery,
                normalizedTowerId,
                incidentStatus,
                normalizedApartmentId,
                PageRequest.of(page, size)
        );

        java.util.Map<String, Apartment> apartmentsById = apartmentRepository.findAllById(
                        incidentPage.getContent().stream()
                                .map(Incident::getApartmentId)
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(Apartment::getId, apartment -> apartment));
        java.util.Map<String, Tower> towersById = towerRepository.findAllById(
                        apartmentsById.values().stream()
                                .map(Apartment::getTowerId)
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(Tower::getId, tower -> tower));

        java.util.List<IncidentListResponse.IncidentItem> items = incidentPage.getContent().stream()
                .map(incident -> toIncidentItem(
                        incident,
                        apartmentsById.get(incident.getApartmentId()),
                        towersById
                ))
                .toList();
        return new IncidentListResponse(items, incidentPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentSummaryResponse getSummary(String towerId) {
        String normalizedTowerId = towerId == null || towerId.isBlank()
                ? null
                : towerId.trim();
        IncidentRepository.IncidentSummaryProjection projection =
                incidentRepository.getIncidentSummary(normalizedTowerId);
        if (projection == null) {
            return new IncidentSummaryResponse(0, 0, 0, 0, 0, 0);
        }
        long newCount = valueOrZero(projection.getNewCount());
        long acknowledged = valueOrZero(projection.getAcknowledged());
        long inProgress = valueOrZero(projection.getInProgress());
        long closed = valueOrZero(projection.getClosed());
        long cancelled = valueOrZero(projection.getCancelled());
        long total = newCount + acknowledged + inProgress + closed + cancelled;
        return new IncidentSummaryResponse(
                total,
                newCount,
                acknowledged,
                inProgress,
                closed,
                cancelled
        );
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    @Override
    @Transactional
    public IncidentDetailResponse createIncident(
            String apartmentId,
            String reportedBy,
            String reporterName,
            String channel,
            String area,
            String title,
            String description,
            List<MultipartFile> files
    ) {
        if (apartmentId == null || apartmentId.isBlank()) {
            throw new BusinessException(404, "APARTMENT_NOT_FOUND");
        }
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(apartmentId.trim())
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        if (title == null || title.isBlank() || area == null || area.isBlank()
                || channel == null || channel.isBlank()) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        boolean hasReportedBy = reportedBy != null && !reportedBy.isBlank();
        boolean hasReporterName = reporterName != null && !reporterName.isBlank();
        if (!hasReportedBy && !hasReporterName) {
            throw new BusinessException(422, "REPORTER_REQUIRED");
        }

        String snapshotReporterName = null;
        if (hasReportedBy) {
            if (!residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                    apartment.getId(),
                    reportedBy.trim()
            )) {
                throw new BusinessException(422, "REPORTER_NOT_IN_APARTMENT");
            }
            snapshotReporterName = residentRepository.findByIdAndDeletedFalse(reportedBy.trim())
                    .map(Resident::getFullName)
                    .orElse(null);
            if (snapshotReporterName == null) {
                throw new BusinessException(422, "REPORTER_NOT_IN_APARTMENT");
            }
        } else {
            snapshotReporterName = reporterName.trim();
        }

        IncidentChannel incidentChannel;
        try {
            incidentChannel = IncidentChannel.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (incidentChannel == IncidentChannel.PORTAL) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        if (safeFiles.size() > 5) {
            throw new BusinessException(422, "TOO_MANY_FILES");
        }
        java.util.List<String> uploadedUrls = new ArrayList<>();
        try {
            for (MultipartFile file : safeFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (file.getSize() > 5L * 1024 * 1024) {
                    throw new BusinessException(422, "FILE_TOO_LARGE");
                }
                String extension = detectImageExtension(file);
                if (extension == null) {
                    throw new BusinessException(422, "FILE_TYPE_NOT_ALLOWED");
                }
                uploadedUrls.add(minioStorageService.uploadIncidentImage(file, extension));
            }

            Incident incident = new Incident();
            incident.setApartmentId(apartment.getId());
            incident.setReportedBy(hasReportedBy ? reportedBy.trim() : null);
            incident.setReporterName(snapshotReporterName);
            incident.setChannel(incidentChannel);
            incident.setTitle(title.trim());
            incident.setDescription(description == null || description.isBlank() ? null : description.trim());
            incident.setArea(area.trim());
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            incident.setAssigneeName(null);
            incident.setDeleted(false);

            Incident savedIncident;
            try {
                savedIncident = incidentRepository.saveAndFlush(incident);

                LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
                IncidentEvent newEvent = new IncidentEvent();
                newEvent.setIncidentId(savedIncident.getId());
                newEvent.setStatus(IncidentStatus.NEW);
                newEvent.setCreatedAt(now);

                IncidentEvent acknowledgedEvent = new IncidentEvent();
                acknowledgedEvent.setIncidentId(savedIncident.getId());
                acknowledgedEvent.setStatus(IncidentStatus.ACKNOWLEDGED);
                acknowledgedEvent.setCreatedAt(now);
                incidentEventRepository.saveAllAndFlush(List.of(newEvent, acknowledgedEvent));

                for (String url : uploadedUrls) {
                    IncidentAttachment attachment = new IncidentAttachment();
                    attachment.setIncidentId(savedIncident.getId());
                    attachment.setStorageKey(url);
                    incidentAttachmentRepository.saveAndFlush(attachment);
                }
            } catch (RuntimeException exception) {
                uploadedUrls.forEach(minioStorageService::delete);
                if (exception instanceof DataIntegrityViolationException) {
                    throw exception;
                }
                throw exception;
            }

            return getIncidentDetails(savedIncident.getId());
        } catch (BusinessException exception) {
            uploadedUrls.forEach(minioStorageService::delete);
            throw exception;
        }
    }

    @Override
    @Transactional
    public IncidentDetailResponse acknowledgeIncident(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() != IncidentStatus.NEW) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }

        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incidentRepository.saveAndFlush(incident);

        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.ACKNOWLEDGED);
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    @Override
    @Transactional
    public IncidentDetailResponse startIncident(String incidentId, IncidentStartRequest request) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() != IncidentStatus.ACKNOWLEDGED) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }
        if (request == null
                || request.assigneeName() == null
                || request.assigneeName().isBlank()) {
            throw new BusinessException(422, "ASSIGNEE_REQUIRED");
        }

        String assigneeName = request.assigneeName().trim();
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setAssigneeName(assigneeName);
        incidentRepository.saveAndFlush(incident);

        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.IN_PROGRESS);
        event.setAssigneeName(assigneeName);
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    @Override
    @Transactional
    public IncidentDetailResponse completeIncident(String incidentId, IncidentCompleteRequest request) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() != IncidentStatus.IN_PROGRESS) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String note = request == null || request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(now);
        incidentRepository.saveAndFlush(incident);

        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.CLOSED);
        event.setAssigneeName(incident.getAssigneeName());
        event.setNote(note);
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    @Override
    @Transactional
    public IncidentDetailResponse revertIncident(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() != IncidentStatus.IN_PROGRESS) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }

        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAssigneeName(null);
        incidentRepository.saveAndFlush(incident);

        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.ACKNOWLEDGED);
        event.setAssigneeName(null);
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    @Override
    @Transactional
    public IncidentDetailResponse cancelIncident(String incidentId, IncidentCancelRequest request) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() == IncidentStatus.CLOSED
                || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }
        if (request == null
                || request.cancelReason() == null
                || request.cancelReason().isBlank()) {
            throw new BusinessException(422, "CANCEL_NOTE_REQUIRED");
        }

        IncidentCancelReason reason;
        try {
            reason = IncidentCancelReason.valueOf(
                    request.cancelReason().trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (reason == IncidentCancelReason.OTHER
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(422, "CANCEL_NOTE_REQUIRED");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String note = request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
        incident.setStatus(IncidentStatus.CANCELLED);
        incident.setCancelReason(reason);
        incident.setCancelledAt(now);
        incidentRepository.saveAndFlush(incident);

        String actorName = SecurityUtils.getUsername();
        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.CANCELLED);
        event.setAssigneeName(incident.getAssigneeName());
        event.setNote(note);
        event.setCreatedBy(actorName);
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    private String detectImageExtension(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {
                return "jpg";
            }
            if (bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a) {
                return "png";
            }
            if (bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P') {
                return "webp";
            }
        } catch (Exception exception) {
            throw new BusinessException(422, "FILE_TYPE_NOT_ALLOWED");
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentDetailResponse getIncidentDetails(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));

        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(incident.getApartmentId())
                .orElse(null);
        Tower tower = apartment == null
                ? null
                : towerRepository.findByIdAndDeletedFalse(apartment.getTowerId()).orElse(null);

        IncidentDetailResponse.Reporter reporter = null;
        ResidencyRelation relation = null;
        if (incident.getReportedBy() != null && !incident.getReportedBy().isBlank()) {
            Resident resident = residentRepository.findByIdAndDeletedFalse(incident.getReportedBy())
                    .orElse(null);
            if (resident != null) {
                relation = residencyRepository.findByResidentIdAndEndDateIsNullAndDeletedFalse(resident.getId())
                        .map(Residency::getRelation)
                        .orElse(null);
                reporter = new IncidentDetailResponse.Reporter(
                        resident.getId(),
                        resident.getFullName(),
                        relation
                );
            }
        }

        java.util.List<IncidentDetailResponse.Attachment> attachments =
                incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(incident.getId())
                        .stream()
                        .map(this::toAttachment)
                        .toList();
        java.util.List<IncidentEvent> incidentEvents =
                incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(incident.getId());
        java.util.List<IncidentDetailResponse.TimelineItem> timeline =
                incidentEvents.stream()
                        .map(this::toTimelineItem)
                        .toList();

        String resultNote = incident.getStatus() == IncidentStatus.CLOSED
                ? timeline.stream()
                        .filter(item -> item.status() == IncidentStatus.CLOSED)
                        .map(IncidentDetailResponse.TimelineItem::note)
                        .filter(note -> note != null)
                        .reduce((first, second) -> second)
                        .orElse(null)
                : null;

        IncidentDetailResponse.Cancellation cancellation = null;
        if (incident.getStatus() == IncidentStatus.CANCELLED) {
            String cancelledByName = incident.getUpdatedBy();
            cancellation = new IncidentDetailResponse.Cancellation(
                    incident.getCancelReason(),
                    timeline.stream()
                            .filter(item -> item.status() == IncidentStatus.CANCELLED)
                            .map(IncidentDetailResponse.TimelineItem::note)
                            .filter(note -> note != null)
                            .findFirst()
                            .orElse(null),
                    cancellationActor(incidentEvents),
                    cancelledByName,
                    toOffsetDateTime(incident.getCancelledAt())
            );
        }

        return new IncidentDetailResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getArea(),
                tower == null
                        ? null
                        : new IncidentDetailResponse.Tower(tower.getId(), tower.getCode(), tower.getName()),
                apartment == null
                        ? null
                        : new IncidentDetailResponse.Apartment(apartment.getId(), apartment.getCode()),
                reporter,
                incident.getReporterName(),
                apartment == null ? null : apartment.getCode(),
                incident.getChannel(),
                incident.getAssigneeName(),
                incident.getDescription(),
                attachments,
                timeline,
                resultNote,
                cancellation,
                allowedActions(incident.getStatus()),
                toOffsetDateTime(incident.getCreatedAt())
        );
    }

    private String cancellationActor(List<IncidentEvent> incidentEvents) {
        return incidentEvents.stream()
                .filter(event -> event.getStatus() == IncidentStatus.CANCELLED)
                .map(IncidentEvent::getCreatedBy)
                .filter(username -> username != null && !username.isBlank())
                .reduce((first, second) -> second)
                .flatMap(userRepository::findUserByUsernameAndDeletedFalse)
                .map(user -> user.getRole())
                .filter(role -> "RESIDENT".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role))
                .map(role -> role.toUpperCase(java.util.Locale.ROOT))
                .orElse(null);
    }

    private IncidentDetailResponse.Attachment toAttachment(IncidentAttachment attachment) {
        String storageKey = attachment.getStorageKey();
        String url = storageKey;
        if (storageKey != null
                && !storageKey.startsWith("http://")
                && !storageKey.startsWith("https://")) {
            url = "https://minio.example.com/emerald/" + storageKey;
        }
        String fileName = storageKey == null
                ? null
                : storageKey.substring(storageKey.lastIndexOf('/') + 1);
        return new IncidentDetailResponse.Attachment(attachment.getId(), fileName, url);
    }

    private IncidentDetailResponse.TimelineItem toTimelineItem(IncidentEvent event) {
        return new IncidentDetailResponse.TimelineItem(
                event.getStatus(),
                toOffsetDateTime(event.getCreatedAt()),
                event.getCreatedBy(),
                event.getAssigneeName(),
                event.getNote()
        );
    }

    private java.util.List<String> allowedActions(IncidentStatus status) {
        if (status == IncidentStatus.NEW) {
            return java.util.List.of("ACKNOWLEDGE", "CANCEL");
        }
        if (status == IncidentStatus.ACKNOWLEDGED) {
            return java.util.List.of("START", "CHANGE_ASSIGNEE", "CANCEL");
        }
        if (status == IncidentStatus.IN_PROGRESS) {
            return java.util.List.of("COMPLETE", "REVERT", "CHANGE_ASSIGNEE", "CANCEL");
        }
        return java.util.List.of();
    }

    private IncidentListResponse.IncidentItem toIncidentItem(
            Incident incident,
            Apartment apartment,
            java.util.Map<String, Tower> towersById
    ) {
        Tower tower = apartment == null ? null : towersById.get(apartment.getTowerId());
        IncidentListResponse.Tower towerResponse = tower == null
                ? null
                : new IncidentListResponse.Tower(tower.getId(), tower.getCode(), tower.getName());
        return new IncidentListResponse.IncidentItem(
                incident.getId(),
                incident.getTitle(),
                incident.getArea(),
                towerResponse,
                incident.getReporterName(),
                apartment == null ? null : apartment.getCode(),
                incident.getChannel(),
                incident.getAssigneeName(),
                incident.getStatus(),
                incident.getCancelReason(),
                toOffsetDateTime(incident.getCreatedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
