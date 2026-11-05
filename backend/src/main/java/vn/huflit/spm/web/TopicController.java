package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.RegistrationService;
import vn.huflit.spm.service.TopicService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TopicController {
    private final TopicService topics;
    private final RegistrationService registrations;

    public TopicController(TopicService topics, RegistrationService registrations) {
        this.topics = topics;
        this.registrations = registrations;
    }

    @GetMapping("/topics")
    public List<TopicDto> all() {
        return topics.listAll();
    }

    @GetMapping("/topics/mine")
    @PreAuthorize("hasRole('LECTURER')")
    public List<TopicDto> mine() {
        return topics.listMine();
    }

    @PostMapping("/topics")
    @PreAuthorize("hasRole('LECTURER')")
    public TopicDto create(@Valid @RequestBody TopicRequest req) {
        return topics.create(req);
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasRole('LECTURER')")
    public TopicDto update(@PathVariable Long id, @Valid @RequestBody TopicRequest req) {
        return topics.update(id, req);
    }

    @DeleteMapping("/topics/{id}")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        topics.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- dang ky de tai -----
    @PostMapping("/registrations")
    @PreAuthorize("hasRole('STUDENT')")
    public RegistrationDto register(@Valid @RequestBody RegistrationRequest req) {
        return registrations.register(req);
    }

    @DeleteMapping("/registrations/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        registrations.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/registrations/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<RegistrationDto> mineRegs() {
        return registrations.forMyTeam();
    }

    @GetMapping("/registrations/lecturer")
    @PreAuthorize("hasRole('LECTURER')")
    public List<RegistrationDto> forLecturer() {
        return registrations.forLecturer();
    }

    @PostMapping("/registrations/{id}/decision")
    @PreAuthorize("hasRole('LECTURER')")
    public RegistrationDto decide(@PathVariable Long id, @RequestBody DecisionRequest req) {
        return registrations.decide(id, req);
    }
}
