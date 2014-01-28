package edu.unc.mapseq.commands.nec.ic;

import java.util.concurrent.Executors;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;

import edu.unc.mapseq.commons.nec.ic.SaveBestMatchAttributeRunnable;
import edu.unc.mapseq.dao.MaPSeqDAOBean;

@Command(scope = "nec-ic", name = "save-best-match-attribute", description = "")
public class SaveBestMatchAttributeAction extends AbstractAction {

    @Argument(index = 0, name = "htsfSampleId", description = "htsfSampleId", required = true, multiValued = false)
    private Long htsfSampleId;

    private MaPSeqDAOBean maPSeqDAOBean;

    public SaveBestMatchAttributeAction() {
        super();
    }

    @Override
    public Object doExecute() {

        SaveBestMatchAttributeRunnable runnable = new SaveBestMatchAttributeRunnable();
        runnable.setMapseqDAOBean(maPSeqDAOBean);
        runnable.setHtsfSampleId(htsfSampleId);
        Executors.newSingleThreadExecutor().execute(runnable);

        return null;
    }

    public Long getHtsfSampleId() {
        return htsfSampleId;
    }

    public void setHtsfSampleId(Long htsfSampleId) {
        this.htsfSampleId = htsfSampleId;
    }

    public MaPSeqDAOBean getMaPSeqDAOBean() {
        return maPSeqDAOBean;
    }

    public void setMaPSeqDAOBean(MaPSeqDAOBean maPSeqDAOBean) {
        this.maPSeqDAOBean = maPSeqDAOBean;
    }

}
