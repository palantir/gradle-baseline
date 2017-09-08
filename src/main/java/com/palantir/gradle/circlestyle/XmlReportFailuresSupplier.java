package com.palantir.gradle.circlestyle;

import static com.palantir.gradle.circlestyle.XmlUtils.parseXml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.SingleFileReport;

class XmlReportFailuresSupplier implements FailuresSupplier {

    public static <T extends Task & Reporting<? extends ReportContainer<SingleFileReport>>>
            XmlReportFailuresSupplier create(final T task, final ReportHandler<T> reportHandler) {
        // Ensure any necessary output is enabled
        task.doFirst(new Action<Task>() {
            @Override
            public void execute(Task ignored) {
                reportHandler.configureTask(task);
            }
        });
        return new XmlReportFailuresSupplier(task, reportHandler);
    }

    private final Reporting<? extends ReportContainer<SingleFileReport>> reporting;
    private final ReportHandler<?> reportHandler;

    private XmlReportFailuresSupplier(
            Reporting<? extends ReportContainer<SingleFileReport>> reporting,
            ReportHandler<?> reportHandler) {
        this.reporting = reporting;
        this.reportHandler = reportHandler;
    }

    @Override
    public List<Failure> getFailures() throws IOException {
        File sourceReport = reporting.getReports().findByName("xml").getDestination();
        return parseXml(reportHandler, new FileInputStream(sourceReport)).failures();
    }

    @Override
    public RuntimeException handleInternalFailure(File reportDir, RuntimeException e) {
        File rawReportsDir = new File(reportDir, UUID.randomUUID().toString());
        rawReportsDir.mkdirs();
        for (SingleFileReport rawReport : reporting.getReports()) {
            if (rawReport.isEnabled()) {
                rawReport.getDestination().renameTo(new File(rawReportsDir, rawReport.getDestination().getName()));
            }
        }
        return new RuntimeException(
                "Finalizer failed; raw report files can be found at " + rawReportsDir.getName(), e);
    }
}
