package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.common.enums.VehicleMoveOutAction;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.dto.request.ResidentHeadCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentPasswordResetRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidencyMoveOutRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentUpdateRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentAccountLockResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentHeadCreateResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentPasswordResetResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidencyMoveOutResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.entity.Vehicle;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.example.emeraldmanagementsystem.service.ResidentService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResidentServiceImplement implements ResidentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final String PASSWORD_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String PASSWORD_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PASSWORD_DIGITS = "23456789";
    private static final String PASSWORD_SPECIAL = "!@#$%&*?";

    private final ResidentRepository residentRepository;
    private final ResidencyRepository residencyRepository;
    private final UserRepository userRepository;
    private final ApartmentRepository apartmentRepository;
    private final VehicleRepository vehicleRepository;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Override
    @Transactional(readOnly = true)
    public ResidentListResponse getResidents(
            String query,
            String towerId,
            boolean residing,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedQuery = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        Page<ResidentRepository.ResidentListProjection> residentPage = residentRepository.searchResidents(
                normalizedQuery,
                towerId,
                residing,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id")))
        );
        List<ResidentRepository.ResidentListProjection> residents = residentPage.getContent();
        List<String> residentIds = residents.stream()
                .map(ResidentRepository.ResidentListProjection::getId)
                .toList();
        Map<String, String> accountStatuses = residentIds.isEmpty()
                ? Map.of()
                : userRepository.findResidentAccountStatuses(residentIds).stream()
                        .filter(account -> account.getStatus() != null)
                        .collect(Collectors.toMap(
                                UserRepository.ResidentAccountStatusProjection::getResidentId,
                                UserRepository.ResidentAccountStatusProjection::getStatus,
                                (existing, ignored) -> existing
                        ));

        List<ResidentListResponse.ResidentItem> items = residents.stream()
                .map(resident -> new ResidentListResponse.ResidentItem(
                        resident.getId(),
                        resident.getFullName(),
                        resident.getPhone(),
                        resident.getApartmentId() == null
                                ? null
                                : new ResidentListResponse.Apartment(
                                        resident.getApartmentId(),
                                        resident.getApartmentCode(),
                                        resident.getTowerCode(),
                                        resident.getTowerName()
                                ),
                        resident.getRole(),
                        resident.getRole() == ResidencyRole.HEAD ? null : resident.getRelation(),
                        resident.getRole() == ResidencyRole.MEMBER
                                ? "NONE"
                                : accountStatuses.getOrDefault(resident.getId(), "NONE")
                ))
                .toList();
        return new ResidentListResponse(items, residentPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ResidentDetailResponse getResidentDetails(String residentId) {
        Resident resident = residentRepository.findByIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));
        ResidencyRepository.ResidentCurrentResidencyProjection current =
                residencyRepository.findCurrentResidencyDetailsByResidentId(residentId).orElse(null);

        ResidentDetailResponse.Account account = null;
        if (current == null || current.getRole() != ResidencyRole.MEMBER) {
            account = userRepository.findByResidentIdAndDeletedFalse(residentId)
                    .map(this::toAccount)
                    .orElse(null);
        }

        ResidentDetailResponse.CurrentResidency currentResidency = null;
        if (current != null) {
            List<ResidentDetailResponse.Housemate> housemates =
                    residencyRepository.findCurrentHousemates(current.getApartmentId(), residentId).stream()
                            .map(housemate -> new ResidentDetailResponse.Housemate(
                                    housemate.getResidentId(),
                                    housemate.getFullName(),
                                    housemate.getRelation()
                            ))
                            .toList();
            currentResidency = new ResidentDetailResponse.CurrentResidency(
                    current.getResidencyId(),
                    toApartment(
                            current.getApartmentId(),
                            current.getApartmentCode(),
                            current.getTowerCode(),
                            current.getTowerName()
                    ),
                    current.getRole(),
                    current.getRole() == ResidencyRole.HEAD ? null : current.getRelation(),
                    current.getStartDate(),
                    housemates
            );
        }

        List<ResidentDetailResponse.ResidencyHistoryItem> residencyHistory =
                residencyRepository.findResidencyHistoryByResidentId(residentId).stream()
                        .map(history -> new ResidentDetailResponse.ResidencyHistoryItem(
                                history.getResidencyId(),
                                history.getApartmentId() == null
                                        ? null
                                        : toApartment(
                                                history.getApartmentId(),
                                                history.getApartmentCode(),
                                                history.getTowerCode(),
                                                history.getTowerName()
                                        ),
                                history.getRole(),
                                history.getRole() == ResidencyRole.HEAD ? null : history.getRelation(),
                                history.getStartDate(),
                                history.getEndDate(),
                                history.getEndReason()
                        ))
                        .toList();

        return new ResidentDetailResponse(
                resident.getId(),
                resident.getFullName(),
                resident.getBirthDate(),
                resident.getGender(),
                resident.getPhone(),
                resident.getEmail(),
                maskNationalId(resident.getNationalId()),
                account,
                currentResidency,
                residencyHistory
        );
    }

    @Override
    @Transactional
    public ResidentDetailResponse updateResident(String residentId, ResidentUpdateRequest request) {
        Resident resident = residentRepository.findByIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));
        resident.setFullName(request.fullName());
        resident.setBirthDate(request.birthDate());
        resident.setGender(request.gender());
        resident.setPhone(request.phone());
        resident.setEmail(request.email());
        if (request.nationalId() != null && !request.nationalId().isBlank()) {
            resident.setNationalId(request.nationalId());
        }
        residentRepository.saveAndFlush(resident);
        return getResidentDetails(residentId);
    }

    @Override
    @Transactional
    public ResidentPasswordResetResponse resetResidentPassword(
            String residentId,
            ResidentPasswordResetRequest request
    ) {
        residentRepository.findByIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));
        User user = userRepository.findByResidentIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(422, "NO_ACCOUNT"));
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(409, "ACCOUNT_LOCKED");
        }

        String temporaryPassword = request == null || request.temporaryPassword() == null
                ? generateTemporaryPassword()
                : request.temporaryPassword();
        resetKeycloakPassword(user.getUsername(), temporaryPassword);
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);

        return new ResidentPasswordResetResponse(user.getId(), temporaryPassword, true);
    }

    @Override
    @Transactional
    public ResidentAccountLockResponse lockResidentAccount(String residentId) {
        residentRepository.findByIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));
        User user = userRepository.findByResidentIdAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(422, "NO_ACCOUNT"));
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(409, "ACCOUNT_ALREADY_LOCKED");
        }

        lockKeycloakUser(user.getUsername());
        user.setStatus("LOCKED");
        userRepository.saveAndFlush(user);
        return new ResidentAccountLockResponse(user.getId(), "LOCKED");
    }

    @Override
    @Transactional
    public ResidencyMoveOutResponse moveOutResidency(String residencyId, ResidencyMoveOutRequest request) {
        Residency residency = residencyRepository.findByIdAndDeletedFalse(residencyId)
                .orElseThrow(() -> new BusinessException(404, "RESIDENCY_NOT_FOUND"));
        if (residency.getEndDate() != null) {
            throw new BusinessException(409, "RESIDENCY_ALREADY_ENDED");
        }
        if (request == null || request.endDate() == null || request.endReason() == null) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (request.endDate().isBefore(residency.getStartDate())) {
            throw new BusinessException(422, "INVALID_END_DATE");
        }

        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(residency.getApartmentId())
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        Resident resident = residentRepository.findByIdAndDeletedFalse(residency.getResidentId())
                .orElseThrow(() -> new BusinessException(404, "RESIDENT_NOT_FOUND"));

        List<Residency> remainingResidencies = residencyRepository
                .findByApartmentIdAndEndDateIsNullAndDeletedFalse(apartment.getId()).stream()
                .filter(active -> !active.getId().equals(residency.getId()))
                .toList();
        boolean isHead = residency.getRole() == ResidencyRole.HEAD;

        ResidencyMoveOutRequest.NewHead requestedNewHead = request.newHead();
        Residency newHeadResidency = null;
        Resident newHeadResident = null;
        User oldHeadAccount = null;

        if (isHead && !remainingResidencies.isEmpty()) {
            if (requestedNewHead == null) {
                throw new BusinessException(422, "NEW_HEAD_REQUIRED");
            }
            if (isBlank(requestedNewHead.username()) || isBlank(requestedNewHead.temporaryPassword())) {
                throw new BusinessException(422, "NEW_HEAD_ACCOUNT_REQUIRED");
            }
            newHeadResidency = remainingResidencies.stream()
                    .filter(active -> active.getRole() == ResidencyRole.MEMBER)
                    .filter(active -> active.getResidentId().equals(requestedNewHead.residentId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(422, "NEW_HEAD_INVALID"));
            if (userRepository.existsByUsernameAndDeletedFalse(requestedNewHead.username())) {
                throw new BusinessException(409, "USERNAME_DUPLICATED");
            }
            if (userRepository.findByResidentIdAndDeletedFalse(newHeadResidency.getResidentId()).isPresent()) {
                throw new BusinessException(422, "NEW_HEAD_INVALID");
            }
            newHeadResident = residentRepository.findByIdAndDeletedFalse(newHeadResidency.getResidentId())
                    .orElseThrow(() -> new BusinessException(422, "NEW_HEAD_INVALID"));
        } else if (requestedNewHead != null) {
            throw new BusinessException(422, "NEW_HEAD_INVALID");
        }

        if (isHead) {
            oldHeadAccount = userRepository.findByResidentIdAndDeletedFalse(residency.getResidentId())
                    .orElseThrow(() -> new BusinessException(422, "NO_ACCOUNT"));
        }

        List<Vehicle> vehicles = vehicleRepository.findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
                apartment.getId(),
                residency.getResidentId(),
                VehicleStatus.ACTIVE
        );
        List<ResidencyMoveOutRequest.VehicleAction> vehicleActions =
                request.vehicleActions() == null ? List.of() : request.vehicleActions();
        Map<String, ResidencyMoveOutRequest.VehicleAction> actionsByVehicleId = new LinkedHashMap<>();
        for (ResidencyMoveOutRequest.VehicleAction action : vehicleActions) {
            if (action == null || isBlank(action.vehicleId()) || action.action() == null
                    || actionsByVehicleId.putIfAbsent(action.vehicleId(), action) != null) {
                throw new BusinessException(422, "VEHICLE_ACTION_INVALID");
            }
        }
        Set<String> activeVehicleIds = vehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());
        List<String> missingVehicleIds = activeVehicleIds.stream()
                .filter(vehicleId -> !actionsByVehicleId.containsKey(vehicleId))
                .sorted()
                .toList();
        if (!missingVehicleIds.isEmpty()) {
            throw new BusinessException(
                    422,
                    "VEHICLE_ACTION_REQUIRED\n" + String.join("\n", missingVehicleIds)
            );
        }
        if (actionsByVehicleId.keySet().stream().anyMatch(vehicleId -> !activeVehicleIds.contains(vehicleId))) {
            throw new BusinessException(422, "VEHICLE_ACTION_INVALID");
        }

        Set<String> remainingResidentIds = remainingResidencies.stream()
                .map(Residency::getResidentId)
                .collect(Collectors.toSet());
        for (ResidencyMoveOutRequest.VehicleAction action : vehicleActions) {
            if (action.action() == VehicleMoveOutAction.TRANSFER) {
                if (isBlank(action.transferToResidentId())
                        || !remainingResidentIds.contains(action.transferToResidentId())
                        || residentRepository.findByIdAndDeletedFalse(action.transferToResidentId()).isEmpty()) {
                    throw new BusinessException(422, "TRANSFER_TARGET_INVALID");
                }
            } else if (!isBlank(action.transferToResidentId())) {
                throw new BusinessException(422, "VEHICLE_ACTION_INVALID");
            }
        }

        residency.setEndDate(request.endDate());
        residency.setEndReason(request.endReason());
        residencyRepository.saveAndFlush(residency);

        if (newHeadResidency != null) {
            newHeadResidency.setRole(ResidencyRole.HEAD);
            newHeadResidency.setRelation(null);
            residencyRepository.saveAndFlush(newHeadResidency);
        }

        Map<String, Vehicle> vehiclesById = vehicles.stream()
                .collect(Collectors.toMap(Vehicle::getId, vehicle -> vehicle));
        for (ResidencyMoveOutRequest.VehicleAction action : vehicleActions) {
            Vehicle vehicle = vehiclesById.get(action.vehicleId());
            if (action.action() == VehicleMoveOutAction.TRANSFER) {
                vehicle.setUserResidentId(action.transferToResidentId());
            } else {
                vehicle.setStatus(VehicleStatus.REVOKED);
                vehicle.setRevokedAt(request.endDate());
            }
        }
        if (!vehicles.isEmpty()) {
            vehicleRepository.saveAllAndFlush(vehicles);
        }

        apartment.setStatus(
                remainingResidencies.isEmpty() ? ApartmentStatus.VACANT : ApartmentStatus.OCCUPIED
        );
        apartmentRepository.saveAndFlush(apartment);

        User newHeadAccount = null;
        if (newHeadResidency != null) {
            newHeadAccount = new User();
            newHeadAccount.setRole("RESIDENT");
            newHeadAccount.setUsername(requestedNewHead.username());
            newHeadAccount.setFullName(newHeadResident.getFullName());
            newHeadAccount.setMustChangePassword(true);
            newHeadAccount.setStatus("ACTIVE");
            newHeadAccount.setResidentId(newHeadResident.getId());
            try {
                userRepository.saveAndFlush(newHeadAccount);
            } catch (DataIntegrityViolationException exception) {
                if (isDuplicateKeyViolation(exception)) {
                    throw new BusinessException(409, "USERNAME_DUPLICATED");
                }
                throw exception;
            }
        }

        if (oldHeadAccount != null) {
            oldHeadAccount.setStatus("LOCKED");
            userRepository.saveAndFlush(oldHeadAccount);
        }

        String newKeycloakUserId = null;
        if (newHeadAccount != null) {
            newKeycloakUserId = createKeycloakUser(
                    requestedNewHead.username(),
                    newHeadResident.getFullName(),
                    newHeadResident.getEmail(),
                    requestedNewHead.temporaryPassword()
            );
        }
        if (oldHeadAccount != null) {
            try {
                lockKeycloakUser(oldHeadAccount.getUsername());
            } catch (BusinessException exception) {
                if (newKeycloakUserId != null) {
                    deleteKeycloakUser(keycloak.realm(realm).users(), newKeycloakUserId);
                }
                throw exception;
            }
        }

        ResidencyMoveOutResponse.NewHead newHeadResponse = newHeadResidency == null
                ? null
                : new ResidencyMoveOutResponse.NewHead(
                        newHeadResidency.getResidentId(),
                        newHeadResidency.getId(),
                        true,
                        newHeadAccount.getUsername()
                );
        List<ResidencyMoveOutResponse.VehicleResult> vehicleResults = new ArrayList<>();
        for (ResidencyMoveOutRequest.VehicleAction action : vehicleActions) {
            vehicleResults.add(new ResidencyMoveOutResponse.VehicleResult(
                    action.vehicleId(),
                    action.action(),
                    action.action() == VehicleMoveOutAction.TRANSFER ? action.transferToResidentId() : null
            ));
        }

        return new ResidencyMoveOutResponse(
                residency.getId(),
                new ResidencyMoveOutResponse.Resident(resident.getId(), resident.getFullName()),
                request.endDate(),
                request.endReason(),
                new ResidencyMoveOutResponse.Apartment(apartment.getId(), apartment.getCode(), apartment.getStatus()),
                oldHeadAccount != null,
                newHeadResponse,
                vehicleResults
        );
    }

    @Override
    @Transactional
    public ResidentHeadCreateResponse createResidentHead(ResidentHeadCreateRequest request) {
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(request.apartmentId())
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        if (apartment.getStatus() == ApartmentStatus.REPAIRING) {
            throw new BusinessException(422, "APARTMENT_REPAIRING");
        }
        if (residencyRepository.existsByApartmentIdAndRoleAndEndDateIsNullAndDeletedFalse(
                apartment.getId(),
                ResidencyRole.HEAD
        )) {
            throw new BusinessException(409, "APARTMENT_ALREADY_HAS_HEAD");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(request.username())) {
            throw new BusinessException(409, "USERNAME_DUPLICATED");
        }
        if (request.nationalId() != null && !request.nationalId().isBlank()) {
            residentRepository.findByNationalIdAndDeletedFalse(request.nationalId())
                    .filter(existingResident -> residencyRepository
                            .findByResidentIdAndEndDateIsNullAndDeletedFalse(existingResident.getId())
                            .isPresent())
                    .ifPresent(existingResident -> {
                        throw new BusinessException(409, "RESIDENT_ALREADY_RESIDING");
                    });
        }

        Resident resident = new Resident();
        resident.setFullName(request.fullName());
        resident.setBirthDate(request.birthDate());
        resident.setGender(request.gender());
        resident.setPhone(request.phone());
        resident.setEmail(request.email());
        resident.setNationalId(request.nationalId());
        residentRepository.saveAndFlush(resident);

        Residency residency = new Residency();
        residency.setApartmentId(apartment.getId());
        residency.setResidentId(resident.getId());
        residency.setRole(ResidencyRole.HEAD);
        residency.setRelation(null);
        residency.setStartDate(request.startDate());
        residencyRepository.saveAndFlush(residency);

        User user = new User();
        user.setRole("RESIDENT");
        user.setUsername(request.username());
        user.setFullName(request.fullName());
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setResidentId(resident.getId());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateKeyViolation(exception)) {
                throw new BusinessException(409, "USERNAME_DUPLICATED");
            }
            throw exception;
        }

        apartment.setStatus(ApartmentStatus.OCCUPIED);
        apartmentRepository.saveAndFlush(apartment);

        createKeycloakUser(request);
        return new ResidentHeadCreateResponse(
                resident.getId(),
                residency.getId(),
                user.getId(),
                user.getUsername(),
                apartment.getId(),
                apartment.getStatus()
        );
    }

    private String createKeycloakUser(ResidentHeadCreateRequest request) {
        return createKeycloakUser(
                request.username(),
                request.fullName(),
                request.email(),
                request.temporaryPassword()
        );
    }

    private String createKeycloakUser(
            String username,
            String fullName,
            String email,
            String temporaryPassword
    ) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(username);
        userRepresentation.setEnabled(true);
        userRepresentation.setFirstName(fullName);
        userRepresentation.setEmail(email);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(temporaryPassword);
        credential.setTemporary(true);
        userRepresentation.setCredentials(List.of(credential));

        UsersResource users = keycloak.realm(realm).users();
        String keycloakUserId;
        try (Response response = users.create(userRepresentation)) {
            if (response.getStatus() == 400) {
                throw new BusinessException(422, "WEAK_PASSWORD");
            }
            if (response.getStatus() == 409) {
                throw new BusinessException(409, "USERNAME_DUPLICATED");
            }
            if (response.getStatus() != 201) {
                throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
            }
            keycloakUserId = getKeycloakUserId(response.getLocation());
            if (keycloakUserId == null) {
                cleanupKeycloakUserByUsername(users, username);
                throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
            }
        } catch (WebApplicationException exception) {
            int status = exception.getResponse() == null ? 0 : exception.getResponse().getStatus();
            if (status == 400) {
                throw new BusinessException(422, "WEAK_PASSWORD");
            }
            if (status == 409) {
                throw new BusinessException(409, "USERNAME_DUPLICATED");
            }
            log.error("Identity provider error while creating resident account", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        } catch (ProcessingException exception) {
            log.error("Identity provider error while creating resident account", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }

        try {
            RoleRepresentation residentRole = keycloak.realm(realm)
                    .roles()
                    .get("RESIDENT")
                    .toRepresentation();
            users.get(keycloakUserId).roles().realmLevel().add(List.of(residentRole));
        } catch (WebApplicationException | ProcessingException exception) {
            deleteKeycloakUser(users, keycloakUserId);
            log.error("Identity provider error while assigning resident role", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
        return keycloakUserId;
    }

    private void resetKeycloakPassword(String username, String temporaryPassword) {
        UsersResource users = keycloak.realm(realm).users();
        UserRepresentation keycloakUser = findKeycloakUser(users, username);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(temporaryPassword);
        credential.setTemporary(true);
        try {
            users.get(keycloakUser.getId()).resetPassword(credential);
        } catch (WebApplicationException exception) {
            int status = exception.getResponse() == null ? 0 : exception.getResponse().getStatus();
            if (status == 400) {
                throw new BusinessException(422, "WEAK_PASSWORD");
            }
            log.error("Identity provider error while resetting resident password", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        } catch (ProcessingException exception) {
            log.error("Identity provider error while resetting resident password", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
    }

    private void lockKeycloakUser(String username) {
        UsersResource users = keycloak.realm(realm).users();
        UserRepresentation keycloakUser = findKeycloakUser(users, username);
        try {
            UserRepresentation userRepresentation = users.get(keycloakUser.getId()).toRepresentation();
            if (userRepresentation == null) {
                throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
            }
            userRepresentation.setEnabled(false);
            users.get(keycloakUser.getId()).update(userRepresentation);
        } catch (WebApplicationException | ProcessingException exception) {
            log.error("Identity provider error while locking resident account", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
    }

    private UserRepresentation findKeycloakUser(UsersResource users, String username) {
        UserRepresentation keycloakUser;
        try {
            keycloakUser = users.search(username, true).stream()
                    .filter(candidate -> username.equals(candidate.getUsername()))
                    .findFirst()
                    .orElse(null);
        } catch (WebApplicationException | ProcessingException exception) {
            log.error("Identity provider error while finding resident account", exception);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
        if (keycloakUser == null || keycloakUser.getId() == null || keycloakUser.getId().isBlank()) {
            log.error("Identity provider account not found for resident username {}", username);
            throw new BusinessException(502, "IDENTITY_PROVIDER_ERROR");
        }
        return keycloakUser;
    }

    private String generateTemporaryPassword() {
        char[] password = new char[20];
        password[0] = randomCharacter(PASSWORD_LOWERCASE);
        password[1] = randomCharacter(PASSWORD_UPPERCASE);
        password[2] = randomCharacter(PASSWORD_DIGITS);
        password[3] = randomCharacter(PASSWORD_SPECIAL);
        String characters = PASSWORD_LOWERCASE + PASSWORD_UPPERCASE + PASSWORD_DIGITS + PASSWORD_SPECIAL;
        for (int i = 4; i < password.length; i++) {
            password[i] = randomCharacter(characters);
        }
        for (int i = password.length - 1; i > 0; i--) {
            int swapIndex = PASSWORD_RANDOM.nextInt(i + 1);
            char current = password[i];
            password[i] = password[swapIndex];
            password[swapIndex] = current;
        }
        return new String(password);
    }

    private char randomCharacter(String characters) {
        return characters.charAt(PASSWORD_RANDOM.nextInt(characters.length()));
    }

    private String getKeycloakUserId(URI location) {
        if (location == null || location.getPath() == null) {
            return null;
        }
        String path = location.getPath();
        String userId = path.substring(path.lastIndexOf('/') + 1);
        return userId.isBlank() ? null : userId;
    }

    private void cleanupKeycloakUserByUsername(UsersResource users, String username) {
        try {
            users.search(username, true).stream()
                    .filter(user -> username.equals(user.getUsername()))
                    .map(UserRepresentation::getId)
                    .filter(userId -> userId != null && !userId.isBlank())
                    .findFirst()
                    .ifPresent(userId -> deleteKeycloakUser(users, userId));
        } catch (WebApplicationException | ProcessingException exception) {
            log.error("Unable to find the newly created identity provider user for cleanup", exception);
        }
    }

    private void deleteKeycloakUser(UsersResource users, String keycloakUserId) {
        try (Response ignored = users.delete(keycloakUserId)) {
        } catch (WebApplicationException | ProcessingException exception) {
            log.error("Unable to remove the newly created identity provider user after role assignment failed", exception);
        }
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResidentDetailResponse.Account toAccount(User user) {
        return new ResidentDetailResponse.Account(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.isMustChangePassword(),
                toOffsetDateTime(user.getCreatedAt()),
                toOffsetDateTime(user.getLastLoginAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private ResidentDetailResponse.Apartment toApartment(
            String apartmentId,
            String apartmentCode,
            String towerCode,
            String towerName
    ) {
        return new ResidentDetailResponse.Apartment(apartmentId, apartmentCode, towerCode, towerName);
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return null;
        }
        if (nationalId.length() <= 6) {
            return "*".repeat(nationalId.length());
        }
        return nationalId.substring(0, 3)
                + "*".repeat(nationalId.length() - 6)
                + nationalId.substring(nationalId.length() - 3);
    }
}
