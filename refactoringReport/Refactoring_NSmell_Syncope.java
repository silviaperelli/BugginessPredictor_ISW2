//core/provisioning-java/src/main/java/org/apache/syncope/core/provisioning/java/data/TaskDataBinderImpl.java/getTaskTO(Task, TaskUtils, boolean)
//Buggy method from last release of the dataset (release ID --> 29 / release 1.2.27) with Max Number of Code Smell
// NSmell: 3

@Override
public <T extends AbstractTaskTO> T getTaskTO(final Task task, final TaskUtils taskUtils, final boolean details) {
    T taskTO = taskUtils.newTaskTO();
    BeanUtils.copyProperties(task, taskTO, IGNORE_TASK_PROPERTIES);

    TaskExec latestExec = taskExecDAO.findLatestStarted(task);
    if (latestExec == null) {
        taskTO.setLatestExecStatus(StringUtils.EMPTY);
    } else {
        taskTO.setLatestExecStatus(latestExec.getStatus());
        taskTO.setStart(latestExec.getStart());
        taskTO.setEnd(latestExec.getEnd());
    }

    if (details) {
        for (TaskExec execution : task.getExecs()) {
            if (execution != null) {
                taskTO.getExecutions().add(getExecTO(execution));
            }
        }
    }

    switch (taskUtils.getType()) {
        case PROPAGATION:
            if (!(task instanceof PropagationTask)) {
                throw new IllegalArgumentException("taskUtils is type Propagation but task is not PropagationTask: " + task.getClass().getName());
            }
            ((PropagationTaskTO) taskTO).setResource(((PropagationTask) task).getResource().getKey());
            ((PropagationTaskTO) taskTO).setAttributes(((PropagationTask) task).getSerializedAttributes());
            break;

        case SCHEDULED:
            if (!(task instanceof SchedTask)) {
                throw new IllegalArgumentException("taskUtils is type Sched but task is not SchedTask: " + task.getClass().getName());
            }
            setExecTime((SchedTaskTO) taskTO, task);
            break;

        case PULL:
            if (!(task instanceof PullTask)) {
                throw new IllegalArgumentException("taskUtils is type Pull but task is not PullTask: "  + task.getClass().getName());
            }

            setExecTime((SchedTaskTO) taskTO, task);
            ((PullTaskTO) taskTO).setDestinationRealm(((PullTask) task).getDestinatioRealm().getFullPath());
            ((PullTaskTO) taskTO).setResource(((PullTask) task).getResource().getKey());
            ((PullTaskTO) taskTO).setMatchingRule(((PullTask) task).getMatchingRule() == null ? MatchingRule.UPDATE : ((PullTask) task).getMatchingRule());
            ((PullTaskTO) taskTO).setUnmatchingRule(((PullTask) task).getUnmatchingRule() == null ? UnmatchingRule.PROVISION : ((PullTask) task).getUnmatchingRule());


            for (AnyTemplate template : ((PullTask) task).getTemplates()) {
                            ((PullTaskTO) taskTO).getTemplates().put(template.getAnyType().getKey(), template.get());
            }
            break;

        case PUSH:
            if (!(task instanceof PushTask)) {
                throw new IllegalArgumentException("taskUtils is type Push but task is not PushTask: " + task.getClass().getName());
            }
            setExecTime((SchedTaskTO) taskTO, task);
            ((PushTaskTO) taskTO).setResource(((PushTask) task).getResource().getKey());
            ((PushTaskTO) taskTO).setMatchingRule(((PushTask) task).getMatchingRule() == null ? MatchingRule.LINK : ((PushTask) task).getMatchingRule());
            ((PushTaskTO) taskTO).setUnmatchingRule(((PushTask) task).getUnmatchingRule() == null ? UnmatchingRule.ASSIGN : ((PushTask) task).getUnmatchingRule());

            for (PushTaskAnyFilter filter : ((PushTask) task).getFilters()) {
                            ((PushTaskTO) taskTO).getFilters().put(filter.getAnyType().getKey(), filter.getFIQLCond());
            }

            break;

        case NOTIFICATION:
            if (((NotificationTask) task).isExecuted() && StringUtils.isBlank(taskTO.getLatestExecStatus())) {
                taskTO.setLatestExecStatus("[EXECUTED]");
            }
            break;
        default:
        }
    return taskTO;
}

