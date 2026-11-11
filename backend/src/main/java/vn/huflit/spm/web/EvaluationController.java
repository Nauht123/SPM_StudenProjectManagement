package vn.huflit.spm.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.huflit.spm.dto.Dtos.*;
import vn.huflit.spm.service.EvaluationService;
import vn.huflit.spm.service.PeerEvaluationService;

/** Danh gia cheo an danh theo milestone, diem tong hop va dieu chinh diem cua giang vien. */
@RestController
@RequestMapping("/api")
public class EvaluationController {
    private final PeerEvaluationService peer;
    private final EvaluationService evaluation;

    public EvaluationController(PeerEvaluationService peer, EvaluationService evaluation) {
        this.peer = peer;
        this.evaluation = evaluation;
    }

    @GetMapping("/milestones/{id}/peer-evaluations/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public MyPeerEvaluationDto myPeerEvaluation(@PathVariable Long id) {
        return peer.mine(id);
    }

    @PutMapping("/milestones/{id}/peer-evaluations")
    @PreAuthorize("hasRole('STUDENT')")
    public MyPeerEvaluationDto savePeerEvaluation(@PathVariable Long id, @Valid @RequestBody PeerEvaluationRequest req) {
        return peer.save(id, req);
    }

    @GetMapping("/milestones/{id}/peer-evaluations/results")
    public PeerResultsDto peerResults(@PathVariable Long id) {
        return peer.results(id);
    }

    @GetMapping("/teams/{id}/evaluation-summary")
    public EvaluationSummaryDto summary(@PathVariable Long id) {
        return evaluation.summary(id);
    }

    @PostMapping("/teams/{id}/adjustments")
    @PreAuthorize("hasRole('LECTURER')")
    public AdjustmentDto adjust(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest req) {
        return evaluation.adjust(id, req);
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<AdjustmentDto> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(evaluation.revoke(id));
    }
}
