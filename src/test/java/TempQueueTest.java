import static org.junit.Assert.*;
import org.junit.Test;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;

public class TempQueueTest extends TestCase {

	@Test
	public void testCreateTempQueueAndSend() throws Exception {

		final ConnectionFactory cf = new ActiveMQConnectionFactory(
				"vm://localhost?broker.persistent=false&broker.useJmx=false");

		// Create connect/session/producer for sending to the temp queue
		final Connection sendConnection = cf.createConnection();
		sendConnection.start();
		final Session sendSession = sendConnection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);

		final MessageProducer producer = sendSession.createProducer(null);
		producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
		producer.setDisableMessageID(true);
		producer.setDisableMessageTimestamp(true);

		final ObjectMessage sendMsg = sendSession.createObjectMessage();
		
		System.out.println("entering main test loop");

		// Keep sending to the temp queue until it fails
		for (int i = 0; i < 10000; i++) {
			// Create connection/session for creating the temp queue
			final Connection connection = cf.createConnection();
			connection.start();
			final Session session = connection.createSession(false,
					Session.AUTO_ACKNOWLEDGE);

			final TemporaryQueue dest = session.createTemporaryQueue();
			final MessageConsumer consumer = session.createConsumer(dest);

			sendMsg.setObject(dest.toString());
			try {
				producer.send(dest, sendMsg);
			} catch (final JMSException e) {
				System.err.println("Failed on iteration " + i);
				throw e;
			}

			final Message msg = consumer.receive();
			assertNotNull(msg);
			final ObjectMessage objMsg = (ObjectMessage) msg;
			assertEquals(dest.toString(), objMsg.getObject());

			consumer.close();
			session.close();
			connection.stop();
			connection.close();
		}

		System.out.println("tests done");
		producer.close();
		sendSession.close();
		sendConnection.stop();
		sendConnection.close();
	}
}