package org.jenkinsci.plugins.youtrack;

import com.google.common.collect.ArrayListMultimap;
import groovy.lang.Binding;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.tasks.Mailer;
import jenkins.model.Jenkins;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.Project;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class YoutrackIssueUpdater {

    private static final Logger LOGGER = Logger.getLogger(YoutrackIssueUpdater.class.getName());


    @Data
    private static class Pair<T, U> {
        private final T first;
        private final U second;
    }

    /**
     * Converts list of commands to map.
     *
     * @param youTrackSite site to convert for.
     * @return map from prefix to command.
     */
    private static Map<String, String> getPrefixCommands(YouTrackSite youTrackSite) {
        List<PrefixCommandPair> prefixCommandPairs = youTrackSite.getPrefixCommandPairs();
        HashMap<String, String> prefixCommandMap = new HashMap<String, String>();
        if (prefixCommandPairs == null || prefixCommandPairs.isEmpty()) {
            return null;
        }
        for (PrefixCommandPair commandPair : prefixCommandPairs) {
            if (commandPair.getPrefix() != null && !commandPair.getPrefix().isEmpty()) {
                if (commandPair.getCommand() != null && !commandPair.getCommand().isEmpty()) {
                    prefixCommandMap.put(commandPair.getPrefix().toLowerCase(), commandPair.getCommand());
                }
            }
        }
        return prefixCommandMap;
    }

    private static Set<String> getFixedValues(YouTrackSite youTrackSite) {
        Set<String> fixedValues = new HashSet<String>();
        if (youTrackSite.getFixedValues() != null && !youTrackSite.getFixedValues().equals("")) {
            String values = youTrackSite.getFixedValues();
            String[] fixedValueArray = values.split(",");
            for (String fixedValueFromArray : fixedValueArray) {
                if (!fixedValueFromArray.trim().equals("")) {
                    fixedValues.add(fixedValueFromArray.trim());
                }
            }
        } else {
            fixedValues.add("Fixed");
        }

        return fixedValues;
    }

    public void update(SCM scm, AbstractBuild<?, ?> build, BuildListener listener, ChangeLogSet<?> changeLogSet) throws InvocationTargetException, IllegalAccessException {
        YouTrackSite youTrackSite = getYouTrackSite(build);
        if (youTrackSite == null || !youTrackSite.isPluginEnabled()) {
            return;
        }

        Iterator<? extends ChangeLogSet.Entry> changeLogIterator = changeLogSet.iterator();

        YouTrackServer youTrackServer = getYouTrackServer(youTrackSite);
        User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword().getPlainText());
        if (user == null || !user.isLoggedIn()) {
            listener.getLogger().append("FAILED: log in with set YouTrack user");
            youTrackSite.failed(build);
        }
        performActions(scm, build, listener, youTrackSite, changeLogIterator, youTrackServer, user);
    }

    YouTrackServer getYouTrackServer(YouTrackSite youTrackSite) {
        return new YouTrackServer(youTrackSite.getUrl());
    }

    YouTrackSite getYouTrackSite(AbstractBuild<?, ?> build) {
        return YouTrackSite.get(build.getProject());
    }

    public void performActions(SCM scm, AbstractBuild<?, ?> build, BuildListener listener, YouTrackSite youTrackSite, Iterator<? extends ChangeLogSet.Entry> changeLogIterator, YouTrackServer youTrackServer, User user) throws IllegalAccessException, InvocationTargetException {
        build.addAction(new YouTrackIssueAction(build.getProject()));

        List<Project> projects = youTrackServer.getProjects(user);


        if (projects != null) {
            build.addAction(new YouTrackSaveProjectShortNamesAction(projects));
        } else {
            AbstractBuild<?, ?> lastSuccessfulBuild = build.getProject().getLastStableBuild();
            YouTrackSaveProjectShortNamesAction action = lastSuccessfulBuild.getAction(YouTrackSaveProjectShortNamesAction.class);
            if (action != null) {
                List<String> shortNames = action.getShortNames();
                List<Project> previousProjects = new ArrayList<Project>();
                for (String shortName : shortNames) {
                    Project prevProject = new Project();
                    prevProject.setShortName(shortName);
                    previousProjects.add(prevProject);
                    projects = previousProjects;
                }
            } else {
                projects = new ArrayList<Project>();
            }
        }

        List<ChangeLogSet.Entry> changeLogEntries = new ArrayList<ChangeLogSet.Entry>();
        while (changeLogIterator.hasNext()) {
            changeLogEntries.add(changeLogIterator.next());
        }

        YouTrackCommandAction commandAction = new YouTrackCommandAction(build);

        List<Issue> fixedIssues = new ArrayList<Issue>();

        EnvVars environment = null;
        try {
            environment = build.getEnvironment(listener);
        } catch (IOException e) {
//            LOGGER.error(e, e);
        } catch (InterruptedException e) {
//            LOGGER.error(e, e);
        }
        if (youTrackSite.isCommentEnabled()) {
            ArrayListMultimap<Issue, ChangeLogSet.Entry> relatedChanges = ArrayListMultimap.create();
            for (ChangeLogSet.Entry entry : changeLogEntries) {
                String msg = getMessage(scm, entry, listener, environment, build);

                List<Issue> issuesFromCommit = findIssuesFromCommit(msg, projects);
                for (Issue issue : issuesFromCommit) {
                    relatedChanges.put(issue, entry);
                }
            }

            for (Issue relatedIssue : relatedChanges.keySet()) {
                List<ChangeLogSet.Entry> entries = relatedChanges.get(relatedIssue);
                List<Command> commands = addComment(build, youTrackSite, youTrackServer, user, relatedIssue, entries, listener);
                for (Command command : commands) {
                    commandAction.addCommand(command);
                }
            }
        }


        for ( ChangeLogSet.Entry entry : changeLogEntries) {
            String msg = getMessage(scm, entry, listener, environment, build);

            if (projects != null) {
                List<Project> youtrackProjects = new ArrayList<Project>(projects.size());
                Set<String> includedProjects = getIncludedProjects(projects, youTrackSite);

                for (Project project : projects) {
                    if (includedProjects.contains(project.getShortName())) {
                        youtrackProjects.add(project);
                    }
                }

                Jenkins instance = Jenkins.getInstance();
                YouTrackPlugin plugin = null;
                if (instance != null) {
                    plugin = instance.getPlugin(YouTrackPlugin.class);
                }
                YoutrackProcessedRevisionsSaver revisionsSaver = null;
                if (plugin != null) {
                    revisionsSaver = plugin.getRevisionsSaver();
                }
                if ((youTrackSite.isTrackCommits() && (revisionsSaver != null && !revisionsSaver.isProcessed(entry.getCommitId()))) || !youTrackSite.isTrackCommits()) {
                    List<Command> commandList = executeCommandsIfEnabled(build, listener, youTrackSite, youTrackServer, user, youtrackProjects, fixedIssues, entry, msg);
                    for (Command command : commandList) {
                        commandAction.addCommand(command);
                    }
                    if (youTrackSite.isTrackCommits() && !commandList.isEmpty()) {
                        if (revisionsSaver != null) {
                            revisionsSaver.addProcessed(entry.getCommitId());
                        }
                    }
                }
            }

        }

        int numCommands = commandAction.getNumCommands();

        if (numCommands > 0) {
            build.addAction(commandAction);
        }

        build.addAction(new YouTrackSaveFixedIssues(fixedIssues));
    }


    public static String getMessage(SCM scm, ChangeLogSet.Entry next, BuildListener listener, EnvVars environment, AbstractBuild<?, ?> build) throws IllegalAccessException, InvocationTargetException {
        String msg;
        if (scm != null && scm.getClass().getSimpleName().equals("GitSCM")) {
            try {
                Method createClient = scm.getClass().getMethod("createClient", TaskListener.class, EnvVars.class, Run.class, FilePath.class);
                GitClient gitClient = (GitClient) createClient.invoke(scm, listener, environment, build, build.getWorkspace());
                List<String> stringList = gitClient.showRevision(gitClient.revParse(next.getCommitId()));
                StringBuilder message = new StringBuilder();
                for (String line : stringList) {
                    if (line.startsWith("   ")) {
                        String substring = line.substring(4);
                        message.append(substring).append("\n");
                    }
                }

                msg = message.toString();
            } catch (SecurityException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);

            }
        } else {
            msg = next.getMsg();
        }
        return msg;
    }


    private Set<String> getIncludedProjects(List<Project> projects, YouTrackSite youTrackSite) {
        String executeProjectLimits = youTrackSite.getExecuteProjectLimits();
        if (executeProjectLimits == null || executeProjectLimits.trim().equals("")) {
            HashSet<String> projectIds = new HashSet<String>();
            for (Project project : projects) {
                projectIds.add(project.getShortName());
            }
            return projectIds;
        } else {
            HashSet<String> nameSet = new HashSet<String>();
            String[] names = executeProjectLimits.split(",");
            for (String name : names) {
                if (!name.trim().equals("")) {
                    nameSet.add(name);
                }
            }
            return nameSet;
        }
    }

    /**
     * Executes the commands if execute commands is enabled;
     *
     *
     * @param build
     * @param listener       the listener.
     * @param youTrackSite   YouTrack site.
     * @param youTrackServer YouTrack server.
     * @param user           user.
     * @param projects       projects.
     * @param fixedIssues    list to fill with fixed issues.
     * @param changeLogEntry the ChangeLogEntry.
     * @param msg            the message to parse.
     * @return the list of commands tried to be executed.
     */
    List<Command> executeCommandsIfEnabled(AbstractBuild<?, ?> build, BuildListener listener, YouTrackSite youTrackSite, YouTrackServer youTrackServer, User user, List<Project> projects, List<Issue> fixedIssues, ChangeLogSet.Entry changeLogEntry, String msg) {
        List<Command> commands = new ArrayList<Command>();
        if (youTrackSite.isCommandsEnabled()) {
            Map<String, String> prefixCommands = getPrefixCommands(youTrackSite);

            String[] lines = msg.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.contains("#")) {
                    String comment = null;
                    String issueStart = line.substring(line.indexOf("#") + 1);
                    boolean isSilent = false;
                    String extraPrefixCommand = null;
                    int hashPosition = line.indexOf("#");
                    if (hashPosition != 0) {
                        int prefixLength = hashPosition;
                        char charBefore = line.charAt(hashPosition - 1);
                        if (charBefore == '!') {
                            isSilent = true;
                            --prefixLength;
                        }

                        if (prefixCommands != null && prefixLength != 0) {
                            String prefix = line.substring(0, prefixLength).trim().toLowerCase();
                            for (Map.Entry<String, String> entry : prefixCommands.entrySet()) {
                                if (prefix.endsWith(entry.getKey())) {
                                    extraPrefixCommand = entry.getValue();
                                    break;
                                }
                            }
                        }
                    }

                    if (i + 1 < lines.length) {
                        // Consider all lines following the action one to be a comment,
                        // until we see another "#"
                        // We can keep moving i at this point since any lines we process
                        // shouldn't be considered for commands.
                        StringBuilder commentBuilder = new StringBuilder();
                        for (++i; i < lines.length; ++i) {
                            String nextLine = lines[i];
                            if (nextLine.contains("#")) {
                                // Reset so we process this line again in the next iteration.
                                --i;
                                break;
                            }
                            commentBuilder.append(nextLine).append("\n");
                        }

                        comment = commentBuilder.toString().trim();
                        if (comment.isEmpty()) {
                            comment = null;
                        }
                    }

                    Project p = null;
                    for (Project project : projects) {
                        if (issueStart.startsWith(project.getShortName() + "-")) {
                            p = project;
                            break;
                        }
                    }

                    if (p == null) {
                        continue;
                    }

                    Pair<String, String> issueAndCommand = getIssueAndCommand(p, issueStart);
                    if (issueAndCommand == null) {
                        continue;
                    }

                    if (extraPrefixCommand != null) {
                        try {
                            EnvVars environment = build.getEnvironment(listener);
                            if(environment != null){
                            extraPrefixCommand = environment.expand(extraPrefixCommand);
                            }
                        } catch (IOException e) {
//            LOGGER.error(e, e);                        
                    }catch (InterruptedException e) {
//            LOGGER.error(e, e);
                }
        
                        applyCommandToIssue(build, youTrackSite, youTrackServer, user, fixedIssues, changeLogEntry, issueAndCommand.getFirst(), extraPrefixCommand, null, listener, commands, isSilent);
                    }

                    if (issueAndCommand.getSecond() != null && !issueAndCommand.getSecond().equals("")) {
                        applyCommandToIssue(build, youTrackSite, youTrackServer, user, fixedIssues, changeLogEntry, issueAndCommand.getFirst(), issueAndCommand.getSecond(), comment, listener, commands, isSilent);
                    }
                }
            }
        }
        return commands;
    }

    private static Pair<String, String> getIssueAndCommand(Project p, String issueStart) {
        Pattern projectPattern = Pattern.compile("(" + p.getShortName() + "-" + "(\\d+)" + ")( )?(.*)");

        Matcher matcher = projectPattern.matcher(issueStart);
        // TODO: Should this support invoking commands on multiple issues when they're on the same line?
        // And even including the second mention as part of the command to the first?
        // while (matcher.find())
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }

        String issueId = p.getShortName() + "-" + matcher.group(2);
        String command = matcher.group(4);

        return new Pair<String, String>(issueId, command);
    }

    private void applyCommandToIssue(AbstractBuild<?, ?> build, YouTrackSite youTrackSite, YouTrackServer youTrackServer, User user, List<Issue> fixedIssues, ChangeLogSet.Entry next, String issueId, String command, String comment, BuildListener listener, List<Command> commands, boolean silent) {
        User userByEmail = null;
        if (youTrackSite.isRunAsEnabled()) {
            String address = next.getAuthor().getProperty(Mailer.UserProperty.class).getAddress();
            userByEmail = youTrackServer.getUserByEmail(user, address);
            if (userByEmail == null) {
                listener.getLogger().println("Failed to find user with e-mail: " + address);
                youTrackSite.failed(build);
            }
        }

        String stateFieldName = "State";
        if (youTrackSite.getStateFieldName() != null && !youTrackSite.getStateFieldName().equals("")) {
            stateFieldName = youTrackSite.getStateFieldName();
        }

        //Get the issue state, then apply command, and get the issue state again.
        //to know whether the command has been marked as fixed, instead of trying to
        //interpret the command. This means however that there is a possibility for
        //the user to change state between the before and the after call, so the after
        //state can be affected by something else than the command.
        Issue before = youTrackServer.getIssue(user, issueId, stateFieldName);
        boolean isSilent = youTrackSite.isSilentCommands() || silent;
        Command cmd = youTrackServer.applyCommand(youTrackSite.getName(), user, new Issue(issueId), command, comment, null, userByEmail, !isSilent);
        if (cmd.getStatus() == Command.Status.OK) {
            listener.getLogger().println("Applied command: " + command + " to issue: " + issueId);
        } else {
            listener.getLogger().println("FAILED: Applying command: " + command + " to issue: " + issueId);
            youTrackSite.failed(build);
        }
        commands.add(cmd);
        Issue after = youTrackServer.getIssue(user, issueId, stateFieldName);

        Set<String> fixedValues = getFixedValues(youTrackSite);

        if (before != null && after != null && !fixedValues.contains(before.getState()) && fixedValues.contains(after.getState())) {
            fixedIssues.add(after);
        }
    }

    private List<Issue> findIssuesFromCommit(String msg, List<Project> projects) {
        List<Issue> issues = new ArrayList<Issue>();
        for (Project project1 : projects) {
            String shortName = project1.getShortName();
            Pattern projectPattern = Pattern.compile("^(" + shortName + "-" + "(\\d+)" + ")|\\W(" + shortName + "-" + "(\\d+))");
            Matcher matcher = projectPattern.matcher(msg);
            while (matcher.find()) {
                if (matcher.groupCount() >= 1) {
                    String id = matcher.group(2);
                    if (id == null) {
                        id = matcher.group(4);
                    }


                    String issueId = shortName + "-" + id;
                    issues.add(new Issue(issueId));
                }
            }
        }
        return issues;
    }

    private List<Command> addComment(AbstractBuild<?, ?> build, YouTrackSite youTrackSite, YouTrackServer youTrackServer, User user, Issue relatedIssue, List<ChangeLogSet.Entry> entries, BuildListener listener) {
        List<Command> commands = new ArrayList<Command>();

        SecureGroovyScript commentTextScript = youTrackSite.getCommentTextSecure();
        String commentText = "";
        if (commentTextScript == null || StringUtils.isBlank(commentTextScript.getScript())) {
            StringBuilder stringBuilder = new StringBuilder("Related build: " + getAbsoluteUrlForBuild(build));
            for (ChangeLogSet.Entry entry : entries) {
                stringBuilder.append("\nSHA: ").append(entry.getCommitId());
            };
            commentText = stringBuilder.toString();
        } else {
            try {
                EnvVars environment = build.getEnvironment(listener);
                commentText = environment.expand(commentText);
                Map<String, Object> env = new HashMap<String, Object>();
                env.put("build", build);
                env.put("entries", entries);


                Binding binding = new Binding(env);
                String result = (String) commentTextScript.evaluate(getClass().getClassLoader(), binding);

                commentText = result;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(),e);

            }
        }
        Command comment;
        comment = youTrackServer.comment(youTrackSite.getName(), user, relatedIssue, commentText, youTrackSite.getLinkVisibility(), youTrackSite.isSilentLinks());
        if (comment != null) {
            commands.add(comment);
            if (comment.getStatus() == Command.Status.OK) {
                listener.getLogger().println("Commented on " + relatedIssue.getId());
            } else {
                listener.getLogger().println("FAILED: Commented on " + relatedIssue.getId());
                youTrackSite.failed(build);
            }
        }

        return commands;
    }

    protected String getAbsoluteUrlForBuild(AbstractBuild build) {
        return build.getAbsoluteUrl();
    }


}
