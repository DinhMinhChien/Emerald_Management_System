package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.dto.response.ResidentHomeResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentProfileResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.HouseholdMemberCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.HouseholdMemberCreateResponse;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.example.emeraldmanagementsystem.entity.InvoiceItem;
import org.example.emeraldmanagementsystem.entity.IncidentAttachment;
import org.example.emeraldmanagementsystem.entity.IncidentEvent;
import org.example.emeraldmanagementsystem.config.MinioProperties;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.IncidentRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceItemRepository;
import org.example.emeraldmanagementsystem.repository.IncidentAttachmentRepository;
import org.example.emeraldmanagementsystem.repository.IncidentEventRepository;
import org.example.emeraldmanagementsystem.service.ResidentPortalService;
import org.example.emeraldmanagementsystem.utils.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.example.emeraldmanagementsystem.service.MinioStorageService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResidentPortalServiceImplement implements ResidentPortalService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserRepository userRepository;
    private final ResidentRepository residentRepository;
    private final ResidencyRepository residencyRepository;
    private final ApartmentRepository apartmentRepository;
    private final TowerRepository towerRepository;
    private final BillingPeriodRepository billingPeriodRepository;
    private final InvoiceRepository invoiceRepository;
    private final IncidentRepository incidentRepository;
    private final VehicleRepository vehicleRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final IncidentAttachmentRepository incidentAttachmentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final MinioProperties minioProperties;
    private final MinioStorageService minioStorageService;

    private record ApartmentContext(Apartment apartment, Residency residency, User user) {
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentHomeResponse getHome() {
        String username = SecurityUtils.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(403, "RESIDENT_ACCOUNT_NOT_FOUND");
        }
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(403, "RESIDENT_ACCOUNT_NOT_FOUND"));
        if (user.getResidentId() == null || user.getResidentId().isBlank()) {
            throw new BusinessException(403, "RESIDENT_ACCOUNT_HAS_NO_RESIDENCY");
        }

        Residency residency = residencyRepository
                .findByResidentIdAndEndDateIsNullAndDeletedFalse(user.getResidentId())
                .orElseThrow(() -> new BusinessException(403, "RESIDENT_ACCOUNT_HAS_NO_RESIDENCY"));
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(residency.getApartmentId())
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        Tower tower = towerRepository.findByIdAndDeletedFalse(apartment.getTowerId())
                .orElse(null);
        String residentName = residentRepository.findByIdAndDeletedFalse(user.getResidentId())
                .map(resident -> resident.getFullName())
                .orElse(user.getFullName());

        BillingPeriod latestPeriod = billingPeriodRepository
                .findFirstByDeletedFalseOrderByCreatedAtDesc()
                .orElse(null);
        ResidentHomeResponse.InvoiceSummaryItem currentInvoice = null;
        if (latestPeriod != null) {
            List<Invoice> currentInvoices = invoiceRepository.findCurrentApartmentInvoices(
                    apartment.getId(),
                    latestPeriod.getId(),
                    PageRequest.of(0, 1)
            );
            if (!currentInvoices.isEmpty()) {
                currentInvoice = toInvoiceItem(currentInvoices.getFirst(), latestPeriod);
            }
        }

        List<ResidentHomeResponse.OpenIncidentItem> openIncidents =
                incidentRepository.findOpenIncidentsByApartmentId(
                                apartment.getId(),
                                PageRequest.of(0, 5)
                        ).stream()
                        .map(incident -> new ResidentHomeResponse.OpenIncidentItem(
                                incident.getId(),
                                incident.getTitle(),
                                incident.getStatus()
                        ))
                        .toList();

        List<ResidentHomeResponse.InvoiceSummaryItem> recentInvoices =
                invoiceRepository.findRecentInvoicesByApartmentId(
                                apartment.getId(),
                                PageRequest.of(0, 3)
                        ).stream()
                        .map(invoice -> billingPeriodRepository.findByIdAndDeletedFalse(invoice.getPeriodId())
                                .map(period -> toInvoiceItem(invoice, period))
                                .orElse(null))
                        .filter(item -> item != null)
                        .toList();

        return new ResidentHomeResponse(
                new ResidentHomeResponse.Resident(residentName),
                new ResidentHomeResponse.Apartment(
                        apartment.getId(),
                        apartment.getCode(),
                        tower == null ? null : tower.getName()
                ),
                currentInvoice,
                openIncidents,
                recentInvoices
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentProfileResponse getProfile() {
        String username = SecurityUtils.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(403, "RESIDENT_ACCOUNT_NOT_FOUND");
        }
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(403, "RESIDENT_ACCOUNT_NOT_FOUND"));
        if (user.getResidentId() == null || user.getResidentId().isBlank()) {
            throw new BusinessException(403, "RESIDENT_ACCOUNT_HAS_NO_RESIDENCY");
        }

        org.example.emeraldmanagementsystem.entity.Resident resident =
                residentRepository.findByIdAndDeletedFalse(user.getResidentId())
                        .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));
        Residency residency = residencyRepository
                .findByResidentIdAndEndDateIsNullAndDeletedFalse(user.getResidentId())
                .orElseThrow(() -> new BusinessException(403, "RESIDENT_ACCOUNT_HAS_NO_RESIDENCY"));
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(residency.getApartmentId())
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        Tower tower = towerRepository.findByIdAndDeletedFalse(apartment.getTowerId())
                .orElse(null);

        List<ResidentProfileResponse.Housemate> housemates =
                residencyRepository.findResidencyDetailsByApartmentId(apartment.getId(), true).stream()
                        .filter(item -> item.getEndDate() == null)
                        .filter(item -> !item.getResidentId().equals(user.getResidentId()))
                        .map(item -> new ResidentProfileResponse.Housemate(
                                item.getResidentId(),
                                item.getFullName(),
                                item.getRelation(),
                                item.getPhone()
                        ))
                        .toList();

        List<ResidentProfileResponse.Vehicle> vehicles =
                vehicleRepository.findVehicleItemsByApartmentId(
                                apartment.getId(),
                                org.example.emeraldmanagementsystem.common.enums.VehicleStatus.ACTIVE
                        ).stream()
                        .map(vehicle -> new ResidentProfileResponse.Vehicle(
                                vehicle.getId(),
                                vehicle.getType(),
                                vehicle.getPlateNumber(),
                                vehicle.getCardNumber()
                        ))
                        .toList();

        return new ResidentProfileResponse(
                new ResidentProfileResponse.Personal(
                        resident.getFullName(),
                        resident.getBirthDate(),
                        resident.getPhone(),
                        resident.getEmail(),
                        maskNationalId(resident.getNationalId())
                ),
                new ResidentProfileResponse.Account(user.getUsername()),
                new ResidentProfileResponse.Apartment(
                        apartment.getId(),
                        apartment.getCode(),
                        tower == null ? null : tower.getName(),
                        apartment.getFloor(),
                        apartment.getAreaM2(),
                        apartment.getBedrooms(),
                        apartment.getBathrooms(),
                        residency.getStartDate()
                ),
                housemates,
                vehicles
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentInvoiceListResponse getInvoices(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        ApartmentContext context = getAuthenticatedApartmentContext();
        List<Invoice> outstandingInvoices =
                invoiceRepository.findOutstandingInvoicesByApartmentId(context.apartment().getId());
        ResidentInvoiceListResponse.Outstanding outstanding = null;
        if (!outstandingInvoices.isEmpty()) {
            long totalAmount = outstandingInvoices.stream()
                    .mapToLong(invoice -> invoice.getTotalAmount() == null
                            ? 0
                            : invoice.getTotalAmount())
                    .sum();
            Invoice earliest = outstandingInvoices.stream()
                    .filter(invoice -> invoice.getDueDate() != null)
                    .min(java.util.Comparator.comparing(Invoice::getDueDate))
                    .orElse(outstandingInvoices.getFirst());
            outstanding = new ResidentInvoiceListResponse.Outstanding(
                    outstandingInvoices.size(),
                    totalAmount,
                    earliest.getDueDate(),
                    earliest.getId()
            );
        }

        Page<Invoice> invoicePage = invoiceRepository.findResidentInvoicesByApartmentId(
                context.apartment().getId(),
                PageRequest.of(page, size)
        );
        List<ResidentInvoiceListResponse.InvoiceItem> items = invoicePage.getContent().stream()
                .map(invoice -> toResidentInvoiceItem(invoice))
                .toList();
        return new ResidentInvoiceListResponse(
                outstanding,
                items,
                invoicePage.getTotalElements(),
                page,
                size
        );
    }

    private ApartmentContext getAuthenticatedApartmentContext() {
        String username = SecurityUtils.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(403, "NO_ACTIVE_RESIDENCY");
        }
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(403, "NO_ACTIVE_RESIDENCY"));
        if (user.getResidentId() == null || user.getResidentId().isBlank()) {
            throw new BusinessException(403, "NO_ACTIVE_RESIDENCY");
        }
        Residency residency = residencyRepository
                .findByResidentIdAndEndDateIsNullAndDeletedFalse(user.getResidentId())
                .orElseThrow(() -> new BusinessException(403, "NO_ACTIVE_RESIDENCY"));
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(residency.getApartmentId())
                .orElseThrow(() -> new BusinessException(403, "NO_ACTIVE_RESIDENCY"));
        return new ApartmentContext(apartment, residency, user);
    }

    private ResidentInvoiceListResponse.InvoiceItem toResidentInvoiceItem(Invoice invoice) {
        BillingPeriod period = billingPeriodRepository.findByIdAndDeletedFalse(invoice.getPeriodId())
                .orElse(null);
        String displayStatus = invoice.getStatus().name();
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())) {
            displayStatus = "OVERDUE";
        }
        return new ResidentInvoiceListResponse.InvoiceItem(
                invoice.getId(),
                period == null
                        ? null
                        : new ResidentInvoiceListResponse.Period(period.getYear(), period.getMonth()),
                invoice.getTotalAmount(),
                invoice.getDueDate(),
                displayStatus,
                invoice.getPaidOn()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentInvoiceDetailResponse getInvoiceDetails(String invoiceId) {
        ApartmentContext context = getAuthenticatedApartmentContext();
        Invoice invoice = invoiceRepository.findByIdAndDeletedFalse(
                        invoiceId == null ? "" : invoiceId.trim()
                )
                .filter(item -> item.getApartmentId().equals(context.apartment().getId()))
                .filter(item -> item.getStatus() != InvoiceStatus.VOID)
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));

        BillingPeriod period = billingPeriodRepository.findByIdAndDeletedFalse(invoice.getPeriodId())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        Tower tower = towerRepository.findByIdAndDeletedFalse(context.apartment().getTowerId())
                .orElse(null);

        String displayStatus = invoice.getStatus().name();
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())) {
            displayStatus = "OVERDUE";
        }

        List<ResidentInvoiceDetailResponse.InvoiceItem> items =
                invoiceItemRepository.findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(invoice.getId())
                        .stream()
                        .map(item -> new ResidentInvoiceDetailResponse.InvoiceItem(
                                item.getFeeType(),
                                item.getDescription(),
                                item.getQuantity(),
                                item.getUnitAmount(),
                                item.getAmount()
                        ))
                        .toList();
        ResidentInvoiceDetailResponse.Payment payment = invoice.getStatus() == InvoiceStatus.PAID
                ? new ResidentInvoiceDetailResponse.Payment(
                        invoice.getPaidOn(),
                        invoice.getPaidNote()
                )
                : null;

        return new ResidentInvoiceDetailResponse(
                invoice.getId(),
                new ResidentInvoiceDetailResponse.Period(period.getId(), period.getYear(), period.getMonth()),
                new ResidentInvoiceDetailResponse.Apartment(
                        context.apartment().getId(),
                        context.apartment().getCode(),
                        tower == null ? null : tower.getCode(),
                        tower == null ? null : tower.getName()
                ),
                invoice.getAreaM2(),
                invoice.getServiceTierName(),
                invoice.getStatus(),
                displayStatus,
                invoice.getDueDate(),
                toOffsetDateTime(invoice.getCreatedAt()),
                items,
                invoice.getTotalAmount(),
                payment
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentIncidentListResponse getIncidents(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        IncidentStatus incidentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                incidentStatus = IncidentStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(400, "VALIDATION_ERROR");
            }
        }

        ApartmentContext context = getAuthenticatedApartmentContext();
        Page<Incident> incidentPage = incidentRepository.findResidentIncidentsByApartmentId(
                context.apartment().getId(),
                incidentStatus,
                PageRequest.of(page, size)
        );
        List<ResidentIncidentListResponse.IncidentItem> items = incidentPage.getContent().stream()
                .map(incident -> new ResidentIncidentListResponse.IncidentItem(
                        incident.getId(),
                        incident.getTitle(),
                        incident.getStatus(),
                        incident.getAssigneeName(),
                        incident.getCancelReason(),
                        toOffsetDateTime(incident.getCreatedAt())
                ))
                .toList();
        return new ResidentIncidentListResponse(
                items,
                incidentPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentIncidentDetailResponse getIncidentDetails(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        ApartmentContext context = getAuthenticatedApartmentContext();
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .filter(item -> item.getApartmentId().equals(context.apartment().getId()))
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));

        List<ResidentIncidentDetailResponse.Attachment> attachments =
                incidentAttachmentRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(incident.getId())
                        .stream()
                        .map(this::toResidentAttachment)
                        .toList();
        List<IncidentEvent> incidentEvents =
                incidentEventRepository.findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(incident.getId());
        List<ResidentIncidentDetailResponse.TimelineItem> timeline =
                incidentEvents.stream()
                        .map(event -> new ResidentIncidentDetailResponse.TimelineItem(
                                event.getStatus(),
                                toOffsetDateTime(event.getCreatedAt()),
                                event.getAssigneeName()
                        ))
                        .toList();

        String resultNote = incident.getStatus() == IncidentStatus.CLOSED
                ? timelineNote(incidentEvents, IncidentStatus.CLOSED)
                : null;
        ResidentIncidentDetailResponse.Cancellation cancellation = null;
        if (incident.getStatus() == IncidentStatus.CANCELLED) {
            cancellation = new ResidentIncidentDetailResponse.Cancellation(
                    incident.getCancelReason(),
                    cancellationActor(incidentEvents)
            );
        }

        return new ResidentIncidentDetailResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getArea(),
                incident.getAssigneeName(),
                incident.getDescription(),
                attachments,
                timeline,
                resultNote,
                cancellation,
                incident.getStatus() == IncidentStatus.NEW,
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
                .map(User::getRole)
                .filter(role -> "RESIDENT".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .orElse(null);
    }

    @Override
    @Transactional
    public ResidentIncidentDetailResponse createIncident(
            String title,
            String area,
            String description,
            List<MultipartFile> files
    ) {
        if (title == null || title.isBlank()
                || area == null || area.isBlank()
                || description == null || description.isBlank()) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        ApartmentContext context = getAuthenticatedApartmentContext();
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        if (safeFiles.size() > 5) {
            throw new BusinessException(422, "TOO_MANY_FILES");
        }

        List<String> uploadedUrls = new java.util.ArrayList<>();
        try {
            for (MultipartFile file : safeFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (file.getSize() > 5L * 1024 * 1024) {
                    throw new BusinessException(422, "FILE_TOO_LARGE");
                }
                String extension = detectResidentImageExtension(file);
                if (extension == null) {
                    throw new BusinessException(422, "FILE_TYPE_NOT_ALLOWED");
                }
                uploadedUrls.add(minioStorageService.uploadIncidentImage(file, extension));
            }

            Resident resident = residentRepository.findByIdAndDeletedFalse(context.user().getResidentId())
                    .orElse(null);
            Incident incident = new Incident();
            incident.setApartmentId(context.apartment().getId());
            incident.setReportedBy(context.user().getResidentId());
            incident.setReporterName(resident == null ? context.user().getFullName() : resident.getFullName());
            incident.setChannel(IncidentChannel.PORTAL);
            incident.setTitle(title.trim());
            incident.setArea(area.trim());
            incident.setDescription(description.trim());
            incident.setStatus(IncidentStatus.NEW);
            incident.setDeleted(false);

            Incident savedIncident;
            try {
                savedIncident = incidentRepository.saveAndFlush(incident);
                IncidentEvent event = new IncidentEvent();
                event.setIncidentId(savedIncident.getId());
                event.setStatus(IncidentStatus.NEW);
                incidentEventRepository.saveAndFlush(event);
                for (String url : uploadedUrls) {
                    IncidentAttachment attachment = new IncidentAttachment();
                    attachment.setIncidentId(savedIncident.getId());
                    attachment.setStorageKey(url);
                    incidentAttachmentRepository.saveAndFlush(attachment);
                }
            } catch (RuntimeException exception) {
                uploadedUrls.forEach(minioStorageService::delete);
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
    public ResidentIncidentDetailResponse cancelIncident(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new BusinessException(404, "INCIDENT_NOT_FOUND");
        }
        ApartmentContext context = getAuthenticatedApartmentContext();
        Incident incident = incidentRepository.findById(incidentId.trim())
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .filter(item -> item.getApartmentId().equals(context.apartment().getId()))
                .orElseThrow(() -> new BusinessException(404, "INCIDENT_NOT_FOUND"));
        if (incident.getStatus() != IncidentStatus.NEW) {
            throw new BusinessException(409, "INVALID_STATUS_TRANSITION");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        incident.setStatus(IncidentStatus.CANCELLED);
        incident.setCancelReason(null);
        incident.setCancelledAt(now);
        incidentRepository.saveAndFlush(incident);

        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incident.getId());
        event.setStatus(IncidentStatus.CANCELLED);
        event.setCreatedBy(context.user().getUsername());
        incidentEventRepository.saveAndFlush(event);

        return getIncidentDetails(incident.getId());
    }

    private String detectResidentImageExtension(MultipartFile file) {
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

    private ResidentIncidentDetailResponse.Attachment toResidentAttachment(IncidentAttachment attachment) {
        String storageKey = attachment.getStorageKey();
        String url = storageKey;
        if (storageKey != null
                && !storageKey.startsWith("http://")
                && !storageKey.startsWith("https://")
                && minioProperties != null) {
            url = minioProperties.getUrl().replaceAll("/$", "")
                    + "/" + minioProperties.getBucketName()
                    + "/" + storageKey;
        }
        String fileName = storageKey == null
                ? null
                : storageKey.substring(storageKey.lastIndexOf('/') + 1);
        return new ResidentIncidentDetailResponse.Attachment(attachment.getId(), fileName, url);
    }

    private String timelineNote(List<IncidentEvent> events, IncidentStatus status) {
        return events.stream()
                .filter(event -> event.getStatus() == status)
                .map(IncidentEvent::getNote)
                .filter(note -> note != null)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    @Override
    @Transactional
    public HouseholdMemberCreateResponse createHouseholdMember(HouseholdMemberCreateRequest request) {
        if (request == null
                || request.fullName() == null
                || request.fullName().isBlank()
                || request.relation() == null) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String username = SecurityUtils.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(403, "NO_ACTIVE_RESIDENCY");
        }
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(403, "NO_ACTIVE_RESIDENCY"));
        if (user.getResidentId() == null || user.getResidentId().isBlank()) {
            throw new BusinessException(403, "NO_ACTIVE_RESIDENCY");
        }
        Residency headResidency = residencyRepository
                .findByResidentIdAndEndDateIsNullAndDeletedFalse(user.getResidentId())
                .orElseThrow(() -> new BusinessException(403, "NO_ACTIVE_RESIDENCY"));
        if (!apartmentRepository.findByIdAndDeletedFalse(headResidency.getApartmentId()).isPresent()) {
            throw new BusinessException(403, "NO_ACTIVE_RESIDENCY");
        }

        String nationalId = request.nationalId() == null
                ? null
                : request.nationalId().trim();
        if (nationalId != null && !nationalId.isBlank()) {
            if (residencyRepository.countActiveOtherApartmentByNationalId(
                    nationalId,
                    headResidency.getApartmentId()
            ) > 0) {
                throw new BusinessException(409, "RESIDENT_ALREADY_RESIDING");
            }
        }

        Resident resident = new Resident();
        resident.setFullName(request.fullName().trim());
        resident.setBirthDate(request.birthDate());
        resident.setPhone(request.phone() == null || request.phone().isBlank()
                ? null
                : request.phone().trim());
        resident.setNationalId(nationalId == null || nationalId.isBlank() ? null : nationalId);
        Resident savedResident = residentRepository.saveAndFlush(resident);

        LocalDate today = LocalDate.now();
        Residency residency = new Residency();
        residency.setApartmentId(headResidency.getApartmentId());
        residency.setResidentId(savedResident.getId());
        residency.setRole(ResidencyRole.MEMBER);
        residency.setRelation(request.relation());
        residency.setStartDate(today);
        residency.setEndDate(null);
        residency.setDeleted(false);
        Residency savedResidency = residencyRepository.saveAndFlush(residency);

        return new HouseholdMemberCreateResponse(
                savedResident.getId(),
                savedResidency.getId(),
                savedResident.getFullName(),
                request.relation(),
                today
        );
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return null;
        }
        String trimmed = nationalId.trim();
        if (trimmed.length() <= 6) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, 3)
                + "*".repeat(Math.max(1, trimmed.length() - 6))
                + trimmed.substring(trimmed.length() - 3);
    }

    private ResidentHomeResponse.InvoiceSummaryItem toInvoiceItem(
            Invoice invoice,
            BillingPeriod period
    ) {
        String displayStatus = invoice.getStatus().name();
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())) {
            displayStatus = "OVERDUE";
        }
        return new ResidentHomeResponse.InvoiceSummaryItem(
                invoice.getId(),
                new ResidentHomeResponse.Period(period.getYear(), period.getMonth()),
                invoice.getTotalAmount(),
                invoice.getDueDate(),
                displayStatus
        );
    }
}
