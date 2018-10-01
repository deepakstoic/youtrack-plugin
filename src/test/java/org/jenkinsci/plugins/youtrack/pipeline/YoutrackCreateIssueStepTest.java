package org.jenkinsci.plugins.youtrack.pipeline;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.*;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.youtrack.Command;
import org.jenkinsci.plugins.youtrack.YouTrackSite;
import org.jenkinsci.plugins.youtrack.YoutrackCreateIssueOnBuildFailure;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class YoutrackCreateIssueStepTest {

    @Test
    public void testNoSiteSetup() throws Exception {
        YouTrackCreateIssueStep step = new YouTrackCreateIssueStep();
        StepContext context = mock(StepContext.class);
        SynchronousNonBlockingStepExecution e = (SynchronousNonBlockingStepExecution) step.start(context);
        e.start();
    }
/*
    @Test
    public void testSuccessFullBuild() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);

        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("PROJECT", "SUMMARY", "DESCRIPTION", YoutrackCreateIssueOnBuildFailure.FAILURE, null, null,false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.SUCCESS);

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);

        youtrackCreateIssueOnBuildFailure.perform(build,launcher,buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo(""));
    }

    @Test
    public void testUnstableOnlyCreateOnFailureBuild() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);

        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("PROJECT", "SUMMARY", "DESCRIPTION", YoutrackCreateIssueOnBuildFailure.FAILURE, null, null,false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.UNSTABLE);

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);
        doReturn(new YouTrackServer(youTrackSite.getUrl())).when(youtrackCreateIssueOnBuildFailure).getYouTrackServer(youTrackSite);

        youtrackCreateIssueOnBuildFailure.perform(build,launcher,buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo(""));
    }

    @Test
    public void testNoConnectionToServer() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);

        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("PROJECT", "SUMMARY", "DESCRIPTION", YoutrackCreateIssueOnBuildFailure.FAILUREORUNSTABL, null, null,false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.UNSTABLE);

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);
        YouTrackServer server = mock(YouTrackServer.class);

        doReturn(server).when(youtrackCreateIssueOnBuildFailure).getYouTrackServer(youTrackSite);

        youtrackCreateIssueOnBuildFailure.perform(build,launcher,buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo("Could not login user to YouTrack"));
    }

    @Test
    public void testUnstableAndCreateIssueOnUnstable() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);


        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("PROJECT", "SUMMARY", "DESCRIPTION", YoutrackCreateIssueOnBuildFailure.FAILUREORUNSTABL, null, null,false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        when(build.getEnvironment(buildListener)).thenReturn(new EnvVars());

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);
        YouTrackServer server = mock(YouTrackServer.class);

        doReturn(server).when(youtrackCreateIssueOnBuildFailure).getYouTrackServer(youTrackSite);


        final List<CreateIssueCommand> commandList = new ArrayList<CreateIssueCommand>();
        Answer<Command> answer = new Answer<Command>() {
            public Command answer(InvocationOnMock invocation) throws Throwable {
                CreateIssueCommand command = new CreateIssueCommand();
                command.project = (String) invocation.getArguments()[2];
                command.summary = (String) invocation.getArguments()[3];
                command.description = (String) invocation.getArguments()[4];
                command.command = (String) invocation.getArguments()[5];
                commandList.add(command);
                Command issuedCommand = new Command();
                issuedCommand.setStatus(Command.Status.OK);
                issuedCommand.setIssueId("PROJECT-1");
                return issuedCommand;
            }
        };
        doAnswer(answer).when(server).createIssue(Mockito.anyString(), Mockito.any(User.class), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Matchers.<File>any());

        User user = new User();
        user.setLoggedIn(true);
        user.setUsername("user");
        when(server.login("user", "password")).thenReturn(user);
        youtrackCreateIssueOnBuildFailure.perform(build, launcher, buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo("Created new YouTrack issue PROJECT-1"));
        assertThat(1, equalTo(commandList.size()));
    }

    @Test
    public void testVariableExpansion() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);


        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("project", "${VAR1}", "${VAR2}", YoutrackCreateIssueOnBuildFailure.FAILUREORUNSTABL, null, "${VAR3}",false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        EnvVars envVars = new EnvVars();
        envVars.put("VAR1", "v1");
        envVars.put("VAR2", "v2");
        envVars.put("VAR3", "v3");
        when(build.getEnvironment(buildListener)).thenReturn(envVars);

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);
        YouTrackServer server = mock(YouTrackServer.class);

        doReturn(server).when(youtrackCreateIssueOnBuildFailure).getYouTrackServer(youTrackSite);


        final List<CreateIssueCommand> commandList = new ArrayList<CreateIssueCommand>();
        Answer<Command> answer = new Answer<Command>() {
            public Command answer(InvocationOnMock invocation) throws Throwable {
                CreateIssueCommand command = new CreateIssueCommand();
                command.project = (String) invocation.getArguments()[2];
                command.summary = (String) invocation.getArguments()[3];
                command.description = (String) invocation.getArguments()[4];
                command.command = (String) invocation.getArguments()[5];
                commandList.add(command);
                Command issuedCommand = new Command();
                issuedCommand.setStatus(Command.Status.OK);
                issuedCommand.setIssueId("project-1");
                return issuedCommand;
            }
        };
        doAnswer(answer).when(server).createIssue(Mockito.anyString(), Mockito.any(User.class), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Matchers.<File>any());

        User user = new User();
        user.setLoggedIn(true);
        user.setUsername("user");
        when(server.login("user", "password")).thenReturn(user);
        youtrackCreateIssueOnBuildFailure.perform(build, launcher, buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo("Created new YouTrack issue project-1"));
        assertThat(1, equalTo(commandList.size()));
        CreateIssueCommand createIssueCommand = commandList.get(0);
        assertThat("v1", equalTo(createIssueCommand.summary));
        assertThat("v2", equalTo(createIssueCommand.description));
        assertThat("v3", equalTo(createIssueCommand.command));
    }

    @Test
    public void testDefaultValues() throws IOException, InterruptedException {
        FreeStyleProject mock = mock(FreeStyleProject.class);
        AbstractBuild build = spy(new FreeStyleBuild(mock));
        Launcher launcher = mock(Launcher.class);
        BuildListener buildListener = mock(BuildListener.class);

        YoutrackCreateIssueOnBuildFailure youtrackCreateIssueOnBuildFailure =
                spy(new YoutrackCreateIssueOnBuildFailure("project", "", "", YoutrackCreateIssueOnBuildFailure.FAILUREORUNSTABL, null, null,false));

        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(buildListener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        when(build.getNumber()).thenReturn(1);
        EnvVars envVars = new EnvVars();


        doReturn(envVars).when(build).getEnvironment(buildListener);

        doReturn(youTrackSite).when(youtrackCreateIssueOnBuildFailure).getYouTrackSite(build);
        YouTrackServer server = mock(YouTrackServer.class);

        doReturn(server).when(youtrackCreateIssueOnBuildFailure).getYouTrackServer(youTrackSite);
        doReturn("buildUrl").when(youtrackCreateIssueOnBuildFailure).getAbsoluteUrl(build);


        final List<CreateIssueCommand> commandList = new ArrayList<CreateIssueCommand>();
        Answer<Command> answer = new Answer<Command>() {
            public Command answer(InvocationOnMock invocation) throws Throwable {
                CreateIssueCommand command = new CreateIssueCommand();
                command.project = (String) invocation.getArguments()[2];
                command.summary = (String) invocation.getArguments()[3];
                command.description = (String) invocation.getArguments()[4];
                command.command = (String) invocation.getArguments()[5];
                commandList.add(command);
                Command issuedCommand = new Command();
                issuedCommand.setStatus(Command.Status.OK);
                issuedCommand.setIssueId("project-1");
                return issuedCommand;
            }
        };
        doAnswer(answer).when(server).createIssue(Mockito.anyString(), Mockito.any(User.class), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Matchers.<File>any());

        User user = new User();
        user.setLoggedIn(true);
        user.setUsername("user");
        when(server.login("user", "password")).thenReturn(user);
        youtrackCreateIssueOnBuildFailure.perform(build, launcher, buildListener);
        String s = stream.toString();
        assertThat(s.trim(), equalTo("Created new YouTrack issue project-1"));
        assertThat(1, equalTo(commandList.size()));
        CreateIssueCommand createIssueCommand = commandList.get(0);
        assertThat("Build failure in build 1", equalTo(createIssueCommand.summary));
        assertThat("buildUrl", equalTo(createIssueCommand.description));
    }

    private static class CreateIssueCommand {
        String project;
        String summary;
        String description;
        String command;
    }
*/
}