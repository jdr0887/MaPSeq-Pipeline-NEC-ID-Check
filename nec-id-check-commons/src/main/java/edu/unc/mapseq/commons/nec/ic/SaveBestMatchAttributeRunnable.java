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

import edu.unc.mapseq.dao.AttributeDAO;
import edu.unc.mapseq.dao.MaPSeqDAOBean;
import edu.unc.mapseq.dao.MaPSeqDAOException;
import edu.unc.mapseq.dao.SampleDAO;
import edu.unc.mapseq.dao.model.Attribute;
import edu.unc.mapseq.dao.model.Sample;

public class SaveBestMatchAttributeRunnable implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(SaveBestMatchAttributeRunnable.class);

    private MaPSeqDAOBean mapseqDAOBean;

    private Long sampleId;

    @Override
    public void run() {
        SampleDAO sampleDAO = mapseqDAOBean.getSampleDAO();
        AttributeDAO attributeDAO = mapseqDAOBean.getAttributeDAO();

        try {

            Sample sample = sampleDAO.findById(this.sampleId);

            File outputDirectory = new File(sample.getOutputDirectory(), "NECIDCheck");

            File compareExpectedOutput = null;

            if (outputDirectory.exists()) {
                for (File file : outputDirectory.listFiles()) {
                    if (file.getName().endsWith(".realign.fix.pr.ec.tsv")) {
                        compareExpectedOutput = file;
                        break;
                    }
                }
            }

            if (compareExpectedOutput == null) {
                File alternateDir = new File(sample.getOutputDirectory(), "NEC");
                for (File file : alternateDir.listFiles()) {
                    if (file.getName().endsWith(".realign.fix.pr.ec.tsv")) {
                        compareExpectedOutput = file;
                        break;
                    }
                }
            }

            if (compareExpectedOutput == null || (compareExpectedOutput != null && !compareExpectedOutput.exists())) {
                logger.error("could not find ec.tsv file");
                return;
            }

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
                            attributeDAO.save(attribute);
                            break;
                        }
                    }
                } else {
                    Attribute attribute = new Attribute("best_match", line.split("\\t")[1]);
                    attribute.setId(attributeDAO.save(attribute));
                    attributeSet.add(attribute);
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
