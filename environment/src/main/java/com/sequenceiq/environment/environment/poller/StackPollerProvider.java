package com.sequenceiq.environment.environment.poller;

import com.dyngr.core.AttemptMaker;
import com.dyngr.core.AttemptResult;
import com.dyngr.core.AttemptResults;
import com.dyngr.core.AttemptState;
import com.sequenceiq.environment.environment.service.stack.StackService;
import com.sequenceiq.flow.core.FlowConstants;
import com.sequenceiq.flow.domain.FlowLog;
import com.sequenceiq.flow.service.flowlog.FlowLogDBService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StackPollerProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackPollerProvider.class);

    private final StackService stackService;

    private final FlowLogDBService flowLogDBService;

    public StackPollerProvider(
        StackService stackService,
        FlowLogDBService flowLogDBService) {
        this.stackService = stackService;
        this.flowLogDBService = flowLogDBService;
    }

    public AttemptMaker<Void> stackUpdateConfigPoller(List<String> stackNames, Long envId, String flowId) {
        List<String> mutableNamesList = new ArrayList<>(stackNames);
        return () -> {
            Optional<FlowLog> flowLog = flowLogDBService.getLastFlowLog(flowId);
            if (flowLog.isPresent() && flowLog.get().getCurrentState().equals(FlowConstants.CANCELLED_STATE)) {
                return AttemptResults.finishWith(null);
            }
            List<String> remaining = new ArrayList<>();
            List<AttemptResult<Void>> results = collectStackUpdateConfigResults(mutableNamesList,
                remaining, envId);
            mutableNamesList.retainAll(remaining);
            return evaluateResult(results);
        };
    }

    private List<AttemptResult<Void>> collectStackUpdateConfigResults(List<String> stackNames,
        List<String> remaining, Long envId) {
        return stackNames.stream()
            .map(stackName -> fetchStackUpdateConfigResults(remaining, stackName))
            .collect(Collectors.toList());
    }

    private AttemptResult<Void> fetchStackUpdateConfigResults(List<String> remainingStacks, String stackName) {
        try {
            stackService.triggerConfigUpdateForStack(stackName);
            return AttemptResults.finishWith(null);
        } catch (BadRequestException e) {
            remainingStacks.add(stackName);
            return AttemptResults.justContinue();
        } catch (Exception e) {
            return AttemptResults.breakFor(e);
        }
    }

    AttemptResult<Void> evaluateResult(List<AttemptResult<Void>> results) {
        Optional<AttemptResult<Void>> error = results.stream()
            .filter(it -> it.getState() == AttemptState.BREAK).findFirst();
        if (error.isPresent()) {
            return error.get();
        }

        Predicate<AttemptResult<Void>> stateCheck = it -> it.getState() == AttemptState.CONTINUE;
        if (results.stream().anyMatch(stateCheck)) {
            return AttemptResults.justContinue();
        }
        return AttemptResults.finishWith(null);
    }
}
