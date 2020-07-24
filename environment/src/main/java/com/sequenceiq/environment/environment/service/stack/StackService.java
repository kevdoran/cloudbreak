package com.sequenceiq.environment.environment.service.stack;


import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.StackV4Endpoint;
import com.sequenceiq.cloudbreak.cloud.scheduler.PollGroup;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.common.exception.WebApplicationExceptionMessageExtractor;
import com.sequenceiq.environment.environment.domain.Environment;
import com.sequenceiq.environment.environment.flow.config.update.config.EnvStackConfigUpdatesFlowConfig;
import com.sequenceiq.environment.store.EnvironmentInMemoryStateStore;
import com.sequenceiq.flow.domain.FlowLog;
import com.sequenceiq.flow.service.FlowCancelService;
import com.sequenceiq.flow.service.flowlog.FlowLogDBService;
import javax.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackService.class);

    private final StackV4Endpoint stackV4Endpoint;

    private final FlowCancelService flowCancelService;

    private final FlowLogDBService flowLogDBService;

    private final WebApplicationExceptionMessageExtractor messageExtractor;

    public StackService(
        StackV4Endpoint stackV4Endpoint,
        FlowCancelService flowCancelService,
        FlowLogDBService flowLogDBService,
        WebApplicationExceptionMessageExtractor messageExtractor) {
        this.stackV4Endpoint = stackV4Endpoint;
        this.flowCancelService = flowCancelService;
        this.flowLogDBService = flowLogDBService;
        this.messageExtractor = messageExtractor;
    }

    public void triggerConfigUpdateForStack(String stackName) {
        try {
            stackV4Endpoint.updatePillarConfigurationByName(0L, stackName);
        } catch (WebApplicationException wae) {
            LOGGER.info(String
                .format("Unable to start config update for stack %s.  Message is %s", stackName,
                    messageExtractor.getErrorMessage(wae)));
            throw wae;
        }
    }

    public void cancelRunningStackConfigUpdates(Environment environment) {
        try {
            FlowLog flowLog = flowLogDBService.getLastFlowLogByResourceCrnOrName(environment.getResourceCrn());
            if (!flowLog.getFinalized() && flowLog.getFlowType() != null
                && EnvStackConfigUpdatesFlowConfig.class.equals(flowLog.getFlowType())) {
                LOGGER.info("Canceling running Stack config update flow for environment {}", environment.getResourceCrn());
                EnvironmentInMemoryStateStore.put(environment.getId(), PollGroup.CANCELLED);
                flowCancelService.cancelFlowSilently(flowLog);
            } else {
                LOGGER.debug("No running Stack config update flow to cancel");
            }
        } catch (NotFoundException nfe) {
            LOGGER.info("No flow logs found for environment {}",  environment.getResourceCrn());
        }
    }
}
