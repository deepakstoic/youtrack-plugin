package org.jenkinsci.plugins.youtrack;

import hudson.Extension;
import hudson.model.*;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.youtrack.youtrackapi.*;
import org.jenkinsci.plugins.youtrack.youtrackapi.Project;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Associates a YouTrack server and enables the users to set integration settings.
 */
public class YouTrackProjectProperty extends JobProperty<AbstractProject<?, ?>> {
    /**
     * The name of the site.
     */
    @Getter @Setter private String siteName;

    /**
     * If the YouTrack plugin is enabled.
     */
    @Getter @Setter private boolean pluginEnabled;
    /**
     * If ping back comments is enabled.
     */
    @Getter @Setter private boolean commentsEnabled;
    /**
     * The text to use for ping back comments
     */
    @Getter @Setter private String commentText;
    /**
     * The text to use for ping back comments
     */
    @Getter @Setter private SecureGroovyScript commentTextSecure;
    /**
     * If executing commands is enabled.
     */
    @Getter @Setter private boolean commandsEnabled;
    /**
     * If the commands should be run as the vcs user.
     */
    @Getter @Setter private boolean runAsEnabled;

    /**
     * If ChangeLog annotations is enabled.
     */
    @Getter @Setter private boolean annotationsEnabled;

    /**
     * The name of the group comment links should be visible for.
     */
    @Getter @Setter private String linkVisibility;
    /**
     * Name of state field to check for weather an issue is selected.
     */
    @Getter @Setter private String stateFieldName;
    /**
     * Comma-separated list of values that are seen as fixed.
     */
    @Getter @Setter private String fixedValues;
    /**
     * Execute commands silently, i.e. do not notify watchers.
     */
    @Getter @Setter private boolean silentCommands;

    /**
     * Execute link comment silently.
     */
    @Getter @Setter private boolean silentLinks;
    /**
     * Limits the projects commands are applied to.
     */
    @Getter @Setter private String executeProjectLimits;
    /**
     * Tracks the processed commits.
     */
    @Getter @Setter private boolean trackCommits;
    /**
     * This is the default project for the integration, used for creating issues.
     */
    @Getter @Setter private String project;
    /**
     * Mapping from prefix words to corresponding commands.
     */
    @Getter @Setter private List<PrefixCommandPair> prefixCommandPairs;
    /**
     * How the build should fail if we can't apply the commands
     */
    @Getter @Setter private YoutrackBuildFailureMode failureMode = YoutrackBuildFailureMode.NONE;

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public YouTrackProjectProperty(String siteName, boolean pluginEnabled, boolean commentsEnabled, boolean commandsEnabled, boolean runAsEnabled, boolean annotationsEnabled, String linkVisibility, String stateFieldName, String fixedValues, boolean silentCommands, boolean silentLinks, String executeProjectLimits, boolean trackCommits, String project, String commentText, YoutrackBuildFailureMode failureMode, SecureGroovyScript commentTextSecure) {
        this.siteName = siteName;
        this.pluginEnabled = pluginEnabled;
        this.commentsEnabled = commentsEnabled;
        this.commandsEnabled = commandsEnabled;
        this.runAsEnabled = runAsEnabled;
        this.annotationsEnabled = annotationsEnabled;
        this.linkVisibility = linkVisibility;
        this.stateFieldName = stateFieldName;
        this.fixedValues = fixedValues;
        this.silentCommands = silentCommands;
        this.silentLinks = silentLinks;
        this.executeProjectLimits = executeProjectLimits;
        this.trackCommits = trackCommits;
        this.project = project;
        this.commentText = commentText;
        this.commentTextSecure = commentTextSecure;
        this.failureMode = failureMode;
        this.prefixCommandPairs = new ArrayList<PrefixCommandPair>();
    }


