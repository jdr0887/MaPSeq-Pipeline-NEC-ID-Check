package edu.unc.mapseq.commons.nec.ic;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.dao.MaPSeqDAOBean;
import edu.unc.mapseq.dao.MaPSeqDAOException;
import edu.unc.mapseq.dao.model.Attribute;
import edu.unc.mapseq.dao.model.FileData;
import edu.unc.mapseq.dao.model.MimeType;
import edu.unc.mapseq.dao.model.Sample;
import edu.unc.mapseq.dao.model.Workflow;
import edu.unc.mapseq.module.gatk2.GATKUnifiedGenotyper;
import edu.unc.mapseq.workflow.WorkflowUtil;

public class SaveBestMatchAttributeRunnable implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(SaveBestMatchAttributeRunnable.class);

    private MaPSeqDAOBean mapseqDAOBean;

    private Long sampleId;

    @Override
    public void run() {

        try {

            Sample sample = mapseqDAOBean.getSampleDAO().findById(this.sampleId);

            File outputDirectory = new File(sample.getOutputDirectory());

            Set<FileData> fileDataSet = sample.getFileDatas();

            Workflow variantCallingWorkflow = mapseqDAOBean.getWorkflowDAO().findByName("NECVariantCalling").get(0);

            File vcfFile = null;
            List<File> possibleVCFFileList = WorkflowUtil.lookupFileByJobAndMimeTypeAndWorkflowId(fileDataSet,
                    mapseqDAOBean, GATKUnifiedGenotyper.class, MimeType.TEXT_VCF, variantCallingWorkflow.getId());

            if (possibleVCFFileList != null && possibleVCFFileList.size() > 0) {
                vcfFile = possibleVCFFileList.get(0);
            }

            if (vcfFile == null) {
                logger.debug("could not find VCF");
                for (File file : outputDirectory.listFiles()) {
                    if (file.getName().endsWith(".realign.fix.pr.vcf")) {
                        vcfFile = file;
                    }
                }
            }

            if (vcfFile == null) {
                logger.warn("vcf file to process was not found: {}", sample.toString());
                return;
            }

            File compareExpectedOutput = new File(outputDirectory, vcfFile.getName().replace(".vcf", ".ec.tsv"));
            List<String> lineList = null;
            try {
                lineList = FileUtils.readLines(compareExpectedOutput);
            } catch (IOException e1) {
                e1.printStackTrace();
            }

            Set<Attribute> attributeSet = sample.getAttributes();

            Set<String> attributeNameSet = new HashSet<String>();

            for (Attribute attribute : attributeSet) {
                attributeNameSet.add(attribute.getName());
            }

            Set<String> synchSet = Collections.synchronizedSet(attributeNameSet);

            if (lineList != null && lineList.size() > 1) {
                String line = lineList.get(1);
                if (synchSet.contains("best_match")) {
                    for (Attribute attribute : attributeSet) {
                        if (attribute.getName().equals("best_match")) {
                            attribute.setValue(line.split("\\t")[1]);
                            break;
                        }
                    }
                } else {
                    attributeSet.add(new Attribute("best_match", line.split("\\t")[1]));
                }
            }
            sample.setAttributes(attributeSet);
            mapseqDAOBean.getSampleDAO().save(sample);
        } catch (MaPSeqDAOException e) {
            e.printStackTrace();
        }

    }

    public MaPSeqDAOBean getMapseqDAOBean() {
        return mapseqDAOBean;
    }

    public void setMapseqDAOBean(MaPSeqDAOBean mapseqDAOBean) {
        this.mapseqDAOBean = mapseqDAOBean;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

}
