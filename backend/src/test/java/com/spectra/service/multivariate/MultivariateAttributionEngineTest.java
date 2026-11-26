package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultivariateAttributionEngineTest {

    private MultivariateAttributionEngine attributionEngine;

    @BeforeEach
    void setUp() {
        attributionEngine = new MultivariateAttributionEngine();
    }

    @Test
    void computeAttributions_ConservationLaw_SumSignedContributionsEqualsD2() {
        double[] raw = { 75.0, 80.0, 350.0, 5.0 };
        double[] mean = { 50.0, 50.0, 150.0, 1.0 };
        double[] z = { 25.0, 30.0, 40.0, 40.0 }; // in standardized Z units

        // Realistic precision product vector v = Sigma^-1 * z
        double[] v = { 1.2, -0.4, 0.8, 2.5 };

        MultivariateAttributionEngine.AttributionResult result =
            attributionEngine.computeAttributions(raw, mean, z, v);

        double sumContributions = 0.0;
        for (double c : result.signedContributions()) {
            sumContributions += c;
        }

        // Sum of signed contributions must equal squared Mahalanobis distance D_M^2
        assertEquals(result.squaredDistance(), sumContributions, 1e-12);
        assertTrue(result.squaredDistance() > 0.0);
    }

    @Test
    void computeAttributions_DriverShare_NonNegativeAndSumsTo100Percent() {
        double[] raw = { 75.0, 50.0, 150.0, 1.0 };
        double[] mean = { 50.0, 50.0, 150.0, 1.0 };
        double[] z = { 25.0, -10.0, 5.0, -2.0 };
        double[] v = { 2.0, 0.5, -0.2, -1.0 };

        MultivariateAttributionEngine.AttributionResult result =
            attributionEngine.computeAttributions(raw, mean, z, v);

        double sumShare = 0.0;
        for (double share : result.driverSharePercents()) {
            assertTrue(share >= 0.0, "DriverShare must be non-negative: " + share);
            sumShare += share;
        }

        // Must sum to exactly 100%
        assertEquals(100.0, sumShare, 1e-9);

        // Verify that ranked contributions match the primary driver
        List<FeatureContribution> ranked = result.rankedContributions();
        assertEquals(4, ranked.size());
        assertEquals(result.primaryDriver(), ranked.get(0).metricType());
        assertEquals(1, ranked.get(0).rank());
        assertEquals(4, ranked.get(3).rank());

        // Verify descending order
        for (int i = 0; i < 3; i++) {
            assertTrue(ranked.get(i).driverSharePercent() >= ranked.get(i + 1).driverSharePercent());
        }
    }

    @Test
    void computeAttributions_ZeroDeviation_SplitsDriverShareEqually() {
        // Observation exactly at baseline mean -> z = [0, 0, 0, 0]
        double[] raw = { 50.0, 50.0, 150.0, 1.0 };
        double[] mean = { 50.0, 50.0, 150.0, 1.0 };
        double[] z = { 0.0, 0.0, 0.0, 0.0 };
        double[] v = { 0.0, 0.0, 0.0, 0.0 };

        MultivariateAttributionEngine.AttributionResult result =
            attributionEngine.computeAttributions(raw, mean, z, v);

        assertEquals(0.0, result.squaredDistance(), 1e-12);
        for (double share : result.driverSharePercents()) {
            assertEquals(25.0, share, 1e-9); // 100% / 4 = 25% each
        }
    }
}
