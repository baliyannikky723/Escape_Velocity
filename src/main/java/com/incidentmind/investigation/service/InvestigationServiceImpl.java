package com.incidentmind.investigation.service;

import com.incidentmind.agent.core.AgentExecutionService;
import com.incidentmind.agent.dto.AgentRunResponse;
import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.repository.AuditEventRepository;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.common.filter.CorrelationContext;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.repository.IncidentRepository;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.investigation.dto.AuditEventResponse;
import com.incidentmind.investigation.dto.CreateInvestigationRequest;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.dto.TaskResponse;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.investigation.repository.InvestigationRepository;
import com.incidentmind.planner.core.InvestigationPlanner;
import com.incidentmind.planner.core.PlannerContext;
import com.incidentmind.planner.model.InvestigationPlan;
import com.incidentmind.planner.model.PlanAction;
import com.incidentmind.planner.model.PlannedTask;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.blackboard.core.DefaultInvestigationBlackboard;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.blackboard.dto.InvestigationMetricsResponse;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import com.incidentmind.critic.dto.CriticEvaluationResponse;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.executor.core.DefaultInvestigationExecutor;
import com.incidentmind.executor.core.InvestigationExecutor;
import com.incidentmind.executor.model.ExecutionContext;
import com.incidentmind.executor.model.ExecutionResult;
import com.incidentmind.stopping.core.DefaultInvestigationStoppingPolicy;
import com.incidentmind.stopping.core.InvestigationStoppingPolicy;
import com.incidentmind.stopping.model.StoppingContext;
import com.incidentmind.stopping.model.StoppingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InvestigationServiceImpl implements InvestigationService {

    private final InvestigationRepository investigationRepository;
    private final IncidentRepository incidentRepository;
    private final InvestigationTaskRepository taskRepository;
    private final EvidenceRepository evidenceRepository;
    private final AuditEventRepository auditEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final AuditService auditService;
    private final InvestigationPlanner investigationPlanner;
    private final AgentExecutionService agentExecutionService;
    private final com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository;
    private final InvestigationExecutor investigationExecutor;
    private final InvestigationStoppingPolicy stoppingPolicy;
    private final com.incidentmind.tool.repository.ToolCallRepository toolCallRepository;
    private final com.incidentmind.report.core.InvestigationSynthesizer investigationSynthesizer;

    private final Map<UUID, InvestigationBlackboard> blackboards = new ConcurrentHashMap<>();

    public InvestigationServiceImpl(InvestigationRepository investigationRepository,
                                    IncidentRepository incidentRepository,
                                    InvestigationTaskRepository taskRepository,
                                    EvidenceRepository evidenceRepository,
                                    AuditEventRepository auditEventRepository,
                                    AgentRunRepository agentRunRepository,
                                    AuditService auditService,
                                    InvestigationPlanner investigationPlanner,
                                    AgentExecutionService agentExecutionService,
                                    com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository) {
        this(investigationRepository, incidentRepository, taskRepository, evidenceRepository,
                auditEventRepository, agentRunRepository, auditService, investigationPlanner,
                agentExecutionService, recoveryAttemptRepository, null, null, null, null);
    }

    public InvestigationServiceImpl(InvestigationRepository investigationRepository,
                                    IncidentRepository incidentRepository,
                                    InvestigationTaskRepository taskRepository,
                                    EvidenceRepository evidenceRepository,
                                    AuditEventRepository auditEventRepository,
                                    AgentRunRepository agentRunRepository,
                                    AuditService auditService,
                                    InvestigationPlanner investigationPlanner,
                                    AgentExecutionService agentExecutionService,
                                    com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository,
                                    InvestigationExecutor investigationExecutor,
                                    InvestigationStoppingPolicy stoppingPolicy) {
        this(investigationRepository, incidentRepository, taskRepository, evidenceRepository,
                auditEventRepository, agentRunRepository, auditService, investigationPlanner,
                agentExecutionService, recoveryAttemptRepository, investigationExecutor, stoppingPolicy, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InvestigationServiceImpl(InvestigationRepository investigationRepository,
                                    IncidentRepository incidentRepository,
                                    InvestigationTaskRepository taskRepository,
                                    EvidenceRepository evidenceRepository,
                                    AuditEventRepository auditEventRepository,
                                    AgentRunRepository agentRunRepository,
                                    AuditService auditService,
                                    InvestigationPlanner investigationPlanner,
                                    AgentExecutionService agentExecutionService,
                                    com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    InvestigationExecutor investigationExecutor,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    InvestigationStoppingPolicy stoppingPolicy,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    com.incidentmind.tool.repository.ToolCallRepository toolCallRepository,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false)
                                    com.incidentmind.report.core.InvestigationSynthesizer investigationSynthesizer) {
        this.investigationRepository = investigationRepository;
        this.incidentRepository = incidentRepository;
        this.taskRepository = taskRepository;
        this.evidenceRepository = evidenceRepository;
        this.auditEventRepository = auditEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.auditService = auditService;
        this.investigationPlanner = investigationPlanner;
        this.agentExecutionService = agentExecutionService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.investigationExecutor = investigationExecutor;
        this.stoppingPolicy = stoppingPolicy;
        this.toolCallRepository = toolCallRepository;
        this.investigationSynthesizer = investigationSynthesizer != null
                ? investigationSynthesizer
                : new com.incidentmind.report.core.DefaultInvestigationSynthesizer();
    }

    @Override
    @Transactional
    public InvestigationResponse createInvestigation(UUID incidentId, CreateInvestigationRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        String objective = (request != null && request.getObjective() != null && !request.getObjective().isBlank())
                ? request.getObjective().trim()
                : "Investigate root cause and causal chain for " + incident.getTitle();
        int maxTasks = (request != null && request.getMaxTasks() != null && request.getMaxTasks() > 0)
                ? request.getMaxTasks()
                : 10;
        int maxRetries = (request != null && request.getMaxRetriesPerTask() != null && request.getMaxRetriesPerTask() >= 0)
                ? request.getMaxRetriesPerTask()
                : 3;
        int maxRuntime = (request != null && request.getMaxRuntimeSeconds() != null && request.getMaxRuntimeSeconds() > 0)
                ? request.getMaxRuntimeSeconds()
                : 300;

        Instant now = Instant.now();
        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .incidentId(incident.getId())
                .status(InvestigationStatus.CREATED)
                .objective(objective)
                .maxTasks(maxTasks)
                .maxRetriesPerTask(maxRetries)
                .maxRuntimeSeconds(maxRuntime)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Investigation saved = investigationRepository.save(investigation);
        log.info("Created investigation id={} for incident id={}", saved.getId(), incident.getId());

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("incidentId", incident.getId().toString());
        auditData.put("incidentKey", incident.getIncidentKey());
        auditData.put("investigationId", saved.getId().toString());
        auditData.put("objective", saved.getObjective());
        auditData.put("maxTasks", saved.getMaxTasks());
        auditData.put("maxRetriesPerTask", saved.getMaxRetriesPerTask());
        auditData.put("maxRuntimeSeconds", saved.getMaxRuntimeSeconds());

        auditService.recordEvent(
                saved.getId(),
                null,
                null,
                AuditEventType.INVESTIGATION_CREATED,
                ActorType.USER,
                "api-user",
                auditData,
                CorrelationContext.getCorrelationIdAsUuid()
        );

        return InvestigationResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestigationResponse> getInvestigationsByIncidentId(UUID incidentId) {
        return investigationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(InvestigationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InvestigationResponse getInvestigationById(UUID id) {
        Investigation investigation = getInvestigationEntity(id);
        return InvestigationResponse.fromEntity(investigation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getInvestigationTasks(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        return taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId).stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> getInvestigationEvidence(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        return evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId).stream()
                .map(EvidenceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventResponse> getInvestigationAuditTrail(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        return auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId).stream()
                .map(AuditEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InvestigationResponse startInvestigation(UUID id) {
        Investigation investigation = getInvestigationEntity(id);
        UUID incidentId = investigation.getIncidentId();
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        UUID correlationId = CorrelationContext.getCorrelationIdAsUuid();

        // 1. Transition status: CREATED -> PLANNING -> RUNNING
        investigation.setStatus(InvestigationStatus.PLANNING);
        Instant now = Instant.now();
        if (investigation.getStartedAt() == null) {
            investigation.setStartedAt(now);
        }
        investigationRepository.save(investigation);

        Map<String, Object> startAuditData = new HashMap<>();
        startAuditData.put("status", "PLANNING");
        if (investigation.getObjective() != null) startAuditData.put("objective", investigation.getObjective());

        auditService.recordEvent(
                investigation.getId(),
                null,
                null,
                AuditEventType.INVESTIGATION_STARTED,
                ActorType.ORCHESTRATOR,
                "investigation-orchestrator",
                startAuditData,
                correlationId
        );

        investigation.setStatus(InvestigationStatus.RUNNING);
        investigationRepository.save(investigation);

        // 2. Dynamic Planning and Execution Loop
        int maxIterations = Math.min(investigation.getMaxTasks(), 10);
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            List<InvestigationTask> existingTasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
            if (existingTasks == null) {
                existingTasks = List.of();
            }
            List<Evidence> evidenceList = evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
            if (evidenceList == null) {
                evidenceList = List.of();
            }

            PlannerContext plannerContext = PlannerContext.builder()
                    .incident(incident)
                    .investigation(investigation)
                    .existingTasks(existingTasks)
                    .evidence(evidenceList)
                    .correlationId(correlationId)
                    .build();

            InvestigationPlan plan = investigationPlanner.plan(plannerContext);
            log.info("Planner evaluation (iteration {}): nextAction={}, reasoning='{}'",
                    iteration, plan.getNextAction(), plan.getReasoningSummary());

            Map<String, Object> planAuditData = new HashMap<>();
            planAuditData.put("iteration", iteration);
            planAuditData.put("nextAction", plan.getNextAction().name());
            planAuditData.put("reasoningSummary", plan.getReasoningSummary());
            planAuditData.put("tasksCount", plan.getTasks() != null ? plan.getTasks().size() : 0);

            auditService.recordEvent(
                    investigation.getId(),
                    null,
                    null,
                    AuditEventType.PLAN_CREATED,
                    ActorType.ORCHESTRATOR,
                    "investigation-planner",
                    planAuditData,
                    correlationId
            );

            final Incident currentIncident = incident;
            final Investigation currentInvestigation = investigation;
            InvestigationBlackboard blackboard = blackboards.computeIfAbsent(investigation.getId(), idKey ->
                    new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));

            // Evaluate stopping policy before task execution
            if (stoppingPolicy != null) {
                long runtimeSeconds = (System.currentTimeMillis() - investigation.getStartedAt().toEpochMilli()) / 1000;
                StoppingContext stopCtx = StoppingContext.builder()
                        .investigation(investigation)
                        .blackboard(blackboard)
                        .tasks(existingTasks)
                        .evidence(evidenceList)
                        .runtimeSeconds(runtimeSeconds)
                        .planHasNoFurtherTasks(plan.getNextAction() == PlanAction.NO_FURTHER_TASKS)
                        .correlationId(correlationId)
                        .build();

                StoppingResult stopResult = stoppingPolicy.evaluate(stopCtx);
                if (stopResult.isShouldStop()) {
                    log.info("Stopping policy triggered ({}): {}", stopResult.getDecision(), stopResult.getReason());
                    break;
                }
            }

            // Create new tasks dynamically identified by planner
            Set<String> existingTaskTypes = existingTasks.stream()
                    .map(t -> t.getTaskType().toUpperCase())
                    .collect(Collectors.toSet());

            boolean anyTaskCreated = false;
            if (plan.getTasks() != null) {
                for (PlannedTask plannedTask : plan.getTasks()) {
                    if (!existingTaskTypes.contains(plannedTask.getTaskType().toUpperCase())) {
                        Instant taskCreateTime = Instant.now();
                        InvestigationTask newTask = InvestigationTask.builder()
                                .id(UUID.randomUUID())
                                .investigationId(investigation.getId())
                                .parentTaskId(plannedTask.getParentTaskId())
                                .taskType(plannedTask.getTaskType())
                                .title(plannedTask.getTitle())
                                .description(plannedTask.getDescription())
                                .priority(plannedTask.getPriority())
                                .status(TaskStatus.PENDING)
                                .assignedAgentType(plannedTask.getAssignedAgentType())
                                .attemptCount(0)
                                .maxAttempts(investigation.getMaxRetriesPerTask() + 1)
                                .createdAt(taskCreateTime)
                                .updatedAt(taskCreateTime)
                                .build();

                        newTask = taskRepository.save(newTask);
                        anyTaskCreated = true;
                        existingTaskTypes.add(newTask.getTaskType().toUpperCase());

                        Map<String, Object> taskCreatedData = new HashMap<>();
                        if (newTask.getId() != null) taskCreatedData.put("taskId", newTask.getId().toString());
                        if (newTask.getTaskType() != null) taskCreatedData.put("taskType", newTask.getTaskType());
                        if (newTask.getTitle() != null) taskCreatedData.put("title", newTask.getTitle());
                        taskCreatedData.put("assignedAgentType", newTask.getAssignedAgentType() != null ? newTask.getAssignedAgentType() : "auto");

                        auditService.recordEvent(
                                investigation.getId(),
                                newTask.getId(),
                                null,
                                AuditEventType.TASK_CREATED,
                                ActorType.ORCHESTRATOR,
                                "investigation-planner",
                                taskCreatedData,
                                correlationId
                        );

                        log.info("Dynamically created task: id={}, type='{}', parent='{}'",
                                newTask.getId(), newTask.getTaskType(), newTask.getParentTaskId());
                    }
                }
            }

            // Execute ready tasks sequentially via InvestigationExecutor (or fallback to AgentExecutionService)
            List<InvestigationTask> updatedTasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
            if (updatedTasks == null) {
                updatedTasks = List.of();
            }
            boolean anyTaskExecuted = false;

            for (InvestigationTask task : updatedTasks) {
                if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.READY) {
                    if (investigationExecutor != null) {
                        ExecutionContext execContext = ExecutionContext.builder()
                                .incident(incident)
                                .investigation(investigation)
                                .task(task)
                                .blackboard(blackboard)
                                .correlationId(correlationId)
                                .build();
                        investigationExecutor.execute(execContext);
                    } else {
                        // Check parent dependency if set
                        if (task.getParentTaskId() != null) {
                            InvestigationTask parent = taskRepository.findById(task.getParentTaskId()).orElse(null);
                            if (parent != null && parent.getStatus() != TaskStatus.COMPLETED && parent.getStatus() != TaskStatus.FAILED) {
                                task.setStatus(TaskStatus.BLOCKED);
                                taskRepository.save(task);
                                continue;
                            }
                        }
                        agentExecutionService.executeTask(incident, investigation, task, correlationId);
                    }
                    anyTaskExecuted = true;
                }
            }

            if (plan.getNextAction() == PlanAction.NO_FURTHER_TASKS && !anyTaskCreated && !anyTaskExecuted) {
                break;
            }

            if (!anyTaskCreated && !anyTaskExecuted) {
                break;
            }
        }

        // 3. Set terminal status: WAITING
        investigation.setStatus(InvestigationStatus.WAITING);
        investigation.setUpdatedAt(Instant.now());
        investigation = investigationRepository.save(investigation);

        log.info("Investigation id={} execution completed with status WAITING", investigation.getId());
        return InvestigationResponse.fromEntity(investigation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRunResponse> getInvestigationAgentRuns(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        List<AgentRunResponse> agentRuns = new ArrayList<>();

        for (InvestigationTask task : tasks) {
            List<AgentRun> runs = agentRunRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            for (AgentRun run : runs) {
                agentRuns.add(AgentRunResponse.fromEntity(run));
            }
        }

        return agentRuns;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.incidentmind.recovery.dto.RecoveryAttemptResponse> getInvestigationRecoveryAttempts(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        return recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId).stream()
                .map(com.incidentmind.recovery.dto.RecoveryAttemptResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InvestigationMetricsResponse getInvestigationMetrics(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);
        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));
        return InvestigationMetricsResponse.fromModel(bb.getMetrics());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CriticEvaluationResponse> getInvestigationCriticEvaluations(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);
        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));
        return bb.getCriticResults().stream()
                .map(CriticEvaluationResponse::fromModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public com.incidentmind.report.dto.InvestigationReportResponse getInvestigationReport(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);

        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        List<Evidence> evidenceList = evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        List<RecoveryAttempt> recoveryAttempts = recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        List<AuditEvent> auditEvents = auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId);

        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));

        return investigationSynthesizer.synthesizeReport(
                incident,
                investigation,
                tasks,
                evidenceList,
                recoveryAttempts,
                auditEvents,
                bb
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.incidentmind.report.dto.TimelineEventDto> getInvestigationTimeline(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<AuditEvent> events = auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId);
        return events.stream()
                .map(com.incidentmind.report.dto.TimelineEventDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public com.incidentmind.report.dto.TaskGraphResponse getInvestigationGraph(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);

        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);
        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));

        Map<UUID, com.incidentmind.critic.model.CriticDecision> criticDecisions = new HashMap<>();
        for (com.incidentmind.critic.model.CriticResult cr : bb.getCriticResults()) {
            if (cr.getTaskId() != null) {
                criticDecisions.put(cr.getTaskId(), cr.getDecision());
            }
        }

        List<com.incidentmind.report.dto.TaskGraphNode> nodes = new ArrayList<>();
        List<com.incidentmind.report.dto.TaskGraphEdge> edges = new ArrayList<>();

        for (InvestigationTask task : tasks) {
            nodes.add(com.incidentmind.report.dto.TaskGraphNode.builder()
                    .taskId(task.getId())
                    .taskType(task.getTaskType())
                    .title(task.getTitle())
                    .description(task.getDescription())
                    .assignedAgent(task.getAssignedAgentType())
                    .status(task.getStatus())
                    .priority(task.getPriority())
                    .parentTaskId(task.getParentTaskId())
                    .createdAt(task.getCreatedAt())
                    .completedAt(task.getCompletedAt())
                    .criticDecision(criticDecisions.get(task.getId()))
                    .retryCount(Math.max(0, task.getAttemptCount() - 1))
                    .build());

            if (task.getParentTaskId() != null) {
                edges.add(com.incidentmind.report.dto.TaskGraphEdge.builder()
                        .fromTaskId(task.getParentTaskId())
                        .toTaskId(task.getId())
                        .relationship("DEPENDS_ON")
                        .build());
            }
        }

        return com.incidentmind.report.dto.TaskGraphResponse.builder()
                .investigationId(investigationId)
                .nodes(nodes)
                .edges(edges)
                .totalNodes(nodes.size())
                .totalEdges(edges.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.incidentmind.report.dto.AgentActivityResponse> getInvestigationAgents(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);

        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);
        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));

        Map<UUID, com.incidentmind.critic.model.CriticDecision> criticDecisions = new HashMap<>();
        for (com.incidentmind.critic.model.CriticResult cr : bb.getCriticResults()) {
            if (cr.getTaskId() != null) {
                criticDecisions.put(cr.getTaskId(), cr.getDecision());
            }
        }

        List<com.incidentmind.report.dto.AgentActivityResponse> activities = new ArrayList<>();
        for (InvestigationTask task : tasks) {
            List<AgentRun> runs = agentRunRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            for (AgentRun run : runs) {
                activities.add(com.incidentmind.report.dto.AgentActivityResponse.fromEntity(run, criticDecisions.get(task.getId())));
            }
        }

        return activities;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.incidentmind.report.dto.ToolExecutionResponse> getInvestigationTools(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        List<com.incidentmind.report.dto.ToolExecutionResponse> toolResponses = new ArrayList<>();

        if (toolCallRepository != null) {
            for (InvestigationTask task : tasks) {
                List<AgentRun> runs = agentRunRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
                for (AgentRun run : runs) {
                    List<com.incidentmind.tool.entity.ToolCall> calls = toolCallRepository.findByAgentRunIdOrderByCreatedAtAsc(run.getId());
                    if (calls != null) {
                        for (com.incidentmind.tool.entity.ToolCall call : calls) {
                            toolResponses.add(com.incidentmind.report.dto.ToolExecutionResponse.fromEntity(call));
                        }
                    }
                }
            }
        }

        return toolResponses;
    }

    @Override
    @Transactional(readOnly = true)
    public com.incidentmind.report.dto.RecoverySummaryResponse getInvestigationRecoverySummary(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);

        int success = 0;
        int exhausted = 0;
        int retries = 0;
        int fallbacks = 0;
        int replans = 0;

        for (RecoveryAttempt ra : attempts) {
            if (ra.getStatus() == RecoveryStatus.SUCCESS) success++;
            if (ra.getStatus() == RecoveryStatus.FAILED) exhausted++;
            if (ra.getRecoveryType() != null) {
                switch (ra.getRecoveryType()) {
                    case RETRY -> retries++;
                    case FALLBACK_TOOL -> fallbacks++;
                    case REPLAN -> replans++;
                    default -> {}
                }
            }
        }

        List<com.incidentmind.recovery.dto.RecoveryAttemptResponse> attemptDtos = attempts.stream()
                .map(com.incidentmind.recovery.dto.RecoveryAttemptResponse::fromEntity)
                .collect(Collectors.toList());

        return com.incidentmind.report.dto.RecoverySummaryResponse.builder()
                .totalAttempts(attempts.size())
                .successfulRecoveries(success)
                .exhaustedRecoveries(exhausted)
                .retryCount(retries)
                .fallbackCount(fallbacks)
                .replanCount(replans)
                .attempts(attemptDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public com.incidentmind.report.dto.CriticSummaryResponse getInvestigationCriticSummary(UUID investigationId) {
        ensureInvestigationExists(investigationId);
        Investigation investigation = getInvestigationEntity(investigationId);
        Incident incident = incidentRepository.findById(investigation.getIncidentId()).orElse(null);
        final Incident currentIncident = incident;
        final Investigation currentInvestigation = investigation;
        InvestigationBlackboard bb = blackboards.computeIfAbsent(investigationId, idKey ->
                new DefaultInvestigationBlackboard(currentIncident, currentInvestigation, taskRepository, evidenceRepository, agentRunRepository, recoveryAttemptRepository));

        List<com.incidentmind.critic.model.CriticResult> criticResults = bb.getCriticResults();
        int accepted = 0;
        int rejected = 0;
        int inconclusive = 0;
        int humanApproval = 0;
        List<UUID> rejectedTaskIds = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        List<com.incidentmind.critic.dto.CriticEvaluationResponse> evals = new ArrayList<>();

        for (com.incidentmind.critic.model.CriticResult cr : criticResults) {
            evals.add(com.incidentmind.critic.dto.CriticEvaluationResponse.fromModel(cr));
            if (cr.getDecision() == com.incidentmind.critic.model.CriticDecision.ACCEPT) {
                accepted++;
            } else if (cr.getDecision() == com.incidentmind.critic.model.CriticDecision.REJECT) {
                rejected++;
                if (cr.getTaskId() != null) rejectedTaskIds.add(cr.getTaskId());
                if (cr.getReasons() != null) rejectionReasons.addAll(cr.getReasons());
            } else if (cr.getDecision() == com.incidentmind.critic.model.CriticDecision.INCONCLUSIVE) {
                inconclusive++;
            } else if (cr.getDecision() == com.incidentmind.critic.model.CriticDecision.HUMAN_APPROVAL_REQUIRED) {
                humanApproval++;
            }
        }

        return com.incidentmind.report.dto.CriticSummaryResponse.builder()
                .totalEvaluations(criticResults.size())
                .acceptedCount(accepted)
                .rejectedCount(rejected)
                .inconclusiveCount(inconclusive)
                .humanApprovalCount(humanApproval)
                .rejectedTaskIds(rejectedTaskIds)
                .rejectionReasons(rejectionReasons)
                .replanTriggeredByCritic(rejected > 0)
                .evaluations(evals)
                .build();
    }

    @Override
    @Transactional
    public com.incidentmind.investigation.dto.HumanActionResponse processHumanAction(UUID investigationId, com.incidentmind.investigation.dto.HumanActionRequest request) {
        ensureInvestigationExists(investigationId);
        Investigation investigation = getInvestigationEntity(investigationId);

        String actionStr = request.getAction().trim().toUpperCase();
        UUID correlationId = CorrelationContext.getCorrelationId() != null 
                ? UUID.fromString(CorrelationContext.getCorrelationId()) 
                : UUID.randomUUID();

        // Record HUMAN_ACTION_RECEIVED audit event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("action", actionStr);
        if (request.getNotes() != null) eventData.put("notes", request.getNotes());
        if (request.getApprovedAction() != null) eventData.put("approvedAction", request.getApprovedAction());
        if (request.getModifiedDirections() != null) eventData.put("modifiedDirections", request.getModifiedDirections());

        auditService.recordEvent(
                investigationId,
                null,
                null,
                AuditEventType.HUMAN_ACTION_RECEIVED,
                ActorType.USER,
                "human-operator",
                eventData,
                correlationId
        );

        String statusMessage;
        switch (actionStr) {
            case "STOP" -> {
                investigation.setStatus(InvestigationStatus.STOPPED);
                investigationRepository.save(investigation);
                statusMessage = "Investigation terminated by human operator.";
            }
            case "APPROVE_ACTION" -> {
                log.info("Human approved sensitive action '{}' for investigation {}", request.getApprovedAction(), investigationId);
                statusMessage = "Sensitive action approved. Proceeding with execution.";
            }
            case "REJECT_ACTION" -> {
                log.info("Human rejected sensitive action '{}' for investigation {}", request.getApprovedAction(), investigationId);
                statusMessage = "Sensitive action rejected. Continuing investigation without executing destructive action.";
            }
            case "MODIFY_PLAN" -> {
                log.info("Human supplied modified plan directions for investigation {}", investigationId);
                statusMessage = "Modified plan directions accepted. Triggering dynamic plan reconciliation.";
            }
            case "CONTINUE" -> {
                if (investigation.getStatus() == InvestigationStatus.WAITING) {
                    investigation.setStatus(InvestigationStatus.RUNNING);
                    investigationRepository.save(investigation);
                }
                statusMessage = "Investigation execution resumed autonomously.";
            }
            default -> throw new IllegalArgumentException("Unknown human action: " + actionStr);
        }

        return com.incidentmind.investigation.dto.HumanActionResponse.builder()
                .investigationId(investigationId)
                .action(actionStr)
                .status("PROCESSED")
                .message(statusMessage)
                .processedAt(Instant.now())
                .build();
    }

    private Investigation getInvestigationEntity(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investigation", id));
    }

    private void ensureInvestigationExists(UUID id) {
        if (!investigationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Investigation", id);
        }
    }
}
