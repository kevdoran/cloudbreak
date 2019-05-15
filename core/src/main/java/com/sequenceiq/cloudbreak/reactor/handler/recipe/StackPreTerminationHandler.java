package com.sequenceiq.cloudbreak.reactor.handler.recipe;

import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.recipe.StackPreTerminationRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.recipe.StackPreTerminationSuccess;
import com.sequenceiq.flow.handler.EventHandler;
import com.sequenceiq.cloudbreak.service.cluster.flow.PreTerminationStateExecutor;
import com.sequenceiq.cloudbreak.service.cluster.flow.recipe.RecipeEngine;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.StackService;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class StackPreTerminationHandler implements EventHandler<StackPreTerminationRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackPreTerminationHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private StackService stackService;

    @Inject
    private RecipeEngine recipeEngine;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private PreTerminationStateExecutor preTerminationStateExecutor;

    @Override
    public void accept(Event<StackPreTerminationRequest> requestEvent) {
        StackPreTerminationRequest request = requestEvent.getData();
        Stack stack = stackService.getByIdWithListsInTransaction(request.getResourceId());
        try {
            Cluster cluster = stack.getCluster();
            if (cluster != null) {
                Set<HostGroup> hostGroups = hostGroupService.getByCluster(cluster.getId());
                recipeEngine.executePreTerminationRecipes(stack, hostGroups);
                preTerminationStateExecutor.runPreteraminationTasks(stack);
            }
        } catch (Exception ex) {
            LOGGER.info("Pre-termination failed: {}", ex.getMessage(), ex);
        }

        Selectable result = new StackPreTerminationSuccess(stack.getId());
        eventBus.notify(result.selector(), new Event<>(requestEvent.getHeaders(), result));
    }

    @Override
    public String selector() {
        return EventSelectorUtil.selector(StackPreTerminationRequest.class);
    }
}
