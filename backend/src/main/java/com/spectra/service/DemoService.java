package com.spectra.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;

@Service
@Profile("demo")
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final MonitoredServiceRepository serviceRepository;
    private final ServiceMetricRepository metricRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final java.util.concurrent.ConcurrentHashMap<Long, String> userActiveScenario = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public DemoService(
            MonitoredServiceRepository serviceRepository,
            ServiceMetricRepository metricRepository,
            AlertRuleRepository ruleRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentRepository incidentRepository,
            IncidentNoteRepository noteRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.serviceRepository = serviceRepository;
        this.metricRepository = metricRepository;
        this.ruleRepository = ruleRepository;
        this.alertEventRepository = alertEventRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.incidentRepository = incidentRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User getOrCreateDemoUser() {
        return userRepository.findByEmail("operator@spectra.local").orElseGet(() -> {
            User newUser = new User();
            newUser.setFullName("Spectra Demo Operator");
            newUser.setEmail("operator@spectra.local");
            newUser.setPassword(passwordEncoder.encode("Password123!"));
            newUser.setRole(Role.USER);
            return userRepository.save(newUser);
        });
    }

    public String getActiveScenario(User user) {
        if (user == null) return "DATABASE_CONTENTION";
        return userActiveScenario.getOrDefault(user.getId(), "DATABASE_CONTENTION");
    }

    @Transactional
    public MonitoredService getOrCreateService(User user, String name, String endpointUrl, String environment, String description) {
        return serviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    MonitoredService svc = new MonitoredService();
                    svc.setUser(user);
                    svc.setName(name);
                    svc.setEndpointUrl(endpointUrl);
                    svc.setEnvironment(environment);
                    svc.setDescription(description);
                    return serviceRepository.saveAndFlush(svc);
                });
    }

    @Transactional
    public AlertRule getOrCreateRule(MonitoredService service, String ruleName, String metricName, String unit, String comparator, Double threshold, String severity) {
        return ruleRepository.findByServiceId(service.getId())
                .stream()
                .filter(r -> r.getRuleName().equalsIgnoreCase(ruleName))
                .findFirst()
                .orElseGet(() -> {
                    AlertRule rule = new AlertRule();
                    rule.setService(service);
                    rule.setRuleName(ruleName);
                    rule.setMetricName(metricName);
                    rule.setUnit(unit);
                    rule.setComparator(comparator);
                    rule.setThresholdValue(threshold);
                    rule.setSeverity(severity);
                    rule.setActive(true);
                    return ruleRepository.saveAndFlush(rule);
                });
    }

    @Transactional
    public void clearTelemetryAndIncidents(User user) {
        if (user == null) return;
        List<MonitoredService> services = serviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (MonitoredService svc : services) {
            Long svcId = svc.getId();

            // 1. Delete incident notes & incidents
            List<Incident> incidents = incidentRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(svcId);
            for (Incident inc : incidents) {
                List<IncidentNote> notes = noteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId());
                noteRepository.deleteAll(notes);
            }
            noteRepository.flush();
            incidentRepository.deleteAll(incidents);
            incidentRepository.flush();

            // 2. Delete anomalies
            List<AnomalyEvent> anomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                    svcId, org.springframework.data.domain.PageRequest.of(0, 5000));
            anomalyEventRepository.deleteAll(anomalies);
            anomalyEventRepository.flush();

            // 3. Delete alerts
            List<AlertEvent> alerts = alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(svcId);
            alertEventRepository.deleteAll(alerts);
            alertEventRepository.flush();

            // 4. Delete metrics
            List<ServiceMetric> metrics = metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                    svcId, org.springframework.data.domain.PageRequest.of(0, 5000));
            metricRepository.deleteAll(metrics);
            metricRepository.flush();
        }
    }

    @Transactional
    public void resetDemoData(User user) {
        if (user == null) return;
        Long userId = user.getId();
        log.info("Resetting demo data for user: {}", user.getEmail());

        clearTelemetryAndIncidents(user);

        List<MonitoredService> services = serviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (MonitoredService svc : services) {
            List<AlertRule> rules = ruleRepository.findByServiceId(svc.getId());
            ruleRepository.deleteAll(rules);
        }
        ruleRepository.flush();

        serviceRepository.deleteAll(services);
        serviceRepository.flush();
        userActiveScenario.remove(userId);
        log.info("Demo data reset complete for user: {}", user.getEmail());
    }

    @Transactional
    public void seedDemoData(User user) {
        seedDemoData(user, "DATABASE_CONTENTION");
    }

    @Transactional
    public void seedDemoData(User user, String scenario) {
        if (user == null) return;

        // Clear existing telemetry, alerts, incidents, anomalies with immediate database flush
        clearTelemetryAndIncidents(user);

        Random rng = new Random();
        String activeScenario;
        if (scenario == null || scenario.trim().isEmpty() || "RANDOM".equalsIgnoreCase(scenario.trim())) {
            String[] pool = {"DATABASE_CONTENTION", "MEMORY_LEAK", "CPU_STARVATION", "NETWORK_DEPENDENCY"};
            activeScenario = pool[rng.nextInt(pool.length)];
        } else {
            activeScenario = scenario.trim().toUpperCase();
        }

        userActiveScenario.put(user.getId(), activeScenario);
        log.info("Seeding live demo cluster with scenario '{}' for: {}", activeScenario, user.getEmail());
        Instant now = Instant.now();

        // 1. Get or Create 4 Core Microservices (Idempotent, avoids unique constraint violation)
        MonitoredService orderService = getOrCreateService(
                user, "Order-Processing-Service", "http://order-processor.cluster.local:8081",
                "PRODUCTION", "Core distributed transaction and fulfillment microservice (Cluster: Market)");

        MonitoredService paymentService = getOrCreateService(
                user, "Payment-Service", "http://payment-gateway.cluster.local:8080",
                "PRODUCTION", "High-throughput PCI-DSS payment and settlement microservice (Cluster: Bank)");

        MonitoredService authService = getOrCreateService(
                user, "Auth-Identity-Service", "http://auth-service.cluster.local:8082",
                "PRODUCTION", "OAuth2 / OIDC token authority and customer identity provider (Cluster: Security)");

        MonitoredService dbService = getOrCreateService(
                user, "Database-Gateway", "http://postgres-pool.cluster.local:5432",
                "PRODUCTION", "PostgreSQL connection pooling and read-replica gateway (Cluster: Persistence)");

        // 2. Get or Create Production Alert Rules (Idempotent)
        AlertRule orderLatencyRule = getOrCreateRule(
                orderService, "Critical Query Latency Surge", "RESPONSE_TIME",
                "MILLISECONDS", "GREATER_THAN", 2000.0, "CRITICAL");

        AlertRule paymentMemRule = getOrCreateRule(
                paymentService, "Heap Exhaustion & OOM Risk", "MEMORY_USAGE",
                "PERCENT", "GREATER_THAN", 85.0, "CRITICAL");

        AlertRule dbCpuRule = getOrCreateRule(
                dbService, "Compute Worker Saturation", "CPU_USAGE",
                "PERCENT", "GREATER_THAN", 80.0, "CRITICAL");

        AlertRule authNetRule = getOrCreateRule(
                authService, "Downstream Network Latency", "RESPONSE_TIME",
                "MILLISECONDS", "GREATER_THAN", 2000.0, "CRITICAL");

        // 3. Seed Dense Baseline Metrics (35 historical points per service = 420+ total metric records)
        // Architecturally differentiated per microservice and dynamically reflecting the active failure scenario!
        List<MonitoredService> allServices = List.of(orderService, paymentService, authService, dbService);
        for (MonitoredService s : allServices) {
            String sName = s.getName();
            boolean isOrder = sName.contains("Order");
            boolean isPayment = sName.contains("Payment");
            boolean isDb = sName.contains("Database");
            boolean isAuth = sName.contains("Auth");

            for (int i = 35; i >= 1; i--) {
                Instant metricTime = now.minus(i * 2, ChronoUnit.MINUTES);

                double cpuVal;
                double memVal;
                double latVal;

                if (isOrder) {
                    cpuVal = Math.round((34.0 + rng.nextGaussian() * 3.0) * 10.0) / 10.0;
                    memVal = Math.round((54.0 + rng.nextGaussian() * 2.8) * 10.0) / 10.0;
                    if (activeScenario.contains("DATABASE")) {
                        if (i > 12) {
                            latVal = Math.round((115.0 + rng.nextGaussian() * 12.0) * 10.0) / 10.0;
                        } else {
                            double surge = 2800.0 + (12 - i) * 160.0 + rng.nextGaussian() * 150.0;
                            latVal = Math.round(Math.min(5200.0, Math.max(1500.0, surge)) * 10.0) / 10.0;
                        }
                    } else {
                        latVal = Math.round((115.0 + rng.nextGaussian() * 14.0) * 10.0) / 10.0;
                    }
                } else if (isPayment) {
                    cpuVal = Math.round((30.0 + rng.nextGaussian() * 3.2) * 10.0) / 10.0;
                    latVal = Math.round((85.0 + rng.nextGaussian() * 9.0) * 10.0) / 10.0;
                    if (activeScenario.contains("MEMORY")) {
                        double leak = 50.0 + (35 - i) * (46.0 / 35.0) + rng.nextGaussian() * 1.2;
                        memVal = Math.round(Math.min(97.8, Math.max(48.0, leak)) * 10.0) / 10.0;
                    } else {
                        memVal = Math.round((48.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0;
                    }
                } else if (isDb) {
                    memVal = Math.round((60.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0;
                    latVal = Math.round((28.0 + rng.nextGaussian() * 4.0) * 10.0) / 10.0;
                    if (activeScenario.contains("CPU")) {
                        if (i > 10) {
                            cpuVal = Math.round((38.0 + rng.nextGaussian() * 3.5) * 10.0) / 10.0;
                        } else {
                            double spike = 75.0 + (10 - i) * 2.3 + rng.nextGaussian() * 1.5;
                            cpuVal = Math.round(Math.min(98.8, Math.max(70.0, spike)) * 10.0) / 10.0;
                        }
                    } else {
                        cpuVal = Math.round((40.0 + rng.nextGaussian() * 3.5) * 10.0) / 10.0;
                    }
                } else {
                    cpuVal = Math.round((22.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0;
                    memVal = Math.round((40.0 + rng.nextGaussian() * 2.2) * 10.0) / 10.0;
                    if (activeScenario.contains("NET") || activeScenario.contains("TIMEOUT")) {
                        if (i > 10) {
                            latVal = Math.round((68.0 + rng.nextGaussian() * 8.0) * 10.0) / 10.0;
                        } else {
                            double timeout = 4200.0 + (10 - i) * 90.0 + rng.nextGaussian() * 120.0;
                            latVal = Math.round(Math.min(5400.0, Math.max(3500.0, timeout)) * 10.0) / 10.0;
                        }
                    } else {
                        latVal = Math.round((68.0 + rng.nextGaussian() * 7.5) * 10.0) / 10.0;
                    }
                }

                // CPU
                ServiceMetric mCpu = new ServiceMetric();
                mCpu.setService(s);
                mCpu.setMetricName("CPU_USAGE");
                mCpu.setUnit("PERCENT");
                mCpu.setMetricValue(cpuVal);
                mCpu.setRecordedAt(metricTime);
                metricRepository.save(mCpu);

                // Memory
                ServiceMetric mMem = new ServiceMetric();
                mMem.setService(s);
                mMem.setMetricName("MEMORY_USAGE");
                mMem.setUnit("PERCENT");
                mMem.setMetricValue(memVal);
                mMem.setRecordedAt(metricTime);
                metricRepository.save(mMem);

                // Latency
                ServiceMetric mLat = new ServiceMetric();
                mLat.setService(s);
                mLat.setMetricName("RESPONSE_TIME");
                mLat.setUnit("MILLISECONDS");
                mLat.setMetricValue(latVal);
                mLat.setRecordedAt(metricTime);
                metricRepository.save(mLat);
            }
        }

        // 4. Inject Specific Failure Scenario with Alert, Anomaly, Incident, and Logs
        Instant errorTime = now.minus(30, ChronoUnit.SECONDS);
        MonitoredService targetSvc;
        AlertRule targetRule;
        String metricName;
        String metricUnit;
        Double errorVal;
        String incidentTitle;
        String incidentDesc;
        String anomalyMsg;

        double baselineMean;
        double baselineStdDev;
        double anomalyScore;

        if (activeScenario.contains("MEMORY")) {
            // Scenario: JVM Heap Memory Leak
            targetSvc = paymentService;
            targetRule = paymentMemRule;
            metricName = "MEMORY_USAGE";
            metricUnit = "PERCENT";
            errorVal = Math.round((95.0 + rng.nextDouble() * 3.8) * 10.0) / 10.0;
            baselineMean = 52.0;
            baselineStdDev = 3.2;
            anomalyScore = Math.round(((errorVal - baselineMean) / baselineStdDev) * 10.0) / 10.0;
            incidentTitle = "Payment-Service JVM Heap Exhaustion & OutOfMemory Hazard";
            incidentDesc = String.format("Eviction threshold exceeded: cache footprint %.1f%%. java.lang.OutOfMemoryError: Java heap space. Major GC pause duration %dms causing payment transaction halts.", errorVal, 4200 + rng.nextInt(900));
            anomalyMsg = String.format("Hybrid anomaly detected: observed %.2f PERCENT deviates sharply from baseline mean %.2f (|Z|=%.1f, severity=CRITICAL)", errorVal, baselineMean, anomalyScore);
        } else if (activeScenario.contains("CPU")) {
            // Scenario: CPU Worker Starvation
            targetSvc = dbService;
            targetRule = dbCpuRule;
            metricName = "CPU_USAGE";
            metricUnit = "PERCENT";
            errorVal = Math.round((94.5 + rng.nextDouble() * 4.9) * 10.0) / 10.0;
            baselineMean = 35.0;
            baselineStdDev = 4.8;
            anomalyScore = Math.round(((errorVal - baselineMean) / baselineStdDev) * 10.0) / 10.0;
            incidentTitle = "Database-Gateway CPU Saturation & Worker Thread Starvation";
            incidentDesc = String.format("Worker pool saturation: active threads 256/256. Core utilization at %.1f%%. org.apache.tomcat.util.net.NioEndpoint - Thread starvation observed under burst read-replica throughput.", errorVal);
            anomalyMsg = String.format("Hybrid anomaly detected: observed %.2f PERCENT deviates sharply from baseline mean %.2f (|Z|=%.1f, severity=CRITICAL)", errorVal, baselineMean, anomalyScore);
        } else if (activeScenario.contains("NET") || activeScenario.contains("TIMEOUT")) {
            // Scenario: Network Dependency Cascade
            targetSvc = authService;
            targetRule = authNetRule;
            metricName = "RESPONSE_TIME";
            metricUnit = "MILLISECONDS";
            errorVal = Math.round((4800.0 + rng.nextDouble() * 1400.0) * 10.0) / 10.0;
            baselineMean = 110.0;
            baselineStdDev = 15.0;
            anomalyScore = Math.round(((errorVal - baselineMean) / baselineStdDev) * 10.0) / 10.0;
            incidentTitle = "Auth-Identity-Service Downstream Network Timeout & Error Surge";
            incidentDesc = String.format("Upstream dependency unreachable. java.net.SocketTimeoutException: Connect timed out to identity-vault.cluster.local:443 after 5000ms. Response latency surged to %.0fms.", errorVal);
            anomalyMsg = String.format("Hybrid anomaly detected: observed %.2f MILLISECONDS deviates sharply from baseline mean %.2f (|Z|=%.1f, severity=CRITICAL)", errorVal, baselineMean, anomalyScore);
        } else {
            // Default Scenario: Database Connection Pool Contention
            targetSvc = orderService;
            targetRule = orderLatencyRule;
            metricName = "RESPONSE_TIME";
            metricUnit = "MILLISECONDS";
            errorVal = Math.round((4400.0 + rng.nextDouble() * 1200.0) * 10.0) / 10.0;
            baselineMean = 115.0;
            baselineStdDev = 16.5;
            anomalyScore = Math.round(((errorVal - baselineMean) / baselineStdDev) * 10.0) / 10.0;
            incidentTitle = "Order-Processing-Service Connection Pool Starvation & Query Latency Surge";
            incidentDesc = String.format("HikariPool-1 active connections maxed at 50/50. com.zaxxer.hikari.pool.HikariPool - Connection is not available, request timed out after 5000ms. Downstream SQL query latency surged to %.0fms under burst checkout transaction throughput.", errorVal);
            anomalyMsg = String.format("Hybrid anomaly detected: observed %.2f MILLISECONDS deviates sharply from baseline mean %.2f (|Z|=%.1f, severity=CRITICAL)", errorVal, baselineMean, anomalyScore);
        }

        // Save Error Metric
        ServiceMetric errorMetric = new ServiceMetric();
        errorMetric.setService(targetSvc);
        errorMetric.setMetricName(metricName);
        errorMetric.setUnit(metricUnit);
        errorMetric.setMetricValue(errorVal);
        errorMetric.setRecordedAt(errorTime);
        errorMetric = metricRepository.save(errorMetric);

        // Save Triggered Alert
        AlertEvent alert = new AlertEvent();
        alert.setRule(targetRule);
        alert.setService(targetSvc);
        alert.setMetricName(metricName);
        alert.setUnit(metricUnit);
        alert.setTriggerValue(errorVal);
        alert.setThresholdValue(targetRule.getThresholdValue());
        alert.setSeverity("CRITICAL");
        alert.setStatus("TRIGGERED");
        alert.setMessage(metricName + " (" + errorVal + " " + metricUnit + ") breached rule '" + targetRule.getRuleName() + "' (Threshold: " + targetRule.getThresholdValue() + ")");
        alert.setTriggeredAt(errorTime);
        alert = alertEventRepository.save(alert);

        // Save Anomaly Event
        AnomalyEvent anomaly = new AnomalyEvent();
        anomaly.setService(targetSvc);
        anomaly.setMetric(errorMetric);
        anomaly.setMetricName(metricName);
        anomaly.setUnit(metricUnit);
        anomaly.setObservedValue(errorVal);
        anomaly.setBaselineMean(baselineMean);
        anomaly.setBaselineStdDev(baselineStdDev);
        anomaly.setAnomalyScore(anomalyScore);
        anomaly.setSeverity("CRITICAL");
        anomaly.setDirection("POSITIVE_SPIKE");
        anomaly.setStatus("DETECTED");
        anomaly.setMessage(anomalyMsg);
        anomaly.setDetectedAt(errorTime);
        anomalyEventRepository.save(anomaly);

        // Auto-Create Active Incident
        Incident incident = new Incident();
        incident.setService(targetSvc);
        incident.setAlert(alert);
        incident.setTitle(incidentTitle);
        incident.setDescription(incidentDesc);
        incident.setSeverity("CRITICAL");
        incident.setStatus("OPEN");
        incident.setAssignedTo("SRE On-Call Lead");
        incident.setTriggeredAt(errorTime);
        incident = incidentRepository.save(incident);

        // Add Initial Audit Trail Note
        IncidentNote initialNote = new IncidentNote();
        initialNote.setIncident(incident);
        initialNote.setAuthor("Spectra AIOps Engine");
        initialNote.setNoteText(
            "Automated incident escalation: Detected critical telemetry surge (" + errorVal + " " + metricUnit + "). " +
            "Multi-signal AI Root Cause Analysis and Runbook retrieval initiated."
        );
        initialNote.setCreatedAt(errorTime);
        noteRepository.save(initialNote);

        // Seed a realistic secondary WARNING alert on another service for operational depth
        MonitoredService warnSvc = targetSvc.equals(paymentService) ? authService : paymentService;
        AlertRule warnRule = targetSvc.equals(paymentService) ? authNetRule : paymentMemRule;
        double warnVal = targetSvc.equals(paymentService)
                ? Math.round((1450.0 + rng.nextDouble() * 350.0) * 10.0) / 10.0
                : Math.round((78.0 + rng.nextDouble() * 5.0) * 10.0) / 10.0;
        String warnMetricName = warnRule.getMetricName();
        String warnUnit = warnRule.getUnit();

        ServiceMetric warnMetric = new ServiceMetric();
        warnMetric.setService(warnSvc);
        warnMetric.setMetricName(warnMetricName);
        warnMetric.setUnit(warnUnit);
        warnMetric.setMetricValue(warnVal);
        warnMetric.setRecordedAt(errorTime.minus(45, ChronoUnit.SECONDS));
        metricRepository.save(warnMetric);

        AlertEvent warnAlert = new AlertEvent();
        warnAlert.setRule(warnRule);
        warnAlert.setService(warnSvc);
        warnAlert.setMetricName(warnMetricName);
        warnAlert.setUnit(warnUnit);
        warnAlert.setTriggerValue(warnVal);
        warnAlert.setThresholdValue(warnRule.getThresholdValue());
        warnAlert.setSeverity("WARNING");
        warnAlert.setStatus("TRIGGERED");
        warnAlert.setMessage("Elevated operational threshold: " + warnMetricName + " reached " + warnVal + " " + warnUnit);
        warnAlert.setTriggeredAt(errorTime.minus(45, ChronoUnit.SECONDS));
        alertEventRepository.save(warnAlert);

        log.info("Demo data seeding completed successfully: Scenario='{}', Incident #{} on {}.", activeScenario, incident.getId(), targetSvc.getName());
    }

    @Transactional(readOnly = true)
    public boolean hasExistingDemoData(User user) {
        if (user == null) return false;
        return serviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).size() >= 4;
    }

    @Transactional
    public int tickDemoMetrics(User user) {
        if (user == null) return 0;
        List<MonitoredService> services = serviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (services.isEmpty()) return 0;

        Instant now = Instant.now();
        Random rng = new Random();
        int count = 0;
        String activeScenario = userActiveScenario.getOrDefault(user.getId(), "DATABASE_CONTENTION");

        for (MonitoredService s : services) {
            String svcName = s.getName();
            boolean isOrder = svcName.contains("Order");
            boolean isPayment = svcName.contains("Payment");
            boolean isDb = svcName.contains("Database");
            boolean isAuth = svcName.contains("Auth");

            double cpuVal;
            double memVal;
            double latVal;

            if (isOrder) {
                cpuVal = Math.max(10.0, Math.min(99.0, Math.round((34.0 + rng.nextGaussian() * 3.5) * 10.0) / 10.0));
                memVal = Math.max(15.0, Math.min(99.0, Math.round((54.0 + rng.nextGaussian() * 2.8) * 10.0) / 10.0));
                if (activeScenario.contains("DATABASE")) {
                    latVal = Math.round((4450.0 + rng.nextGaussian() * 320.0) * 10.0) / 10.0;
                } else {
                    latVal = Math.max(20.0, Math.round((115.0 + rng.nextGaussian() * 14.0) * 10.0) / 10.0);
                }
            } else if (isPayment) {
                cpuVal = Math.max(10.0, Math.min(99.0, Math.round((30.0 + rng.nextGaussian() * 3.0) * 10.0) / 10.0));
                latVal = Math.max(20.0, Math.round((85.0 + rng.nextGaussian() * 9.0) * 10.0) / 10.0);
                if (activeScenario.contains("MEMORY")) {
                    memVal = Math.round((96.5 + rng.nextDouble() * 2.3) * 10.0) / 10.0;
                } else {
                    memVal = Math.max(15.0, Math.min(99.0, Math.round((48.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0));
                }
            } else if (isDb) {
                memVal = Math.max(15.0, Math.min(99.0, Math.round((60.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0));
                latVal = Math.max(10.0, Math.round((28.0 + rng.nextGaussian() * 4.0) * 10.0) / 10.0);
                if (activeScenario.contains("CPU")) {
                    cpuVal = Math.round((95.5 + rng.nextDouble() * 3.6) * 10.0) / 10.0;
                } else {
                    cpuVal = Math.max(10.0, Math.min(99.0, Math.round((40.0 + rng.nextGaussian() * 3.5) * 10.0) / 10.0));
                }
            } else {
                cpuVal = Math.max(10.0, Math.min(99.0, Math.round((22.0 + rng.nextGaussian() * 2.5) * 10.0) / 10.0));
                memVal = Math.max(15.0, Math.min(99.0, Math.round((40.0 + rng.nextGaussian() * 2.2) * 10.0) / 10.0));
                if (activeScenario.contains("NET") || activeScenario.contains("TIMEOUT")) {
                    latVal = Math.round((5050.0 + rng.nextGaussian() * 280.0) * 10.0) / 10.0;
                } else {
                    latVal = Math.max(20.0, Math.round((68.0 + rng.nextGaussian() * 7.5) * 10.0) / 10.0);
                }
            }

            ServiceMetric mCpu = new ServiceMetric();
            mCpu.setService(s);
            mCpu.setMetricName("CPU_USAGE");
            mCpu.setUnit("PERCENT");
            mCpu.setMetricValue(cpuVal);
            mCpu.setRecordedAt(now);
            metricRepository.save(mCpu);
            count++;

            ServiceMetric mMem = new ServiceMetric();
            mMem.setService(s);
            mMem.setMetricName("MEMORY_USAGE");
            mMem.setUnit("PERCENT");
            mMem.setMetricValue(memVal);
            mMem.setRecordedAt(now);
            metricRepository.save(mMem);
            count++;

            ServiceMetric mLat = new ServiceMetric();
            mLat.setService(s);
            mLat.setMetricName("RESPONSE_TIME");
            mLat.setUnit("MILLISECONDS");
            mLat.setMetricValue(latVal);
            mLat.setRecordedAt(now);
            metricRepository.save(mLat);
            count++;
        }

        return count;
    }
}
