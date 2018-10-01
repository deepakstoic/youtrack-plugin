package org.jenkinsci.plugins.youtrack.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import lombok.Getter;
import lombok.Setter;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jenkinsci.plugins.youtrack.Command;
import org.jenkinsci.plugins.youtrack.YouTrackCommandAction;
import org.jenkinsci.plugins.youtrack.YouTrackSite;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class YouTrackCreateIssueStep extends Step {
    @Getter @Setter private String project;
    @Getter private String summary = DescriptorImpl.DEFAULT_SUMMARY;
    @Getter private String description = DescriptorImpl.DEFAULT_DESCRIPTION;
    @Getter @Setter private String visibility;
    @Getter @Setter private String command;
    @Getter @DataBoundSetter private boolean attachBuildLog = true;

    @DataBoundConstructor
    public YouTrackCreateIssueStep() {}

    @DataBoundSetter
    public void setSummary(String summary) {
        if(!summary.equals(DescriptorImpl.DEFAULT_SUMMARY))
            this.summary = summary;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        if(!description.equals(DescriptorImpl.DEFAULT_DESCRIPTION))
            this.description = description;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private transient YouTrackCreateIssueStep step;
        private transient Run run;
        private transient TaskListener listener;
        private transient YouTrackSite site;
        private transient YouTrackServer server;
        private transient User user;

        protected Execution(YouTrackCreateIssueStep step, @Nonnull StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
            run = context.get(Run.class);
            listener = context.get(TaskListener.class);
            site = YouTrackSite.get(run.getParent());
            if (site != null) {
                server = site.createServer();
            } else {
                server = null;
            }
            user = null;
        }

        private User getUser() {
            if (user == null)
                user = site.getUser(server);

            return user;
        }

        private Command getCommand(File buildLog) {
            String project = step.getProject();
            if (project == null || project.isEmpty())
                project = site.getProject();

            return server.createIssue(site.getName(), getUser(), project,
                    step.getSummary(), step.getDescription(), step.getCommand(), buildLog);
        }

        private void runAction(File buildLog) {
            YouTrackCommandAction youTrackCommandAction = run.getAction(YouTrackCommandAction.class);
            if (youTrackCommandAction == null) {
                youTrackCommandAction = new YouTrackCommandAction(run);
                run.addAction(youTrackCommandAction);
            }
            Command issue = getCommand(buildLog);
            youTrackCommandAction.addCommand(issue);

            listener.getLogger().println("Created new YouTrack issue " + issue.getIssueId());
        }

        @Override
        protected Void run() throws Exception {
            if (server == null) {
                listener.getLogger().println("No YouTrack site configured");
            } else {
                User user = getUser();
                if (user == null || !user.isLoggedIn()) {
                    listener.getLogger().println("Could not login user to YouTrack");
                    throw new IllegalArgumentException(
                            "Error in YouTrack Settings. Please review settings in Jenkins configuration.");
                }

                File buildLog = null;
                if (step.isAttachBuildLog()) {
                    buildLog = run.getLogFile();
                }

                runAction(buildLog);
            }

            return null;
        }

    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        public static final String DEFAULT_SUMMARY = "Build ${BUILD_TAG} on Jenkins";
        public static final String DEFAULT_DESCRIPTION = "Automatic issue created by Jenkins for build ${BUILD_TAG}: ${BUILD_URL}";

        @Override
        public String getDisplayName() { return Messages.YouTrackCreateIssue_DisplayName(); }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet(Arrays.asList(
                    Job.class, TaskListener.class
            ));
        }

        @Override
        public String getFunctionName() {
            return "ytCreateIssue";
        }
    }

}
