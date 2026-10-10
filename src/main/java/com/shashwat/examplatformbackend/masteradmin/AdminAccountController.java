package com.shashwat.examplatformbackend.masteradmin;

import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.AdminAccountResponse;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.InviteAdminRequest;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.PasswordSetupRequest;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.PasswordSetupResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminAccountController {
    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    @PostMapping("/api/admin-access/setup-password")
    public PasswordSetupResponse setupPassword(@Valid @RequestBody PasswordSetupRequest request) {
        return service.setupPassword(request);
    }

    @GetMapping("/api/master-admin/accounts")
    public List<AdminAccountResponse> list() {
        return service.listAccounts();
    }

    @PostMapping("/api/master-admin/accounts")
    public AdminAccountResponse invite(@Valid @RequestBody InviteAdminRequest request) {
        return service.invite(request);
    }

    @PatchMapping("/api/master-admin/accounts/{id}/revoke")
    public AdminAccountResponse revoke(@PathVariable Long id) {
        return service.setAccess(id, false);
    }

    @PatchMapping("/api/master-admin/accounts/{id}/restore")
    public AdminAccountResponse restore(@PathVariable Long id) {
        return service.setAccess(id, true);
    }

    @PatchMapping("/api/master-admin/accounts/{id}/require-password-reset")
    public AdminAccountResponse requirePasswordReset(@PathVariable Long id) {
        return service.requirePasswordReset(id);
    }
}
