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
import org.renci.jlrm.condor.CondorJobEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.commons.nec.ic.SaveBestMatchAttributeRunnable;
import edu.unc.mapseq.dao.MaPSeqDAOException;
import edu.unc.mapseq.dao.model.FileData;
import edu.unc.mapseq.dao.model.HTSFSample;
import edu.unc.mapseq.dao.model.MimeType;
import edu.unc.mapseq.dao.model.SequencerRun;
import edu.unc.mapseq.dao.model.Workflow;
import edu.unc.mapseq.module.gatk2.GATKUnifiedGenotyper;
import edu.unc.mapseq.module.ic.CalculateMaximumLikelihoodFromVCFCLI;
import edu.unc.mapseq.workflow.AbstractWorkflow;
import edu.unc.mapseq.workflow.WorkflowException;
import edu.unc.mapseq.workflow.WorkflowJobFactory;
import edu.unc.mapseq.workflow.WorkflowUtil;

public class NECIDCheckWorkflow extends AbstractWorkflow {

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

        Set<HTSFSample> htsfSampleSet = getAggregateHTSFSampleSet();
        logger.info("htsfSampleSet.size(): {}", htsfSampleSet.size());

        String siteName = getWorkflowBeanService().getAttributes().get("siteName");
        String intervalList = getWorkflowBeanService().getAttributes().get("intervalList");
        String exomeChipData = getWorkflowBeanService().getAttributes().get("exomeChipData");
        // String expectedExomeChip2HTSFMap = getWorkflowBeanService().getAttributes().get("expectedExomeChip2HTSFMap");

        Workflow variantCallingWorkflow = null;
        try {
            variantCallingWorkflow = getWorkflowBeanService().getMaPSeqDAOBean().getWorkflowDAO()
                    .findByName("NECVariantCalling");
        } catch (MaPSeqDAOException e1) {
            e1.printStackTrace();
        }