    @Override
    public JobPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends JobPropertyDescriptor {
        private final CopyOnWriteList<YouTrackSite> sites = new CopyOnWriteList<YouTrackSite>();

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType) || WorkflowJob.class.isAssignableFrom(jobType);
        }

        public DescriptorImpl() {
            super(YouTrackProjectProperty.class);
            load();

        }

        public void setSites(YouTrackSite site) {
            sites.add(site);
        }

        public YouTrackSite[] getSites() {
            return sites.toArray(new YouTrackSite[0]);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {

            JSONObject pluginEnabled = (JSONObject) formData.get("pluginEnabled");
            YouTrackProjectProperty ypp = null;
            if (pluginEnabled != null) {
                ypp = req.bindJSON(YouTrackProjectProperty.class, pluginEnabled);
                if (ypp.siteName == null) {
                    return null;
                }

                ypp.commentTextSecure.configuringWithKeyItem();

                ypp.setPluginEnabled(true);
                Object prefixCommandArray = pluginEnabled.get("prefixCommandPairs");
                List<PrefixCommandPair> commandPairs = req.bindJSONToList(PrefixCommandPair.class, prefixCommandArray);
                ypp.setPrefixCommandPairs(commandPairs);
            }


            return ypp;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {

            sites.replaceBy(req.bindParametersToList(YouTrackSite.class, "youtrack."));
            save();
            return true;
        }


        @Override
        public String getDisplayName() {
            return "YouTrack Plugin";
        }

        public AutoCompletionCandidates doAutoCompleteProject(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if (youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if (user != null && user.isLoggedIn()) {
                    List<Project> projects = youTrackServer.getProjects(user);
                    for (Project youtrackProject : projects) {
                        if (value == null || value.equals("")) {
                            autoCompletionCandidates.add(value);
                        } else if (youtrackProject.getShortName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(youtrackProject.getShortName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }

        @SuppressWarnings("UnusedDeclaration")
        public FormValidation doVersionCheck(@QueryParameter final String value) throws IOException, ServletException {
            return new FormValidation.URLCheck() {

                @Override
                protected FormValidation check() throws IOException, ServletException {
                    YouTrackServer youTrackServer = new YouTrackServer(value);
                    String[] version = youTrackServer.getVersion();
                    if (version == null) {
                        return FormValidation.warning("Could not get version, maybe because version is below 4.x");
                    } else {
                        return FormValidation.ok();
                    }
                }
            }.check();
        }

        @SuppressWarnings("UnusedDeclaration")
        public FormValidation doTestConnection(
                @QueryParameter("youtrack.url") final String url,
                @QueryParameter("youtrack.username") final String username,
                @QueryParameter("youtrack.password") final String password) {

            YouTrackServer youTrackServer = new YouTrackServer(url);
            if (username != null && !username.equals("")) {
                User login = youTrackServer.login(username, password);
                if (login != null && login.isLoggedIn()) {
                    return FormValidation.ok("Connection ok!");
                } else {
                    return FormValidation.error("Could not login with given options");
                }
            } else {
                return FormValidation.ok();
            }
        }


        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteLinkVisibility(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            return getPossibleGroups(project, value);
        }

        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteStateFieldName(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            return getFields(project, value);
        }

        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteFixedValues(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if (youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if (user != null) {
                    StateBundle bundle = youTrackServer.getStateBundleForField(user, youTrackSite.getStateFieldName());
                    if (bundle != null) {
                        for (State state : bundle.getStates()) {
                            if (state.getValue().toLowerCase().contains(value.toLowerCase())) {
                                autoCompletionCandidates.add(state.getValue());
                            }
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }

        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteExecuteProjectLimits(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            return getProjects(project, value);
        }
    }

    static AutoCompletionCandidates getProjects(@AncestorInPath AbstractProject project, @QueryParameter String value) {
        YouTrackSite youTrackSite = YouTrackSite.get(project);
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (youTrackSite != null) {
            YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
            User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
            if (user != null) {
                List<Project> projects = youTrackServer.getProjects(user);
                for (Project youtrackProject : projects) {
                    if(youtrackProject.getShortName().toLowerCase().contains(value.toLowerCase())) {
                        autoCompletionCandidates.add(youtrackProject.getShortName());
                    }
                }
            }
        }
        return autoCompletionCandidates;
    }

    public static AutoCompletionCandidates getFields(@AncestorInPath AbstractProject project, @QueryParameter String value) {
        YouTrackSite youTrackSite = YouTrackSite.get(project);
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (youTrackSite != null) {
            YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
            User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
            if (user != null) {
                List<Field> fields = youTrackServer.getFields(user);
                for (Field field : fields) {
                    if (field.getName().toLowerCase().contains(value.toLowerCase())) {
                        autoCompletionCandidates.add(field.getName());
                    }
                }
            }
        }
        return autoCompletionCandidates;
    }

    public static AutoCompletionCandidates getPossibleGroups(AbstractProject project, String value) {
        YouTrackSite youTrackSite = YouTrackSite.get(project);
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (youTrackSite != null) {
            YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
            User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
            if (user != null) {
                List<Group> groups = youTrackServer.getGroups(user);
                for (Group group : groups) {
                    if (group.getName().toLowerCase().contains(value.toLowerCase())) {
                        autoCompletionCandidates.add(group.getName());
                    }
                }
            }
        }
        return autoCompletionCandidates;
    }

    public YouTrackSite getSite() {
        YouTrackSite result = null;
        YouTrackSite[] sites = DESCRIPTOR.getSites();
        if (siteName == null && sites.length > 0) {
            result = sites[0];
        }

        for (YouTrackSite site : sites) {
            if (site.getName() != null && site.getName().equals(siteName)) {
                result = site;
                break;
            }
        }
        if (result != null) {
            result.setPluginEnabled(pluginEnabled);
            result.setCommentEnabled(commentsEnabled);
            result.setCommandsEnabled(commandsEnabled);
            result.setAnnotationsEnabled(annotationsEnabled);
            result.setRunAsEnabled(runAsEnabled);
            result.setLinkVisibility(linkVisibility);
            result.setStateFieldName(stateFieldName);
            result.setFixedValues(fixedValues);
            result.setSilentCommands(silentCommands);
            result.setSilentLinks(silentLinks);
            result.setExecuteProjectLimits(executeProjectLimits);
            result.setTrackCommits(trackCommits);
            result.setProject(project);
            result.setPrefixCommandPairs(prefixCommandPairs);
            result.setCommentTextSecure(commentTextSecure);
            result.setFailureMode(failureMode);
        }
        return result;
    }
}