// REFACTOR
@Override
public <T extends AbstractTaskTO> T getTaskTO2(final Task task, final TaskUtils taskUtils, final boolean details) {
    // 1. Inizializzazione e mappatura dei dati comuni
    T taskTO = taskUtils.newTaskTO();
    BeanUtils.copyProperties(task, taskTO, IGNORE_TASK_PROPERTIES);
    mapLatestExecutionDetails(task, taskTO);
    mapAllExecutions(task, taskTO, details);

    // 2. Dispatch al metodo helper corretto per mappare i dettagli specifici del tipo
    switch (taskUtils.getType()) {
        case PROPAGATION:
            mapPropagationTaskDetails((PropagationTaskTO) taskTO, task);
            break;
        case SCHEDULED:
            mapSchedTaskDetails((SchedTaskTO) taskTO, task);
            break;
        case PULL:
            mapPullTaskDetails((PullTaskTO) taskTO, task);
            break;
        case PUSH:
            mapPushTaskDetails((PushTaskTO) taskTO, task);
            break;
        case NOTIFICATION:
            mapNotificationTaskDetails(taskTO, task);
            break;
        default:
            // Nessuna azione specifica per altri tipi
            break;
    }
    return taskTO;
}

// =================================================================================
// METODI HELPER ESTRATTI (CON SINTASSI COMPATIBILE)
// =================================================================================

private void mapLatestExecutionDetails(final Task task, final AbstractTaskTO taskTO) {
    TaskExec latestExec = taskExecDAO.findLatestStarted(task);
    if (latestExec == null) {
        taskTO.setLatestExecStatus(StringUtils.EMPTY);
    } else {
        taskTO.setLatestExecStatus(latestExec.getStatus());
        taskTO.setStart(latestExec.getStart());
        taskTO.setEnd(latestExec.getEnd());
    }
}

private void mapAllExecutions(final Task task, final AbstractTaskTO taskTO, final boolean details) {
    if (details) {
        for (TaskExec execution : task.getExecs()) {
            if (execution != null) {
                taskTO.getExecutions().add(getExecTO(execution));
            }
        }
    }
}

private void mapPropagationTaskDetails(final PropagationTaskTO to, final Task task) {
    if (!(task instanceof PropagationTask)) {
        throw new IllegalArgumentException("Task non è di tipo PropagationTask: " + task.getClass().getName());
    }
    PropagationTask propagationTask = (PropagationTask) task; // CAST MANUALE
    to.setResource(propagationTask.getResource().getKey());
    to.setAttributes(propagationTask.getSerializedAttributes());
}

private void mapSchedTaskDetails(final SchedTaskTO to, final Task task) {
    if (!(task instanceof SchedTask)) {
        throw new IllegalArgumentException("Task non è di tipo SchedTask: " + task.getClass().getName());
    }
    setExecTime(to, task);
}

private void mapPullTaskDetails(final PullTaskTO to, final Task task) {
    if (!(task instanceof PullTask)) {
        throw new IllegalArgumentException("Task non è di tipo PullTask: " + task.getClass().getName());
    }
    PullTask pullTask = (PullTask) task; // CAST MANUALE
    setExecTime(to, task);
    to.setDestinationRealm(pullTask.getDestinatioRealm().getFullPath());
    to.setResource(pullTask.getResource().getKey());
    to.setMatchingRule(pullTask.getMatchingRule() == null ? MatchingRule.UPDATE : pullTask.getMatchingRule());
    to.setUnmatchingRule(pullTask.getUnmatchingRule() == null ? UnmatchingRule.PROVISION : pullTask.getUnmatchingRule());

    for (AnyTemplate template : pullTask.getTemplates()) {
        to.getTemplates().put(template.getAnyType().getKey(), template.get());
    }
}

private void mapPushTaskDetails(final PushTaskTO to, final Task task) {
    if (!(task instanceof PushTask)) {
        throw new IllegalArgumentException("Task non è di tipo PushTask: " + task.getClass().getName());
    }
    PushTask pushTask = (PushTask) task; // CAST MANUALE
    setExecTime(to, task);
    to.setResource(pushTask.getResource().getKey());
    to.setMatchingRule(pushTask.getMatchingRule() == null ? MatchingRule.LINK : pushTask.getMatchingRule());
    to.setUnmatchingRule(pushTask.getUnmatchingRule() == null ? UnmatchingRule.ASSIGN : pushTask.getUnmatchingRule());

    for (PushTaskAnyFilter filter : pushTask.getFilters()) {
        to.getFilters().put(filter.getAnyType().getKey(), filter.getFIQLCond());
    }
}

private void mapNotificationTaskDetails(final AbstractTaskTO to, final Task task) {
    if (task instanceof NotificationTask) {
        NotificationTask notificationTask = (NotificationTask) task; // CAST MANUALE
        if (notificationTask.isExecuted() && StringUtils.isBlank(to.getLatestExecStatus())) {
            to.setLatestExecStatus("[EXECUTED]");
        }
    }
}