package com.tikal.jenkins.plugins.multijob;


import hudson.model.*;
import hudson.model.Cause.UpstreamCause;
import jenkins.model.Jenkins;

import java.util.List;
import java.util.concurrent.Future;

public final class SubTask {
    final public AbstractProject subJob;
    final public PhaseJobsConfig phaseConfig;
    final public List<Action> actions;
    public Future<AbstractBuild> future;
    final public MultiJobBuild multiJobBuild;
    public Result result;
    private boolean cancel;
    private boolean isShouldTrigger;

    SubTask(AbstractProject subJob, PhaseJobsConfig phaseConfig, List<Action> actions, MultiJobBuild multiJobBuild, boolean isShouldTrigger) {
        this.subJob = subJob;
        this.phaseConfig = phaseConfig;
        this.actions = actions;
        this.multiJobBuild = multiJobBuild;
        this.cancel = false;
        this.isShouldTrigger = isShouldTrigger;
    }

    public boolean isShouldTrigger() {
        return isShouldTrigger;
    }

    public boolean isCancelled() {
        return cancel || future.isCancelled();
    }

    public void cancelJob(MultiJobBuilder multiJobBuilder) {
        this.cancel = true;
        multiJobBuilder.isCanceled = true;
        if (future != null) {
            Queue queue = Jenkins.getInstance().getQueue();
            synchronized (queue) {
                List<Queue.Item> items = queue.getItems(subJob);
                for (Queue.Item item : items) {
                    if (item.getFuture() == Future.class.cast(future))
                        queue.cancel(item);
                }
            }
        }
    }

    public void generateFuture() {
        this.future = subJob.scheduleBuild2(subJob.getQuietPeriod(),
                                            new UpstreamCause((Run) multiJobBuild),
                                            actions.toArray(new Action[actions.size()]));
    }
}