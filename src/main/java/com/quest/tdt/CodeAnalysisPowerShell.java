package com.quest.tdt;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.TaskListener;

import com.quest.tdt.util.StreamThread;
import com.quest.tdt.util.Constants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Base64;
import java.util.StringJoiner;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CodeAnalysisPowerShell implements Serializable  {
    private String connection;
    private List<CodeAnalysisDBObject> objects;
    private List<CodeAnalysisDBObjectFolder> objectFolders;
    private int ruleSet;
    private CodeAnalysisReport report;
    private CodeAnalysisFailConditions failConditions;

    public CodeAnalysisPowerShell(
            String connection, List<CodeAnalysisDBObject> objects, List<CodeAnalysisDBObjectFolder> objectFolders,
            int ruleSet, CodeAnalysisReport report, CodeAnalysisFailConditions failConditions) {
        this.connection = connection;
        this.objects = objects;
        this.objectFolders = objectFolders;
        this.ruleSet = ruleSet;

        this.report = report;
        this.failConditions = failConditions;
    }

    public Result run(final FilePath workspace, final TaskListener listener) throws IOException {

        InputStream resourceStream = this.getClass().getClassLoader().getResourceAsStream(Constants.PS_CA);

        // Create a temporary directory to hold scripts and reports
        Path tempdirectory = Files.createTempDirectory("tdt-ca-");

        // Create a temporary file to store our powershell resource stream
        File script = Files.createTempFile(new File(tempdirectory.toString()).toPath(), "tdt-ca-", ".ps1").toFile();
        Files.copy(resourceStream, script.getAbsoluteFile().toPath(), REPLACE_EXISTING);
        String BaseReportName = report.getName();

        // Create our command with appropriate arguments.
        String command = getProgram(script.getAbsolutePath())
                .concat(getConnectionArgument())
                .concat(getObjectsArgument())
                .concat(getObjectFoldersArgument())
                .concat(getRuleSetArgument())
                .concat(getReportNameArgument())
                .concat(" -reportFolder ".concat(Base64.getEncoder().encodeToString( tempdirectory.toString().getBytes(StandardCharsets.UTF_8))))
                .concat(getReportFormats()
                .concat(getFailConditionsArguments()));

        listener.getLogger().println(Constants.LOG_HEADER_CA + "Preparing analysis...");

        Result result = Result.SUCCESS;
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(command);
        process.getOutputStream().close();

        StreamThread outputStreamThread = new StreamThread(process.getInputStream(), listener, Constants.LOG_HEADER_CA, result);
        StreamThread errorStreamThread = new StreamThread(process.getErrorStream(), listener, Constants.LOG_HEADER_CA_ERR, result);

        outputStreamThread.start();
        errorStreamThread.start();

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            listener.getLogger().println(Constants.LOG_HEADER_CA_ERR + writer.toString());

            result = Result.ABORTED;
        }

        // See if we received any errors during stream processing
        if (outputStreamThread.getResult()!=Result.SUCCESS)
            result = outputStreamThread.getResult();
         else if (errorStreamThread.getResult()!=Result.SUCCESS)
            result = errorStreamThread.getResult();

        listener.getLogger().println(Constants.LOG_HEADER_CA + "Analysis completed");

        // Now copy the generated reports to the specified output directory, if specified
        try {

            FilePath reportpath = new FilePath(new File(tempdirectory.toString()));
            reportpath.copyRecursiveTo(BaseReportName+".*", workspace);

        } catch (InterruptedException e) {

            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            listener.getLogger().println(Constants.LOG_HEADER_CA_ERR + writer.toString());
        }

        // Delete the temporary directory and all contents
        Files.walk(tempdirectory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);


        // return the result of the analysis
        return result;
    }

    private String getProgram(String path) {
        return "powershell ".concat(path);
    }

    private String getConnectionArgument() {
        return " -connection ".concat(Base64.getEncoder().encodeToString(connection.getBytes(StandardCharsets.UTF_8)));
    }

    private String getObjectsArgument() {
        if (objects.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(",");
        for (CodeAnalysisDBObject object : objects) {
            joiner.add(object.toString());
        }

        return " -objects ".concat(joiner.toString());
    }

    private String getObjectFoldersArgument() {
        if (objectFolders.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(",");
        for (CodeAnalysisDBObjectFolder objectFolder : objectFolders) {
            joiner.add(objectFolder.toString());
        }

        return " -folders ".concat(joiner.toString());
    }

    private String getRuleSetArgument() {
        return " -ruleSet ".concat(Integer.toString(ruleSet));
    }

    private String getReportNameArgument() {
        return report.getName().isEmpty()
                ? "" : " -reportName ".concat(Base64.getEncoder().encodeToString(report.getName().getBytes(StandardCharsets.UTF_8)));
    }

    private String getReportFormats() {
        return (report.getHtml() ? " -html" : "")
                .concat(report.getJson() ? " -json" : "")
                .concat(report.getXls() ? " -xls" : "")
                .concat(report.getXml() ? " -xml" : "");
    }

    private String getFailConditionsArguments() {
        return " -halstead ".concat(Integer.toString(failConditions.getHalstead()))
                .concat(" -maintainability ".concat(Integer.toString(failConditions.getMaintainability())))
                .concat(" -mcCabe ".concat(Integer.toString(failConditions.getMcCabe())))
                .concat(" -TCR ".concat(Integer.toString(failConditions.getTCR()))
                .concat(failConditions.getRuleViolations() ? " -ruleViolations" : "")
                .concat(failConditions.getSyntaxErrors() ? " -syntaxErrors" : "")
                .concat(failConditions.getIgnoreWrappedPackages() ? " -ignoreWrappedPackages" : ""));
    }
}
