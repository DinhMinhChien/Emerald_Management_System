package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.dto.response.MeResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.service.MeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeServiceImplement implements MeService {
    private final UserRepository userRepository;
    private final ResidencyRepository residencyRepository;
    private final ApartmentRepository apartmentRepository;

    @Override
    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(String username) {
        User user = userRepository.findUserByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy tài khoản người dùng"));

        String apartmentCode = null;
        if ("RESIDENT".equals(user.getRole())) {
            apartmentCode = getApartmentCode(user.getResidentId());
        }

        return new MeResponse(
                user.getRole(),
                user.getFullName(),
                user.isMustChangePassword(),
                apartmentCode
        );
    }

    private String getApartmentCode(String residentId) {
        if (residentId == null || residentId.isBlank()) {
            throw new BusinessException(403, "Tài khoản không có cư trú đang hoạt động");
        }

        Residency residency = residencyRepository
                .findByResidentIdAndEndDateIsNullAndDeletedFalse(residentId)
                .orElseThrow(() -> new BusinessException(403, "Tài khoản không có cư trú đang hoạt động"));

        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(residency.getApartmentId())
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy căn hộ cư trú"));
        return apartment.getCode();
    }
}
