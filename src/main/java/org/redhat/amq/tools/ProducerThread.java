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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.ExceptionListener;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Worker thread launched by the ProducerTool
 * 
 */
public class ProducerThread implements Runnable, ExceptionListener {

	private static final String JMSXGROUPID = "JMSXGroupID";
	private static final String JMSXGROUPSEQ = "JMSXGroupSeq";
	private static final String RANDOMGROUP = "random";
	private ProducerTool pt;
	private Destination destination;
	private int threadID;
	private Connection connection;
	private int msgsSent = 1;
	private long totalMsgsSent = 0L;
	private long milliStart;
	private long milliLast;
	private int transactedBatchCount;
	private StringBuffer strBuffer;
	private Destination tempDest;
	private MessageConsumer consumer;
	private String batchGroup;
	private boolean running;
	private String mySubject;
	private CyclicBarrier cyclicBarrier;

	public ProducerThread(ProducerTool pt, int threadID,
			CyclicBarrier cyclicBarrier) {
		this.pt = pt;
		this.setThreadID(threadID);
		this.cyclicBarrier = cyclicBarrier;
	}

	/**
	 * This constructor is used when multiple producer threads are started and
	 * they're all supposed to write to distinct subjects based on the one base
	 * subject name. See ProducerTool for more details.
	 * 
	 * @param pt
	 * @param threadID
	 * @param subject
	 */
	public ProducerThread(ProducerTool pt, int threadID, String subject,
			CyclicBarrier cyclicBarrier) {
		this(pt, threadID, cyclicBarrier);
		this.mySubject = subject;
	}

