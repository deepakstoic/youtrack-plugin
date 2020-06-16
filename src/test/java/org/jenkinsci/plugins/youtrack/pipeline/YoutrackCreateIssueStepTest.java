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

public class YoutrackCreateIssueStepTest {
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

    @Test
    public void testWithSiteSetupLoginFailed() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTest");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n"+
                        "  stages {\n"+
                        "    stage(\"Notify\") {\n"+
                        "      steps {\n"+
                        "        ytCreateIssue()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(false).when(user).isLoggedIn();

        job.addProperty(ytProperties);
        WorkflowRun b = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("Could not login user to YouTrack", b);
        j.assertLogContains("FAILURE", b);
    }

    @Test
    public void testDefaultIssue() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTest");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n"+
                        "  stages {\n"+
                        "    stage(\"Notify\") {\n"+
                        "      steps {\n"+
                        "        ytCreateIssue()\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(true).when(user).isLoggedIn();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Command issue = new Command();
                issue.setIssueId("TEST-254");
                issue.setStatus(Command.Status.FAILED);
                for(Object o : invocation.getArguments())
                    System.out.println("Invalid Arguments: " + o);
                return issue;
            }
        }).when(server).createIssue(
                anyString(), any(User.class),
                anyString(), anyString(), anyString(), anyString(),
                any(File.class));


        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Command issue = new Command();
                issue.setIssueId("TEST-254");
                issue.setStatus(Command.Status.OK);

                return issue;
            }
        }).when(server).createIssue(
                eq("YouTrackTestSite"), any(User.class),
                eq("TEST"),
                eq("Build jenkins-YouTrackTest-1 on Jenkins"),
                eq("Automatic issue created by Jenkins for build jenkins-YouTrackTest-1: " + job.getAbsoluteUrl() + "1/"),
                eq(null),
                any(File.class));

        job.addProperty(ytProperties);
        WorkflowRun b = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        j.assertLogContains("Created new YouTrack issue TEST-254", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testCustomIssue() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTest");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n"+
                        "  stages {\n"+
                        "    stage(\"Notify\") {\n"+
                        "      steps {\n"+
                        "        ytCreateIssue(\n" +
                        "           summary: \"This is not an issue from build ${BUILD_NUMBER}: ${currentBuild.currentResult}\"," +
                        "           description: \"This is not an issue test ${BUILD_TAG}\")\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(true).when(user).isLoggedIn();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Command issue = new Command();
                issue.setIssueId("TEST-254");
                issue.setStatus(Command.Status.OK);

                return issue;
            }
        }).when(server).createIssue(
                eq("YouTrackTestSite"), any(User.class),
                eq("TEST"),
                eq("This is not an issue from build 1: SUCCESS"),
                eq("This is not an issue test jenkins-YouTrackTest-1"),
                eq(null),
                (File) notNull());

        job.addProperty(ytProperties);
        WorkflowRun b = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        j.assertLogContains("Created new YouTrack issue TEST-254", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testFullCustomIssue() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTest");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n"+
                        "  stages {\n"+
                        "    stage(\"Notify\") {\n"+
                        "      steps {\n"+
                        "        ytCreateIssue(\n" +
                        "           project: \"${JOB_NAME}\",\n" +
                        "           summary: \"This is not an issue from build ${BUILD_NUMBER}: ${currentBuild.currentResult}\"," +
                        "           description: \"This is not an issue test ${BUILD_TAG}\",\n" +
                        "           command: 'for ${JOB_NAME}'," +
                        "           attachBuildLog: false)" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(true).when(user).isLoggedIn();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Command issue = new Command();
                issue.setIssueId("TEST-254");
                issue.setStatus(Command.Status.OK);

                return issue;
            }
        }).when(server).createIssue(
                eq("YouTrackTestSite"), any(User.class),
                eq("YouTrackTest"),
                eq("This is not an issue from build 1: SUCCESS"),
                eq("This is not an issue test jenkins-YouTrackTest-1"),
                eq("for YouTrackTest"),
                eq(null));

        job.addProperty(ytProperties);
        WorkflowRun b = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        j.assertLogContains("Created new YouTrack issue TEST-254", b);
        j.assertLogContains("SUCCESS", b);
    }

    @Test
    public void testFullCustomIssueOnSuccess() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "YouTrackTest");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "  agent any\n"+
                        "  stages {\n"+
                        "    stage(\"Notify\") {\n"+
                        "      steps {\n"+
                        "        echo 'test'\n"+
                        "      }\n"+
                        "      post {\n"+
                        "       success {\n"+
                        "        ytCreateIssue(\n" +
                        "           project: \"${JOB_NAME}\",\n" +
                        "           summary: \"This is not an issue from build ${BUILD_NUMBER}: ${currentBuild.currentResult}\"," +
                        "           description: \"This is not an issue test ${BUILD_TAG}\",\n" +
                        "           command: 'for ${JOB_NAME}'," +
                        "           attachBuildLog: false)" +
                        "       }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", true)
        );

        doReturn(true).when(user).isLoggedIn();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Command issue = new Command();
                issue.setIssueId("TEST-254");
                issue.setStatus(Command.Status.OK);

                return issue;
            }
        }).when(server).createIssue(
                eq("YouTrackTestSite"), any(User.class),
                eq("YouTrackTest"),
                eq("This is not an issue from build 1: SUCCESS"),
                eq("This is not an issue test jenkins-YouTrackTest-1"),
                eq("for YouTrackTest"),
                eq(null));

        job.addProperty(ytProperties);
        WorkflowRun b = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        j.assertLogContains("Created new YouTrack issue TEST-254", b);
        j.assertLogContains("SUCCESS", b);
    }

}