package com.spectra.dto;

import java.time.Instant;

public record EnrollmentTokenResponse(
        Long enrollmentId,
        Long enterpriseId,
        String enterpriseName,
        String organizationCode,
        String tokenPrefix,
        String enrollmentToken,
        String status,
        Instant expiresAt,
        String installCommand
) {
}
