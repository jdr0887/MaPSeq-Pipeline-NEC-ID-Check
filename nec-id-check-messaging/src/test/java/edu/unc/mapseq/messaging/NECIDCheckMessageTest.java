package edu.unc.mapseq.messaging;

import java.util.Date;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.time.DateFormatUtils;
import org.junit.Test;

public class NECIDCheckMessageTest {

    @Test
    public void testQueue() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(String.format("nio://%s:61616",
                "biodev2.its.unc.edu"));
        String name = String.format("jdr-test-%s", DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(new Date()));
        // String name = "121109_UNC10-SN254_0391_BC0YNYACXX_L1_053007Sm_IDCHK";
        Connection connection = null;
        Session session = null;
        try {
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue("queue/nec.idcheck");
            MessageProducer producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            String format = "{\"entities\":[{\"entityType\":\"Sample\",\"id\":\"%d\"},{\"entityType\":\"WorkflowRun\",\"name\":\"%s\"}]}";
            producer.send(session.createTextMessage(String.format(format, 138714, name)));
        } catch (JMSException e) {
            e.printStackTrace();
        } finally {
            try {
                session.close();
                connection.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }

        // QName serviceQName = new QName("http://ws.mapseq.unc.edu", "WorkflowRunService");
        // QName portQName = new QName("http://ws.mapseq.unc.edu", "WorkflowRunPort");
        // Service service = Service.create(serviceQName);
        // String host = "biodev2.its.unc.edu";
        // service.addPort(portQName, SOAPBinding.SOAP11HTTP_MTOM_BINDING,
        // String.format("http://%s:%d/cxf/WorkflowRunService", host, 8181));
        // WorkflowRunService workflowRunService = service.getPort(WorkflowRunService.class);
        //
        // boolean hasValidStatus = false;
        //
        // while (!hasValidStatus) {
        //
        // List<WorkflowRun> workflowRunList = workflowRunService.findByName(name);
        // Collections.sort(workflowRunList, new Comparator<WorkflowRun>() {
        // @Override
        // public int compare(WorkflowRun o1, WorkflowRun o2) {
        // return o2.getCreationDate().compareTo(o1.getCreationDate());
        // }
        // });
        //
        // WorkflowRun workflowRun = workflowRunList.get(0);
        // System.out.println(workflowRun.getId());
        //
        // WorkflowRunStatusType status = workflowRun.getStatus();
        // System.out.println(status);
        // hasValidStatus = status.equals(WorkflowRunStatusType.PENDING);
        // try {
        // Thread.sleep(20 * 1000);
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        // }

    }
}
