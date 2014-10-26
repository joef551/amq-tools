/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redhat.amq.tools;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

public class ProducerThread implements Runnable {

	private static final String JMSXGROUPID = "JMSXGroupID";
	private ProducerTool pt;
	private Destination destination;
	private int threadID;
	private Connection connection;
	private CountDownLatch latch;
	private long msgsSent = 1L;
	private long milliStart;
	private long milliLast;
	private int transactedBatchCount;
	private StringBuffer strBuffer;
	private Destination tempDest;
	private MessageConsumer consumer;

	public ProducerThread(ProducerTool pt, int threadID, CountDownLatch latch) {
		this.pt = pt;
		this.setThreadID(threadID);
		this.setLatch(latch);
	}

	public void run() {

		System.out.println("Producer " + getThreadID() + " has started");

		// create a shutdown hook that displays the final overall message
		// count for this producer thread
		Runtime.getRuntime().addShutdownHook(new CustomShutdownHook());

		try {
			log("Creating connection...");
			setConnection(getConnectionFactory().createConnection());

			log("Starting connection...");
			getConnection().start();

			Session session = getConnection().createSession(isTransacted(),
					Session.AUTO_ACKNOWLEDGE);

			// create the target destination
			destination = isTopic() ? session.createTopic(getSubject())
					: session.createQueue(getSubject());

			// Create the producer.
			log("Creating producer...");

			MessageProducer producer = session.createProducer(destination);

			// if requested to do so, implement request-reply pattern, but
			// only if transacted is set to false
			if (!isTransacted() && isReply()) {
				if (isVerbose()) {
					log("implementing request-reply");
				}
				tempDest = session.createTemporaryQueue();
				consumer = session.createConsumer(tempDest);
			}

			// specify requested message persistence
			if (isPersistent()) {
				producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			} else {
				producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			}

			// specify whether messages will have a TTL
			if (getTimeToLive() > 0) {
				producer.setTimeToLive(getTimeToLive());
			}

			// specify whther messages have a priority
			if (getPriority() >= 0) {
				producer.setPriority(getPriority());
			}

			// Start sending the messages
			log("Producing ...");

			sendLoop(session, producer);

		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Throwable ignore) {
				}
			}
			// countdown the latch indicating this thread is done
			latch.countDown();
		}

	}

	protected void sendLoop(Session session, MessageProducer producer)
			throws Exception {

		milliStart = System.currentTimeMillis();
		milliLast = System.currentTimeMillis();
		long countLast = 0;
		boolean rolledBack = false;

		// send messageCount number of messages
		for (msgsSent = 1; msgsSent <= getMessageCount(); msgsSent++) {

			TextMessage message = session
					.createTextMessage(createMessageText(msgsSent));

			if (isVerbose()) {
				String msg = message.getText();
				if (msg.length() > 50) {
					msg = msg.substring(0, 50) + "...";
				}
				log("Sending : " + msg);
			}

			// if insructed to do so, use JMSXGROUPID
			if (getGroup() != null) {
				message.setStringProperty(JMSXGROUPID, getGroup());
			}

			// if instructed to do so implement request-reply, but only if
			// non-transacted mode
			if (isReply() && !isTransacted()) {
				message.setJMSReplyTo(tempDest);
				message.setJMSCorrelationID(createRandomString());
			}

			// send message
			producer.send(message);

			// if the producer is transacted and it has reached the transacted
			// batch count, then commit the trx
			if (isTransacted()
					&& (++transactedBatchCount == getTransactedBatchSize())) {
				transactedBatchCount = 0;
				// if instructed to do so, rollback instead of comitting the trx
				if (isRollback()) {
					session.rollback();
					rolledBack = true;
				} else {
					session.commit();
				}
			}

			// if not transacted and request-reply has been requested, then wait
			// for reply
			if (isReply() && !isTransacted()) {
				Message receivedMessage = consumer.receive(5000);
				if (receivedMessage == null) {
					throw new Exception(
							"Reply message not received within allotted time");
				}
			}

			// display metrics only if trx was not rolled back, this is thread
			// #1, and sample size has been reached
			if (!rolledBack && getThreadID() == 1
					&& msgsSent % getSampleSize() == 0) {
				long milliCurrent = System.currentTimeMillis();
				long lastInterval = milliCurrent - milliLast;

				long rateInterval = lastInterval == 0 ? 0
						: (1000 * (msgsSent - countLast)) / lastInterval;

				long rateOverall = (1000 * msgsSent)
						/ (milliCurrent - milliStart);

				log(msgsSent + " messages sent as of second "
						+ (milliCurrent - milliStart) / 1000 + ", sample rate "
						+ rateInterval + "/sec, overall rate " + rateOverall
						+ "/sec");
				milliLast = System.currentTimeMillis();
				countLast = msgsSent;
			}

			// if instructed to do so, sleep in between sends
			if (getSleepTime() > 0) {
				Thread.sleep(getSleepTime());
			}

			rolledBack = false;
		}
	}

	// create message to send
	private String createMessageText(long index) {

		// if user provided a message, then simply return it and don't bother
		// creating one
		if (getMessage() != null) {
			return getMessage();
		}

		if (strBuffer == null) {
			strBuffer = new StringBuffer(getMessageSize());
		} else if (strBuffer.length() > 0) {
			strBuffer.delete(0, strBuffer.length());
		}

		strBuffer.append("Message: " + index + " sent at: " + new Date());
		if (strBuffer.length() > getMessageSize()) {
			return strBuffer.substring(0, getMessageSize());
		}
		for (int i = strBuffer.length(); i < getMessageSize(); i++) {
			strBuffer.append(' ');
		}
		return strBuffer.toString();
	}

	// simple little logger that only prints output from first thread
	private void log(String str) {
		if (getThreadID() == 1) {
			System.out.println(str);
		}
	}

	private String getMessage() {
		return pt.getMessage();
	}

	private long getSampleSize() {
		return pt.getSampleSize();
	}

	private String getGroup() {
		return pt.getGroup();
	}

	private int getPriority() {
		return pt.getPriority();
	}

	private boolean isTransacted() {
		return pt.isTransacted();
	}

	private boolean isPersistent() {
		return pt.isPersistent();
	}

	private boolean isRollback() {
		return pt.isRollback();
	}

	private long getMessageCount() {
		return pt.getMessageCount();
	}

	private int getMessageSize() {
		return pt.getMessageSize();
	}

	private long getSleepTime() {
		return pt.getSleepTime();
	}

	private boolean isVerbose() {
		return pt.isVerbose();
	}

	private String getSubject() {
		return pt.getSubject();
	}

	private long getTimeToLive() {
		return pt.getTimeToLive();
	}

	private boolean isTopic() {
		return pt.isTopic();
	}

	private int getTransactedBatchSize() {
		return pt.getTransactedBatchSize();
	}

	private boolean isReply() {
		return pt.isReply();
	}

	private String createRandomString() {
		Random random = new Random(System.currentTimeMillis());
		long randomLong = random.nextLong();
		return Long.toHexString(randomLong);
	}

	public class CustomShutdownHook extends Thread {

		@Override
		public void run() {
			System.out.println("[" + getThreadID()
					+ "] detected shutdown - total messages sent = "
					+ (msgsSent - 1));
		}
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return pt.getConnectionFactory();
	}

	/**
	 * @return the threadID
	 */
	public int getThreadID() {
		return threadID;
	}

	/**
	 * @param threadID
	 *            the threadID to set
	 */
	public void setThreadID(int threadID) {
		this.threadID = threadID;
	}

	/**
	 * @return the destination
	 */
	public Destination getDestination() {
		return destination;
	}

	/**
	 * @param destination
	 *            the destination to set
	 */
	public void setDestination(Destination destination) {
		this.destination = destination;
	}

	/**
	 * @return the connection
	 */
	public Connection getConnection() {
		return connection;
	}

	/**
	 * @param connection
	 *            the connection to set
	 */
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	/**
	 * @return the latch
	 */
	public CountDownLatch getLatch() {
		return latch;
	}

	/**
	 * @param latch
	 *            the latch to set
	 */
	public void setLatch(CountDownLatch latch) {
		this.latch = latch;
	}

	/**
	 * @return the tempDest
	 */
	public Destination getTempDest() {
		return tempDest;
	}

	/**
	 * @param tempDest
	 *            the tempDest to set
	 */
	public void setTempDest(Destination tempDest) {
		this.tempDest = tempDest;
	}

	/**
	 * @return the consumer
	 */
	public MessageConsumer getConsumer() {
		return consumer;
	}

	/**
	 * @param consumer
	 *            the consumer to set
	 */
	public void setConsumer(MessageConsumer consumer) {
		this.consumer = consumer;
	}

}
