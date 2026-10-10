package com.shashwat.examplatformbackend.platform;

import com.shashwat.examplatformbackend.notification.NotificationService;
import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestAction;
import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestCreate;
import com.shashwat.examplatformbackend.platform.PlatformDtos.InstituteServiceRequestResponse;
import com.shashwat.examplatformbackend.platform.PlatformDtos.PublicRequestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class PlatformService {
    private final InstituteServiceRequestRepository repository;
    private final NotificationService notificationService;

    public PlatformService(InstituteServiceRequestRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    @Transactional
    public PublicRequestResponse createInstituteRequest(InstituteServiceRequestCreate request) {
        InstituteServiceRequest saved = repository.save(new InstituteServiceRequest(
                clean(request.instituteName()),
                clean(request.contactName()),
                normalizeEmail(request.officialEmail()),
                normalizePhone(request.phoneNumber()),
                clean(request.designation()),
                clean(request.instituteAddress()),
                clean(request.requestedServices()),
                clean(request.notes())
        ));
        notificationService.queuePlatformEmail(
                saved.getOfficialEmail(),
                "Service request received: " + saved.getInstituteName(),
                "Your request for examination services has been received.\n\n"
                        + "Request ID: " + saved.getId() + "\n"
                        + "Institute: " + saved.getInstituteName() + "\n"
                        + "Requested services: " + saved.getRequestedServices() + "\n\n"
                        + "Our platform team will review the request and contact the institute representative."
        );
        return new PublicRequestResponse(saved.getId(), saved.getStatus().name(), "Request submitted for platform review.");
    }

    @Transactional(readOnly = true)
    public List<InstituteServiceRequestResponse> list(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return repository.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
        }
        InstituteServiceRequestStatus parsed = InstituteServiceRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return repository.findAllByStatusOrderByCreatedAtDesc(parsed).stream().map(this::response).toList();
    }

    @Transactional
    public InstituteServiceRequestResponse markUnderReview(Long id, InstituteServiceRequestAction action) {
        return update(id, InstituteServiceRequestStatus.UNDER_REVIEW, action, false);
    }

    @Transactional
    public InstituteServiceRequestResponse approve(Long id, InstituteServiceRequestAction action) {
        return update(id, InstituteServiceRequestStatus.APPROVED, action, true);
    }

    @Transactional
    public InstituteServiceRequestResponse reject(Long id, InstituteServiceRequestAction action) {
        return update(id, InstituteServiceRequestStatus.REJECTED, action, true);
    }

    private InstituteServiceRequestResponse update(Long id, InstituteServiceRequestStatus status, InstituteServiceRequestAction action, boolean notify) {
        InstituteServiceRequest request = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Institute request was not found."));
        request.updateStatus(status, action == null ? "" : action.platformNotes());
        InstituteServiceRequest saved = repository.save(request);
        if (notify) {
            notificationService.queuePlatformEmail(
                    saved.getOfficialEmail(),
                    "Service request " + status.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    "Service request update\n\n"
                            + "Request ID: " + saved.getId() + "\n"
                            + "Institute: " + saved.getInstituteName() + "\n"
                            + "Status: " + saved.getStatus().name() + "\n\n"
                            + (saved.getPlatformNotes() == null ? "" : saved.getPlatformNotes())
            );
        }
        return response(saved);
    }

    private InstituteServiceRequestResponse response(InstituteServiceRequest request) {
        return new InstituteServiceRequestResponse(
                request.getId(),
                request.getInstituteName(),
                request.getContactName(),
                request.getOfficialEmail(),
                request.getPhoneNumber(),
                request.getDesignation(),
                request.getInstituteAddress(),
                request.getRequestedServices(),
                request.getNotes(),
                request.getStatus().name(),
                request.getPlatformNotes(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private static String normalizeEmail(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String value) {
        return clean(value).replaceAll("[^0-9+]", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
