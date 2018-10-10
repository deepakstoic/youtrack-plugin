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
    private hudson.model.User jenkinsUser;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(ytProperties.getProject()).thenReturn("TEST");
        when(ytProperties.getDescriptor()).thenReturn(YouTrackProjectProperty.DESCRIPTOR);
        when(ytProperties.getSite()).thenReturn(site);
        when(site.createServer()).thenReturn(server);
        when(site.getUsername()).thenReturn("test");
        when(site.getPassword()).thenReturn("test");
        when(site.getUser(server)).thenReturn(user);
        when(site.getName()).thenReturn("YouTrackTestSite");
        when(server.login("test", "test")).thenReturn(user);

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