package com.shashwat.examplatformbackend.evaluation.manual;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualEvaluationGrantTests {
    @Test
    void approvalIsExamBoundOneTimeAndAudited() {
        ManualEvaluationGrant grant = new ManualEvaluationGrant("EXAM01", "Normal evaluation failed strict authentication.", "exam-admin");
        grant.approve("master-admin", "Approved for isolated recovery.", 30);

        assertTrue(grant.isUsable(Instant.now()));
        assertThrows(IllegalStateException.class, () -> grant.assertUsable("EXAM02", Instant.now()));

        grant.consume("exam-admin", "manual-key.json", "a".repeat(64), 12);

        assertEquals(ManualEvaluationGrantStatus.CONSUMED, grant.getStatus());
        assertFalse(grant.isUsable(Instant.now()));
        assertEquals("a".repeat(64), grant.getKeySha256());
        assertEquals(12, grant.getEvaluatedResponseCount());
        assertNotNull(grant.getConsumedAt());
        assertThrows(IllegalStateException.class,
                () -> grant.consume("exam-admin", "second-key.json", "b".repeat(64), 12));
    }

    @Test
    void rejectedRequestCannotBeApprovedOrConsumed() {
        ManualEvaluationGrant grant = new ManualEvaluationGrant("EXAM01", "The standard key recovery could not complete.", "exam-admin");
        grant.reject("master-admin", "Insufficient evidence.");

        assertEquals(ManualEvaluationGrantStatus.REJECTED, grant.getStatus());
        assertFalse(grant.isUsable(Instant.now()));
        assertThrows(IllegalStateException.class, () -> grant.approve("master-admin", "Retry", 30));
        assertThrows(IllegalStateException.class,
                () -> grant.consume("exam-admin", "key.json", "a".repeat(64), 1));
    }
}
