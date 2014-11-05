package edu.unc.mapseq.workflow.nec.ic;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.renci.jlrm.condor.CondorJob;
import org.renci.jlrm.condor.CondorJobBuilder;
import org.renci.jlrm.condor.CondorJobEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.commons.nec.ic.SaveBestMatchAttributeRunnable;
import edu.unc.mapseq.dao.MaPSeqDAOException;
import edu.unc.mapseq.dao.model.FileData;
import edu.unc.mapseq.dao.model.MimeType;
import edu.unc.mapseq.dao.model.Sample;
import edu.unc.mapseq.dao.model.Workflow;
import edu.unc.mapseq.dao.model.WorkflowRunAttempt;
import edu.unc.mapseq.module.gatk2.GATKUnifiedGenotyper;
import edu.unc.mapseq.module.ic.CalculateMaximumLikelihoodFromVCFCLI;
import edu.unc.mapseq.workflow.WorkflowException;
import edu.unc.mapseq.workflow.WorkflowUtil;
import edu.unc.mapseq.workflow.impl.AbstractSampleWorkflow;
import edu.unc.mapseq.workflow.impl.WorkflowJobFactory;

public class NECIDCheckWorkflow extends AbstractSampleWorkflow {

    private final Logger logger = LoggerFactory.getLogger(NECIDCheckWorkflow.class);

    public NECIDCheckWorkflow() {
        super();
    }

    @Override
    public String getName() {
        return NECIDCheckWorkflow.class.getSimpleName().replace("Workflow", "");
    }

    @Override
    public String getVersion() {
        ResourceBundle bundle = ResourceBundle.getBundle("edu/unc/mapseq/workflow/nec/ic/workflow");
        String version = bundle.getString("version");
        return StringUtils.isNotEmpty(version) ? version : "0.0.1-SNAPSHOT";
    }

    @Override
    public Graph<CondorJob, CondorJobEdge> createGraph() throws WorkflowException {
        logger.debug("ENTERING createGraph()");

        DirectedGraph<CondorJob, CondorJobEdge> graph = new DefaultDirectedGraph<CondorJob, CondorJobEdge>(
                CondorJobEdge.class);

        int count = 0;

        Set<Sample> sampleSet = getAggregatedSamples();
        logger.info("sampleSet.size(): {}", sampleSet.size());

        WorkflowRunAttempt attempt = getWorkflowRunAttempt();

        String siteName = getWorkflowBeanService().getAttributes().get("siteName");
        String intervalList = getWorkflowBeanService().getAttributes().get("intervalList");
        String exomeChipData = getWorkflowBeanService().getAttributes().get("exomeChipData");
        // String expectedExomeChip2HTSFMap = getWorkflowBeanService().getAttributes().get("expectedExomeChip2HTSFMap");

        Workflow variantCallingWorkflow = null;
        try {
            variantCallingWorkflow = getWorkflowBeanService().getMaPSeqDAOBean().getWorkflowDAO()
                    .findByName("NECVariantCalling").get(0);
        } catch (MaPSeqDAOException e1) {
            e1.printStackTrace();
        }

        for (Sample sample : sampleSet) {

            logger.info(sample.toString());

            File outputDirectory = new File(sample.getOutputDirectory(), getName());
            File tmpDirectory = new File(outputDirectory, "tmp");
            tmpDirectory.mkdirs();

            Set<FileData> fileDataSet = sample.getFileDatas();

            File vcfFile = null;
            List<File> possibleVCFFileList = WorkflowUtil.lookupFileByJobAndMimeTypeAndWorkflowId(fileDataSet,
                    getWorkflowBeanService().getMaPSeqDAOBean(), GATKUnifiedGenotyper.class, MimeType.TEXT_VCF,
                    variantCallingWorkflow.getId());

            if (possibleVCFFileList != null && !possibleVCFFileList.isEmpty()) {
                vcfFile = possibleVCFFileList.get(0);
            }

            // check the file system
            if (vcfFile == null) {
                File necVariantCallingDir = new File(sample.getOutputDirectory(), "NECVariantCalling");
                if (necVariantCallingDir.exists()) {
                    for (File f : necVariantCallingDir.listFiles()) {
                        if (f.getName().endsWith(".realign.fix.pr.vcf")) {
                            vcfFile = f;
                            break;
                        }
                    }
                }
            }

            if (vcfFile == null) {
                logger.warn("vcf file to process was not found");
                throw new WorkflowException("vcf file to process was not found");
            }

            CondorJobBuilder builder = WorkflowJobFactory
                    .createJob(++count, CalculateMaximumLikelihoodFromVCFCLI.class, attempt.getId(), sample.getId())
                    .siteName(siteName).priority(200);
            builder.addArgument(CalculateMaximumLikelihoodFromVCFCLI.VCF, vcfFile.getAbsolutePath())
                    .addArgument(CalculateMaximumLikelihoodFromVCFCLI.INTERVALLIST, intervalList)
                    .addArgument(CalculateMaximumLikelihoodFromVCFCLI.SAMPLE, vcfFile.getName().replace(".vcf", ""))
                    .addArgument(CalculateMaximumLikelihoodFromVCFCLI.ECDATA, exomeChipData)
                    .addArgument(CalculateMaximumLikelihoodFromVCFCLI.OUTPUT, outputDirectory.getAbsolutePath());

            CondorJob calculateMaximumLikelihoodsFromVCFJob = builder.build();
            logger.info(calculateMaximumLikelihoodsFromVCFJob.toString());
            graph.addVertex(calculateMaximumLikelihoodsFromVCFJob);

        }

        return graph;
    }

    @Override
    public void postRun() throws WorkflowException {
        logger.info("ENTERING postRun()");

        Set<Sample> sampleSet = getAggregatedSamples();

        for (Sample sample : sampleSet) {
            SaveBestMatchAttributeRunnable runnable = new SaveBestMatchAttributeRunnable();
            runnable.setMapseqDAOBean(getWorkflowBeanService().getMaPSeqDAOBean());
            runnable.setSampleId(sample.getId());
            Executors.newSingleThreadExecutor().execute(runnable);
        }

    }

}
