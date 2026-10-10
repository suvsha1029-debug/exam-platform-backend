package com.shashwat.examplatformbackend.desktopconsole;

import com.shashwat.examplatformbackend.desktopconsole.DesktopConsoleDtos.DeviceResponse;
import com.shashwat.examplatformbackend.desktopconsole.DesktopConsoleDtos.EnrollmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DesktopConsoleDeviceController {
    private static final String ENROLLMENT_HEADER = "X-Desktop-Enrollment-Token";

    private final DesktopConsoleDeviceService service;

    public DesktopConsoleDeviceController(DesktopConsoleDeviceService service) {
        this.service = service;
    }

    @PostMapping("/api/desktop-console/enroll")
    public ResponseEntity<DeviceResponse> enroll(
            @RequestHeader(value = ENROLLMENT_HEADER, required = false) String token,
            @Valid @RequestBody EnrollmentRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.enroll(request, token));
    }

    @GetMapping("/api/master-admin/desktop-devices")
    public List<DeviceResponse> list() {
        return service.list();
    }

    @PatchMapping("/api/master-admin/desktop-devices/{id}/revoke")
    public DeviceResponse revoke(@PathVariable Long id) {
        return service.revoke(id);
    }
}
