package org.jenkinsci.plugins.youtrack.pipeline;

import hudson.model.Action;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.youtrack.Command;
import org.jenkinsci.plugins.youtrack.YouTrackProjectProperty;
import org.jenkinsci.plugins.youtrack.YouTrackSite;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.*;

public class YoutrackUpdateIssuesStepTest {
    @Rule
    public transient JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    @Mock()
    private YouTrackProjectProperty ytProperties;

    @Mock
    private YouTrackSite site;

    @Mock
    private YouTrackServer server;

    @Mock
    private User user;

    @Mock
    private Issue issue1;

    @Mock
    private Issue issue2;

    @Mock
    private Issue issue3;

    @Mock
    private hudson.model.User jenkinsUser;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(ytProperties.getProject()).thenReturn("TEST");
        when(ytProperties.getDescriptor()).thenReturn(YouTrackProjectProperty.DESCRIPTOR);
        when(ytProperties.getSite()).thenReturn(site);
        when(ytProperties.getFixedValues()).thenReturn("Fixed,Done,Verified,Won't Fix");
        when(site.createServer()).thenReturn(server);
        when(site.getUsername()).thenReturn("test");
        when(site.getPassword()).thenReturn("test");
        when(site.getUser(server)).thenReturn(user);
        when(site.getName()).thenReturn("YouTrackTestSite");
        when(server.login("test", "test")).thenReturn(user);

        when(issue1.getId()).thenReturn("TEST-1");
        when(issue1.getState()).thenReturn("Fixed");

        when(issue2.getId()).thenReturn("TEST-2");
        when(issue2.getState()).thenReturn("In Progress");

        when(issue3.getId()).thenReturn("TEST-3");
        when(issue3.getState()).thenReturn("Won't Fix");

        Mockito.reset(user);
    }

    @Test
    public void testNoSiteSetup() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTestNoSite");
        job.removeProperty(YouTrackProjectProperty.class);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );
        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("No YouTrack site configured", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testWithSiteSetupNoChangeSets() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackDefaultSite");
        job.addProperty(ytProperties);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(true).when(user).isLoggedIn();

        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("No issues to update", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testWithSiteSetupNoIssues() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackDefaultSite");
        job.addProperty(ytProperties);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        List<ChangeLogSet.Entry> entries = new ArrayList<>();
        entries.add(new DummyEntry(jenkinsUser, "test1", "commit 1"));

        doReturn(true).when(user).isLoggedIn();
        doReturn(entries).when(site).getChangeLogEntries(any());

        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("Looking for issues about changeset: test1", b);
        j.assertLogContains("No issues to update", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testWithSiteAndIssues() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackDefaultSite");
        job.addProperty(ytProperties);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        List<ChangeLogSet.Entry> entries = new ArrayList<>();
        entries.add(new DummyEntry(jenkinsUser, "test1", "commit 1"));
        entries.add(new DummyEntry(jenkinsUser, "test2", "commit 2 ^TEST-2"));

        List<Issue> issues = new ArrayList<>();
        issues.add(issue1);
        issues.add(issue2);
        issues.add(issue3);

        List<Issue> issues2 = new ArrayList<>();
        issues2.add(issue2);

        doReturn(true).when(user).isLoggedIn();
        doReturn(entries).when(site).getChangeLogEntries(any());
        doReturn(issues).when(server).search(user, "vcs changes: test1");
        doReturn(issues2).when(server).search(user, "vcs changes: test2");

        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("Looking for issues about changeset: test1", b);
        j.assertLogContains("Looking for issues about changeset: test2", b);
        j.assertLogContains("- found issue: TEST-1 [RESOLVED]", b);
        j.assertLogContains("- found issue: TEST-2", b);
        j.assertLogContains("- found issue: TEST-3 [RESOLVED]", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testWithCommands() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackDefaultSite");
        job.addProperty(ytProperties);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues commands: ['for iceseyes', '+1', 'priority major add tag fix this week']\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        List<ChangeLogSet.Entry> entries = new ArrayList<>();
        entries.add(new DummyEntry(jenkinsUser, "test1", "commit 1"));
        entries.add(new DummyEntry(jenkinsUser, "test2", "commit 2 ^TEST-2"));

        List<Issue> issues = new ArrayList<>();
        issues.add(issue1);
        issues.add(issue2);
        issues.add(issue3);

        List<Issue> issues2 = new ArrayList<>();
        issues2.add(issue2);

        doReturn(true).when(user).isLoggedIn();
        doReturn(entries).when(site).getChangeLogEntries(any());
        doReturn(issues).when(server).search(user, "vcs changes: test1");
        doReturn(issues2).when(server).search(user, "vcs changes: test2");
        doReturn(true).when(site).isCommandsEnabled();

        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("Looking for issues about changeset: test1", b);
        j.assertLogContains("Looking for issues about changeset: test2", b);
        j.assertLogContains("Applying command: for iceseyes, to issue TEST-1", b);
        j.assertLogContains("Applying command: for iceseyes, to issue TEST-2", b);
        j.assertLogContains("Applying command: for iceseyes, to issue TEST-3", b);
        j.assertLogContains("Applying command: +1, to issue TEST-1", b);
        j.assertLogContains("Applying command: +1, to issue TEST-2", b);
        j.assertLogContains("Applying command: +1, to issue TEST-3", b);
        j.assertLogContains("Applying command: priority major add tag fix this week, to issue TEST-1", b);
        j.assertLogContains("Applying command: priority major add tag fix this week, to issue TEST-2", b);
        j.assertLogContains("Applying command: priority major add tag fix this week, to issue TEST-3", b);
        j.assertLogContains("SUCCESS", b);

        doReturn(false).when(site).isCommandsEnabled();
    }

    @Test
    public void testWithCommand() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackDefaultSite");
        job.addProperty(ytProperties);
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n" +
                        "  stages {\n" +
                        "    stage(\"Notify\") {\n" +
                        "      steps {\n" +
                        "        ytUpdateIssues commands: [\"for ${BUILD_NUMBER}\"]\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        List<ChangeLogSet.Entry> entries = new ArrayList<>();
        entries.add(new DummyEntry(jenkinsUser, "test2", "commit 2 ^TEST-2"));

        List<Issue> issues2 = new ArrayList<>();
        issues2.add(issue2);

        doReturn(true).when(user).isLoggedIn();
        doReturn(entries).when(site).getChangeLogEntries(any());
        doReturn(issues2).when(server).search(user, "vcs changes: test2");
        doReturn(true).when(site).isCommandsEnabled();

        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("Looking for issues about changeset: test2", b);
        j.assertLogContains("Applying command: for 1, to issue TEST-2", b);
        j.assertLogContains("SUCCESS", b);

        doReturn(false).when(site).isCommandsEnabled();
    }
}

class DummyEntry extends ChangeLogSet.Entry {
    private hudson.model.User user;
    private String cid;
    private String msg;

    public DummyEntry(hudson.model.User user, String cid, String msg) {
        this.user = user;
        this.cid =  cid;
        this.msg = msg;
    }

    @Override
    public String getCommitId() {
        return cid;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    @Override
    public hudson.model.User getAuthor() {
        return user;
    }

    @Override
    public Collection<String> getAffectedPaths() {
        return new ArrayList<>();
    }
}