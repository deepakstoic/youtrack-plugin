package org.jenkinsci.plugins.youtrack.pipeline;

import com.gargoylesoftware.htmlunit.html.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class YouTrackProjectPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testNotConfigured() throws IOException, SAXException {
        String projectName = "testProject1";
        WorkflowJob project1 = j.createProject(WorkflowJob.class, projectName);
        HtmlPage configurePage = j.createWebClient().goTo("job/" + projectName + "/configure");
        HtmlForm form = configurePage.getFormByName("config");
        HtmlButton button = (HtmlButton) j.last(form.getHtmlElementsByTagName("button"));
        button.click();
    }

    @Test
    public void testEnableBtn() throws IOException, SAXException {
        String projectName = "testProject1";
        WorkflowJob project1 = j.createProject(WorkflowJob.class, projectName);
        HtmlPage configurePage = j.createWebClient().goTo("job/" + projectName + "/configure");
        HtmlForm form = configurePage.getFormByName("config");

        List<HtmlInput> enableCheckBtns = form.getInputsByName("youtrack.pluginEnabled");
        assertThat(enableCheckBtns.size(), equalTo(1));

        HtmlInput enableCheckBtn = enableCheckBtns.get(0);
        assertFalse(enableCheckBtn.isChecked());

        enableCheckBtn.setChecked(true);
        assertTrue(enableCheckBtn.isChecked());

        HtmlButton button = (HtmlButton) j.last(form.getHtmlElementsByTagName("button"));
        button.click();
    }
}