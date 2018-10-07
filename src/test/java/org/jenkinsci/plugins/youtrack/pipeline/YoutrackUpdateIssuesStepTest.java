package org.jenkinsci.plugins.youtrack.pipeline;

import hudson.model.Result;
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

import java.io.File;

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
                        "        ytCreateIssue()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );
        WorkflowRun b = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        j.assertLogContains("No YouTrack site configured", b);
        j.assertLogContains("SUCCESS", b);
    }
}