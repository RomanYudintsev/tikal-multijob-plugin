package com.tikal.jenkins.plugins.multijob;

import com.tikal.jenkins.plugins.multijob.views.MultiJobView;
import hudson.Extension;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.RunListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.scm.PollingResult;
import hudson.util.AlternativeUiTextProvider;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public class MultiJobProject extends Project<MultiJobProject, MultiJobBuild>
		implements TopLevelItem {

        private volatile boolean pollSubjobs = false;
        private volatile String resumeEnvVars = null;

	@SuppressWarnings("rawtypes")
	private MultiJobProject(ItemGroup parent, String name) {
		super(parent, name);
	}

	public MultiJobProject(Hudson parent, String name) {
		super(parent, name);
	}

	@Override
	protected Class<MultiJobBuild> getBuildClass() {
		return MultiJobBuild.class;
	}

	@Override
	public String getPronoun() {
		return AlternativeUiTextProvider.get(PRONOUN, this, getDescriptor().getDisplayName());
	}

	public DescriptorImpl getDescriptor() {
		return DESCRIPTOR;
	}

	@Extension(ordinal = 1000)
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends AbstractProjectDescriptor {
		public String getDisplayName() {
			return "MultiJob Project";
		}

		@SuppressWarnings("rawtypes")
		public MultiJobProject newInstance(ItemGroup itemGroup, String name) {
			return new MultiJobProject(itemGroup, name);
		}
	}
    @Extension(ordinal = 1001)
    public static final QueueTaskDispatcherImpl QUEUETASKDISPATCHERIMPL = new QueueTaskDispatcherImpl();

    public static final class QueueTaskDispatcherImpl extends QueueTaskDispatcher {
        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            try {
                MultiJobBuilder.MultiJobAction action = item.getAction(MultiJobBuilder.MultiJobAction.class);
                if (action != null) {
                    if (action.builder.isCanceled)
                    {
                        Queue queue = Jenkins.getInstance().getQueue();
                        queue.cancel(item);
                        return new CauseOfBlockage() {
                            @Override
                            public String getShortDescription() {
                                return "some builds aborted";
                            }
                        };
                    }
                }
            } catch (Exception e ) {}
            return super.canTake(node, item);
        }
    }

    @Extension(ordinal = 1002)
    public static final RunListenerImpl RUNLISTENERIMPL = new RunListenerImpl();

    public static final class RunListenerImpl extends RunListener<AbstractBuild> {
        @Override
        public void onCompleted(AbstractBuild item, TaskListener listener)
        {
            try {
                if (item.getResult() != Result.SUCCESS)
                {
                    MultiJobBuilder.MultiJobAction action = item.getAction(MultiJobBuilder.MultiJobAction.class);
                    if (action != null) {
                        action.builder.isCanceled = true;
                    }
                }
            }
            catch (Exception e) {}
        }
    }

    @Override
	protected void buildDependencyGraph(DependencyGraph graph) {
		super.buildDependencyGraph(graph);
	}

	public boolean isTopMost() {
		return getUpstreamProjects().size() == 0;
	}

	public MultiJobView getView() {
		return new MultiJobView("");
	}

	public String getRootUrl() {
		return Jenkins.getInstance().getRootUrl();
	}

        @Override
        public PollingResult poll(TaskListener listener) {
            //Preserve default behavior unless specified otherwise
            if (!getPollSubjobs()) {
                return super.poll(listener);
            }

            PollingResult result = super.poll(listener);
            //If multijob has changes, save the effort of checking children
            if (result.hasChanges()) {
                return result;
            }
            List<AbstractProject> downProjs = getDownstreamProjects();
            PollingResult tmpResult = new PollingResult(PollingResult.Change.NONE);
            //return when we get changes to save resources
            //If we don't get changes, return the most significant result
            for (AbstractProject downProj : downProjs) {
                tmpResult = downProj.poll(listener);
                if (result.change.ordinal() < tmpResult.change.ordinal()) {
                    result = tmpResult;
                    if (result.hasChanges()) {
                        return result;
                    }
                }
            }
            return result;
        }

        public boolean getPollSubjobs() {
            return pollSubjobs;
        }

        public void setPollSubjobs(boolean poll) {
            pollSubjobs = poll;
        }

        public String getResumeEnvVars() {
			return resumeEnvVars;
		}

        public void setResumeEnvVars(String resumeEnvVars) {
			this.resumeEnvVars = resumeEnvVars;
		}

        public boolean getCheckResumeEnvVars() {
        	return !StringUtils.isBlank(resumeEnvVars);
        }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        String k = "multijob";
        if (json.has(k)) {
            json = json.getJSONObject(k);
            k = "pollSubjobs";
            if (json.has(k)) {
                setPollSubjobs(json.optBoolean(k));
            }
            String resumeEnvVars = null;
            k = "resumeEnvVars";
            if (json.has(k)) {
            	json = json.getJSONObject(k);
                if (json.has(k)) {
                	resumeEnvVars = json.getString(k);
                }
            }
            setResumeEnvVars(resumeEnvVars);
        }
    }
}