        for (HTSFSample htsfSample : htsfSampleSet) {

            SequencerRun sequencerRun = htsfSample.getSequencerRun();
            File outputDirectory = createOutputDirectory(sequencerRun.getName(), htsfSample,
                    getName().replace("IDCheck", ""), getVersion());

            Set<FileData> fileDataSet = htsfSample.getFileDatas();

            File vcfFile = null;
            List<File> possibleVCFFileList = WorkflowUtil.lookupFileByJobAndMimeTypeAndWorkflowId(fileDataSet,
                    getWorkflowBeanService().getMaPSeqDAOBean(), GATKUnifiedGenotyper.class, MimeType.TEXT_VCF,
                    variantCallingWorkflow.getId());

            if (possibleVCFFileList != null && possibleVCFFileList.size() > 0) {
                vcfFile = possibleVCFFileList.get(0);
            }

            if (vcfFile == null) {
                // database may not have the file mapping correct
                if (fileDataSet != null) {
                    for (FileData fileData : fileDataSet) {
                        if (fileData.getMimeType().equals(MimeType.TEXT_VCF)) {
                            possibleVCFFileList.add(new File(fileData.getPath(), fileData.getName()));
                        }
                    }
                }
            }

            if (possibleVCFFileList != null && possibleVCFFileList.size() > 0) {
                vcfFile = possibleVCFFileList.get(0);
            }

            if (vcfFile == null) {
                logger.warn("vcf file to process was not found: {}", htsfSample.toString());
                throw new WorkflowException("vcf file to process was not found");
            }

            // new job
            /*
             * CondorJob subsetVCFJob = WorkflowJobFactory.createJob(++count, SubsetVCFCLI.class, getWorkflowPlan(),
             * htsfSample); subsetVCFJob.setSiteName(siteName); subsetVCFJob.addArgument(SubsetVCFCLI.INTERVALLIST,
             * intervalList); subsetVCFJob.addArgument(SubsetVCFCLI.VCF, vcfFile.getAbsolutePath()); File
             * subsetVCFOutput = new File(outputDirectory, vcfFile.getName().replace(".vcf", ".subset.vcf"));
             * subsetVCFJob.addArgument(SubsetVCFCLI.OUTPUT, subsetVCFOutput.getAbsolutePath());
             * graph.addVertex(subsetVCFJob);
             * 
             * // new job CondorJob flattenVCFJob = WorkflowJobFactory.createJob(++count, FlattenVCFCLI.class,
             * getWorkflowPlan(), htsfSample); flattenVCFJob.setSiteName(siteName);
             * flattenVCFJob.addArgument(FlattenVCFCLI.INTERVALLIST, intervalList); flattenVCFJob.addArgument(
             * FlattenVCFCLI.SAMPLE, String.format("%s_%s_L%03d_%s", sequencerRun.getName(), htsfSample.getBarcode(),
             * htsfSample.getLaneIndex(), htsfSample.getName())); flattenVCFJob.addArgument(FlattenVCFCLI.VCF,
             * subsetVCFOutput.getAbsolutePath()); File flattenVCFOutput = new File(outputDirectory,
             * vcfFile.getName().replace(".vcf", ".fvcf")); flattenVCFJob.addArgument(FlattenVCFCLI.OUTPUT,
             * flattenVCFOutput.getAbsolutePath()); graph.addVertex(flattenVCFJob); graph.addEdge(subsetVCFJob,
             * flattenVCFJob);
             * 
             * // new job CondorJob calculateMaximumLikelihoodsJob = WorkflowJobFactory.createJob(++count,
             * CalculateMaximumLikelihoodsCLI.class, getWorkflowPlan(), htsfSample);
             * calculateMaximumLikelihoodsJob.setSiteName(siteName);
             * calculateMaximumLikelihoodsJob.addArgument(CalculateMaximumLikelihoodsCLI.ECDATA, exomeChipData);
             * calculateMaximumLikelihoodsJob.addArgument(CalculateMaximumLikelihoodsCLI.FLATVCF,
             * flattenVCFOutput.getAbsolutePath()); File calculateMaximumLikelihoodsOutput = new File(outputDirectory,
             * vcfFile.getName().replace(".vcf", ".ec.tsv"));
             * calculateMaximumLikelihoodsJob.addArgument(CalculateMaximumLikelihoodsCLI.OUTPUT,
             * calculateMaximumLikelihoodsOutput.getAbsolutePath()); graph.addVertex(calculateMaximumLikelihoodsJob);
             * graph.addEdge(flattenVCFJob, calculateMaximumLikelihoodsJob);
             */

            // new job
            /*
             * CondorJob compareExpectedJob = WorkflowJobFactory.createJob(++count, CompareExpectedCLI.class,
             * getWorkflowPlan(), htsfSample); compareExpectedJob.setSiteName(siteName);
             * compareExpectedJob.addArgument(CompareExpectedCLI.MAXIMUMLIKELIHOOD,
             * calculateMaximumLikelihoodsOutput.getAbsolutePath());
             * compareExpectedJob.addArgument(CompareExpectedCLI.EXPECTEDEC2HTSFMAP, expectedExomeChip2HTSFMap); File
             * compareExpectedOutput = new File(outputDirectory, vcfFile.getName().replace(".vcf", ".idchk.txt"));
             * compareExpectedJob.addArgument(CompareExpectedCLI.OUTPUT, compareExpectedOutput.getAbsolutePath());
             * graph.addVertex(compareExpectedJob); graph.addEdge(calculateMaximumLikelihoodsJob, compareExpectedJob);
             */

            CondorJob calculateMaximumLikelihoodsFromVCFJob = WorkflowJobFactory.createJob(++count,
                    CalculateMaximumLikelihoodFromVCFCLI.class, getWorkflowPlan(), htsfSample);
            calculateMaximumLikelihoodsFromVCFJob.setSiteName(siteName);
            calculateMaximumLikelihoodsFromVCFJob.addArgument(CalculateMaximumLikelihoodFromVCFCLI.VCF,
                    vcfFile.getAbsolutePath());
            calculateMaximumLikelihoodsFromVCFJob.addArgument(CalculateMaximumLikelihoodFromVCFCLI.INTERVALLIST,
                    intervalList);
            calculateMaximumLikelihoodsFromVCFJob.addArgument(CalculateMaximumLikelihoodFromVCFCLI.SAMPLE, vcfFile
                    .getName().replace(".vcf", ""));
            calculateMaximumLikelihoodsFromVCFJob.addArgument(CalculateMaximumLikelihoodFromVCFCLI.ECDATA,
                    exomeChipData);
            calculateMaximumLikelihoodsFromVCFJob.addArgument(CalculateMaximumLikelihoodFromVCFCLI.OUTPUT,
                    outputDirectory.getAbsolutePath());
            calculateMaximumLikelihoodsFromVCFJob.setPriority(200);
            graph.addVertex(calculateMaximumLikelihoodsFromVCFJob);

        }

        return graph;
    }

    @Override
    public void postRun() throws WorkflowException {
        logger.info("ENTERING postRun()");

        Set<HTSFSample> htsfSampleSet = getAggregateHTSFSampleSet();
        logger.info("htsfSampleSet.size(): {}", htsfSampleSet.size());

        for (HTSFSample htsfSample : htsfSampleSet) {
            SaveBestMatchAttributeRunnable runnable = new SaveBestMatchAttributeRunnable();
            runnable.setMapseqDAOBean(getWorkflowBeanService().getMaPSeqDAOBean());
            runnable.setHtsfSampleId(htsfSample.getId());
            Executors.newSingleThreadExecutor().execute(runnable);
        }

    }

}
