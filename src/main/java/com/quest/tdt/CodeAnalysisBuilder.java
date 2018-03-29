package com.quest.tdt;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CodeAnalysisBuilder extends Builder implements SimpleBuildStep {
    private String connection;
    private List<CodeAnalysisDBObject> objects;
    private List<CodeAnalysisDBObjectFolder> objectFolders;
    private CodeAnalysisReport report;
    private int ruleSet;

    @DataBoundConstructor
    public CodeAnalysisBuilder(String connection, List<CodeAnalysisDBObject> objects, List<CodeAnalysisDBObjectFolder> objectFolders, CodeAnalysisReport report, int ruleSet) {
        this.connection = connection;
        this.objects = objects == null ? new ArrayList<CodeAnalysisDBObject>() : new ArrayList<CodeAnalysisDBObject>(objects);
        this.objectFolders = objectFolders == null ? new ArrayList<CodeAnalysisDBObjectFolder>() : new ArrayList<CodeAnalysisDBObjectFolder>(objectFolders);
        this.report = report;
        this.ruleSet = ruleSet;
    }

    public String getConnection() { return connection; }
    public List<CodeAnalysisDBObject> getObjects() { return objects; }
    public List<CodeAnalysisDBObjectFolder> getObjectFolders() { return objectFolders; }
    public CodeAnalysisReport getReport() { return report; }
    public int getRuleSet() { return ruleSet; }

    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        EnvVars vars = run.getEnvironment(listener);

        String expConnection = vars.expand(connection);
        ArrayList<CodeAnalysisDBObject> expObjects = expandObjects(vars, objects);
        ArrayList<CodeAnalysisDBObjectFolder> expObjectFolders = expandObjectFolders(vars, objectFolders);
        CodeAnalysisReport expReport = expandReport(vars, report);

        CodeAnalysisPowerShell powerShell = new CodeAnalysisPowerShell(expConnection, expObjects, expObjectFolders, ruleSet, expReport);
        powerShell.run(run, listener);
    }

    private ArrayList<CodeAnalysisDBObject> expandObjects(EnvVars vars, List<CodeAnalysisDBObject> objects) {
        ArrayList<CodeAnalysisDBObject> expObjects = new ArrayList<CodeAnalysisDBObject>();
        for (CodeAnalysisDBObject object : objects) {
            CodeAnalysisDBObject expObject = new CodeAnalysisDBObject(
                                                    vars.expand(object.getName()),
                                                    vars.expand(object.getOwner()),
                                                    vars.expand(object.getType()));
            expObjects.add(expObject);
        }
        return expObjects;
    }

    private ArrayList<CodeAnalysisDBObjectFolder> expandObjectFolders(EnvVars vars, List<CodeAnalysisDBObjectFolder> objectFolders) {
        ArrayList<CodeAnalysisDBObjectFolder> expObjectFolders = new ArrayList<CodeAnalysisDBObjectFolder>();
        for (CodeAnalysisDBObjectFolder objectFolder : objectFolders) {
            CodeAnalysisDBObjectFolder expObjectFolder = new CodeAnalysisDBObjectFolder(
                                                            vars.expand(objectFolder.getPath()),
                                                            vars.expand(objectFolder.getFilter()),
                                                            objectFolder.getRecurse());
            expObjectFolders.add(expObjectFolder);
        }
        return expObjectFolders;
    }

    private CodeAnalysisReport expandReport(EnvVars vars, CodeAnalysisReport report) {
        CodeAnalysisReport expReport = new CodeAnalysisReport(
                                            vars.expand(report.getName()),
                                            vars.expand(report.getFolder()),
                                            report.getHtml(),
                                            report.getJson(),
                                            report.getXls(),
                                            report.getXml());
        return expReport;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckConnection(@QueryParameter String value) {
            return value.isEmpty() ? FormValidation.warning(Messages.CodeAnalysisConnectionEmpty()) : FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillRuleSetItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(Messages.RuleSetTop20(), "0");
            items.add(Messages.RuleSetAllRules(), "1");
            items.add(Messages.RuleSetCodeCorrectness(), "2");
            items.add(Messages.RuleSetControlStructures(), "3");
            items.add(Messages.RuleSetCodeEfficiency(), "4");
            items.add(Messages.RuleSetInformational(), "5");
            items.add(Messages.RuleSetMaintainability(), "6");
            items.add(Messages.RuleSetProgramStructures(), "7");
            items.add(Messages.RuleSetReadability(), "8");
            items.add(Messages.RuleSetSevere(), "9");
            items.add(Messages.RuleSetVariables(), "10");
            items.add(Messages.RuleSetWarning(), "11");

            return items;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.CodeAnalysisDisplayName();
        }
    }
}
