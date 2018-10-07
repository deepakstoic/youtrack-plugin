package org.jenkinsci.plugins.youtrack.pipeline;

import com.google.common.collect.ArrayListMultimap;
import groovy.lang.Binding;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jenkinsci.plugins.youtrack.*;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class YouTrackUpdateIssuesStep extends Step {
    @Getter @DataBoundSetter String project;
    @Getter private String description = DescriptorImpl.DEFAULT_DESCRIPTION;
    @Getter @DataBoundSetter boolean addComment = true;
    @Getter @DataBoundSetter boolean addCommitSummary = true;
    @Getter @DataBoundSetter String buildName = DescriptorImpl.DEFAULT_BUILDNAME;
    @Getter @DataBoundSetter String fixedInBuildsField = DescriptorImpl.DEFAULT_FIXED_IN_BUILD_FIELD;
    @Getter @DataBoundSetter List<String> commands;

    @DataBoundConstructor
    public YouTrackUpdateIssuesStep() {}

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
        private transient YouTrackUpdateIssuesStep step;
        private transient WorkflowRun run;
        private transient TaskListener listener;
        private transient YouTrackSite site;
        private transient YouTrackServer server;
        private transient User user;
        private transient EnvVars env;
        private transient YouTrackProjectProperty ytpp;
        private transient List<ChangeLogSet<? extends ChangeLogSet.Entry>> changelogs;
        private static final Logger LOGGER = Logger.getLogger(YouTrackUpdateIssuesStep.class.getName());

        protected Execution(YouTrackUpdateIssuesStep step, @Nonnull StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
            run = context.get(WorkflowRun.class);
            changelogs = run.getChangeSets();
            listener = context.get(TaskListener.class);
            env = context.get(EnvVars.class);
            site = YouTrackSite.get(run.getParent());
            if (site != null) {
                server = site.createServer();
            } else {
                server = null;
            }
            user = null;
            ytpp = run.getParent().getProperty(YouTrackProjectProperty.class);
        }

        private User getUser() {
            if (user == null)
                user = site.getUser(server);

            return user;
        }

        @Override
        protected Void run() throws Exception {
            User user = getUser();
            if (user == null || !user.isLoggedIn()) {
                listener.getLogger().append("FAILED: log in with set YouTrack user");
                site.failed(run);
            }

            run.addAction(new YouTrackIssueAction(run.getParent()));

            List<ChangeLogSet.Entry> changeLogEntries = new ArrayList<ChangeLogSet.Entry>();
            for (ChangeLogSet cs : changelogs) {
                Iterator<? extends ChangeLogSet.Entry> changeLogIterator = cs.iterator();
                while (changeLogIterator.hasNext())
                    changeLogEntries.add(changeLogIterator.next());
            }

            YouTrackCommandAction commandAction = new YouTrackCommandAction(run);

            List<Issue> fixedIssues = new ArrayList<>();
            ArrayListMultimap<Issue, ChangeLogSet.Entry> relatedChanges = ArrayListMultimap.create();
            for (ChangeLogSet.Entry entry : changeLogEntries) {
                List<Issue> issuesFromCommit = server.search(getUser(), "vcs changes: " + entry.getCommitId());
                for (Issue issue : issuesFromCommit) {
                    relatedChanges.put(issue, entry);
                    if(isFixed(issue)) fixedIssues.add(issue);
                }
            }

            for (Issue relatedIssue : relatedChanges.keySet()) {
                List<ChangeLogSet.Entry> entries = relatedChanges.get(relatedIssue);

                if(step.isAddComment() && site.isCommentEnabled()) {
                    List<Command> commands = addComment(relatedIssue, entries);
                    for (Command command : commands) {
                        commandAction.addCommand(command);
                    }
                }

                if(site.isCommandsEnabled()) {
                    for (String cmd_str : step.getCommands()) {
                        commandAction.addCommand(server.applyCommand(
                                site.getName(), getUser(), relatedIssue,
                                cmd_str, env.expand("By build ${BUILD_TAG}."),
                                null, null, site.isSilentCommands()));
                    }
                }

                if(step.getFixedInBuildsField()!=null) {
                    String buildBundleName = server.getBuildBundleNameForField(
                            getUser(), getProject(),
                            env.expand(step.getFixedInBuildsField()));
                    server.addBuildToBundle(site.getName(), getUser(),
                            buildBundleName, env.expand(step.getBuildName()));
                }
            }


            return null;
        }

        private String getProject() {
            if(step.getProject()!=null)
                return step.getProject();

            return ytpp.getProject();
        }

        private boolean isFixed(Issue issue) {
            String[] states = ytpp.getFixedValues().split(",");
            for (String state: states) {
                if(issue.getState() == state)
                    return true;
            }

            return false;
        }

        private List<Command> addComment(Issue relatedIssue, List<ChangeLogSet.Entry> entries) {
            List<Command> commands = new ArrayList<Command>();

            String commentText = "";
            StringBuilder stringBuilder = new StringBuilder("Related build: " + run.getAbsoluteUrl());
            stringBuilder.append("\nBuild Result: ").append(run.getResult());

            if(step.isAddCommitSummary()) {
                stringBuilder.append("\nVCS changesets summary:");
                for (ChangeLogSet.Entry entry : entries) {
                    stringBuilder.append("\nSHA: ").append(entry.getCommitId());
                    stringBuilder.append(":\n").append(entry.getMsg());
                    stringBuilder.append("by ").append(entry.getAuthor()).append("\n");
                }
            }

            if(step.getDescription()!=null)
                stringBuilder
                        .append("\n")
                        .append(env.expand(step.getDescription()));

            commentText = stringBuilder.toString();

            Command comment;
            comment = server.comment(site.getName(), user,
                    relatedIssue, commentText,
                    site.getLinkVisibility(),
                    site.isSilentLinks());

            if (comment != null) {
                commands.add(comment);
                if (comment.getStatus() == Command.Status.OK) {
                    listener.getLogger().println("Commented on " + relatedIssue.getId());
                } else {
                    listener.getLogger().println("FAILED: Commented on " + relatedIssue.getId());
                    site.failed(run);
                }
            }

            return commands;
        }

    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        public static final String DEFAULT_DESCRIPTION = "Automatic issue created by Jenkins for build ${BUILD_TAG}: ${BUILD_URL}";
        public static final String DEFAULT_FIXED_IN_BUILD_FIELD = "Fixed in builds";
        public static final String DEFAULT_BUILDNAME = "${BUILD_TAG}";

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
            return "ytUpdateIssues";
        }
    }

}