	public void run() {

		// wait til all threads are at the gate!
		try {
			getCyclicBarrier().await();
			running = true;
		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
			getLatch().countDown();
			return;
		}

		System.out.println("Producer " + getThreadID() + " has started");

		// create a shutdown hook that displays the final overall message
		// count for this producer thread
		Runtime.getRuntime().addShutdownHook(new CustomShutdownHook());

		try {
			log("Creating connection...");
			setConnection(getJmsConnectionFactory().createConnection());

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

			// if requested to do so, implement request-reply pattern
			// NB: Ignored if (transacted == 'true' and (transactedBatchSize > 1
			// or rollback == 'true'))
			if (isReply()) {
				log("implementing request-reply");
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

			// specify whether messages have a priority
			if (getPriority() >= 0) {
				producer.setPriority(getPriority());
			}

			// Start sending the messages
			if (isObjectMessage()) {
				log("Producing object messages ...");
			} else if (isBytesMessage()) {
				log("Producing bytes messages ...");
			} else {
				log("Producing text messages ...");
			}

			int batchCounter = getBatchCount();
			do {
				sendBatch(session, producer);
				if (--batchCounter > 0 && getBatchSleep() > 0) {
					Thread.sleep(getBatchSleep());
				}
			} while (batchCounter > 0 && running);

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
			getLatch().countDown();
		}

	}

	/**
	 * Send a batch of messages, the number of messages in a batch is specified
	 * by messageCount
	 * 
	 * @param session
	 * @param producer
	 * @throws Exception
	 */
	protected void sendBatch(Session session, MessageProducer producer)
			throws Exception {

		milliStart = System.currentTimeMillis();
		milliLast = milliStart;

		long countLast = 0;
		boolean rolledBack = false;

		// if insructed to do so, use JMSXGROUPID
		if (getGroup() != null) {
			// if instructed to do so, create a random group name for this batch
			// of messages
			if (getGroup().equalsIgnoreCase(RANDOMGROUP)) {
				setBatchGroup(UUID.randomUUID().toString());
			} else {
				setBatchGroup(getGroup());
			}
			// log("Using this batch group: " + getBatchGroup());
		}

		// send messageCount number of messages
		for (msgsSent = 1; msgsSent <= getMessageCount() && running; msgsSent++, totalMsgsSent++) {

			Message message = null;
			String msgToSend = createMessageText(totalMsgsSent);

			if (isObjectMessage()) {
				message = session.createObjectMessage(msgToSend);
			} else if (isBytesMessage()) {
				BytesMessage bmessage = session.createBytesMessage();
				bmessage.writeBytes(msgToSend.getBytes());
				message = bmessage;
			} else {
				message = session.createTextMessage(msgToSend);
			}

			if (getGroup() != null) {
				message.setStringProperty(JMSXGROUPID, getBatchGroup());
				if (msgsSent != getMessageCount()) {
					message.setIntProperty(JMSXGROUPSEQ, msgsSent);
				} else {
					// if last message in batch, then close the group sequence
					message.setIntProperty(JMSXGROUPSEQ, -1);
				}
			}

			if (getHeader() != null) {
				message.setStringProperty(getHeader(), getHeaderValue());
			}

			// if instructed to do so implement request-reply, but only if
			// non-transacted mode
			if (isReply()) {
				message.setJMSReplyTo(tempDest);
				message.setJMSCorrelationID(createRandomString());
			}

			// send message
			producer.send(message);

			// if the producer is transacted and it has reached the transacted
			// batch count, then commit the trx
			if (isTransacted()
					&& ++transactedBatchCount == getTransactedBatchSize()) {
				transactedBatchCount = 0;
				// if instructed to do so, rollback instead of comitting the trx
				if (isRollback()) {
					session.rollback();
					rolledBack = true;
				} else {
					session.commit();
				}
			}

			// if request-reply has been requested, then wait
			// for consumer's reply
			if (running && isReply()) {
				Message receivedMessage = consumer.receive(getReplyWaitTime());
				if (receivedMessage == null) {
					throw new Exception(
							"Reply message not received within allotted time");
				}
			}

			// display metrics only if trx was not rolled back, this is thread
			// #1, and sample size has been reached
			if (running && !rolledBack && getThreadID() == 1
					&& msgsSent % getSampleSize() == 0) {

				// get the current time
				long milliCurrent = System.currentTimeMillis();

				// determine how much time has elapsed since
				// last sample was taken
				long lastInterval = milliCurrent - milliLast;

				// determine rate for last sample interval
				long rateInterval = lastInterval == 0 ? 0
						: (1000 * (msgsSent - countLast)) / lastInterval;

				long rateOverall = (1000 * msgsSent)
						/ (milliCurrent - milliStart);

				log(msgsSent + " messages sent, sample rate = " + rateInterval
						+ "/sec, overall rate = " + rateOverall + "/sec");

				// record this last sample time
				milliLast = milliCurrent;

				// record the number of messages sent when this sample was taken
				countLast = msgsSent;
			}

			// if instructed to do so, sleep in between sends
			if (getSleepTime() > 0) {
				Thread.sleep(getSleepTime());
			}

			rolledBack = false;
		}
	}

	public synchronized void onException(JMSException ex) {
		running = false;
		System.out.println(getThreadID()
				+ ":JMS Exception occured.  Shutting down consumer thread.");
		if (getThreadID() == 1) {
			ex.printStackTrace();
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
		} else {
			for (int i = strBuffer.length(); i < getMessageSize() - 1; i++) {
				strBuffer.append('X');
			}
			strBuffer.append('E');
		}
		return strBuffer.toString();
	}

	// simple little logger that only prints output from first thread
	private void log(String str) {
		if (isVerbose()) {
			System.out.println("[" + getThreadID() + "] " + str);
		} else if (getThreadID() == 1) {
			System.out.println("[" + getThreadID() + "] " + str);
		}
	}

	private long getReplyWaitTime() {
		return pt.getReplyWaitTime();
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

	private String getSubject() {
		return (getMySubject() != null) ? getMySubject() : pt.getSubject();
	}

	private long getTimeToLive() {
		return pt.getTimeToLive();
	}

	private String getHeader() {
		return pt.getHeader();
	}

	private String getHeaderValue() {
		return pt.getHeaderValue();
	}

	private boolean isTopic() {
		return pt.isTopic();
	}

	private boolean isObjectMessage() {
		return pt.isObjectMessage();
	}

	private boolean isBytesMessage() {
		return pt.isBytesMessage();
	}

	private int getTransactedBatchSize() {
		return pt.getTransactedBatchSize();
	}

	private boolean isVerbose() {
		return pt.isVerbose();
	}

	private boolean isReply() {
		if (isTransacted() && (getTransactedBatchSize() > 1 || isRollback())) {
			return false;
		}
		return pt.isReply();
	}

	public CountDownLatch getLatch() {
		return pt.getLatch();
	}

	public int getBatchCount() {
		return pt.getBatchCount();
	}

	public long getBatchSleep() {
		return pt.getBatchSleep();
	}

	private boolean isQpid() {
		return pt.isQpid();
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
					+ (totalMsgsSent));
		}
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return pt.getConnectionFactory();
	}

	public ConnectionFactory getJmsConnectionFactory() {
		return pt.getJmsConnectionFactory();
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

	/**
	 * @return the batchGroup
	 */
	public String getBatchGroup() {
		return batchGroup;
	}

	/**
	 * @param batchGroup
	 *            the batchGroup to set
	 */
	public void setBatchGroup(String batchGroup) {
		this.batchGroup = batchGroup;
	}

	/**
	 * @return the totalMsgsSent
	 */
	public long getTotalMsgsSent() {
		return totalMsgsSent;
	}

	/**
	 * @param totalMsgsSent
	 *            the totalMsgsSent to set
	 */
	public void setTotalMsgsSent(long totalMsgsSent) {
		this.totalMsgsSent = totalMsgsSent;
	}

	/**
	 * @return the mySubject
	 */
	public String getMySubject() {
		return mySubject;
	}

	/**
	 * @param mySubject
	 *            the mySubject to set
	 */
	public void setMySubject(String mySubject) {
		this.mySubject = mySubject;
	}

	/**
	 * @return the cyclicBarrier
	 */
	public CyclicBarrier getCyclicBarrier() {
		return cyclicBarrier;
	}

	/**
	 * @param cyclicBarrier
	 *            the cyclicBarrier to set
	 */
	public void setCyclicBarrier(CyclicBarrier cyclicBarrier) {
		this.cyclicBarrier = cyclicBarrier;
	}

}
