package com.shashwat.examplatformbackend.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/master-admin/result-controls")
public class MasterResultControlController {
    private final ResultControlService service;
    public MasterResultControlController(ResultControlService service) { this.service = service; }
    public record Decision(@NotNull Boolean withheld, @NotNull Boolean expectedWithheld, @NotBlank @Size(min = 5, max = 1000) String reason) {}
    @GetMapping
    public ResultControlService.Candidates list(@RequestParam(defaultValue = "") String exam,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "false") boolean heldOnly,
            @RequestParam(defaultValue = "0") int page) {
        return service.list(exam, search, heldOnly, page);
    }
    @PatchMapping("/{registrationId}")
    public ResultControlService.Candidate decide(@PathVariable String registrationId, @Valid @RequestBody Decision decision, Principal principal) {
        return service.decide(registrationId, decision.withheld(), decision.expectedWithheld(), decision.reason(), principal.getName());
    }
    @GetMapping("/{registrationId}/history")
    public List<ResultHoldEvent> history(@PathVariable String registrationId, @RequestParam(defaultValue = "0") int page) {
        return service.history(registrationId, page);
    }
}
