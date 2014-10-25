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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

public class ConsumerThread implements Runnable, MessageListener,
		ExceptionListener {

	private ConsumerTool ct;
	private MessageProducer replyProducer;
	private Destination destination;
	private int threadID;
	private Connection connection;
	private int countLast;
	private int transactedBatchCount;
	private long msgCount;
	private long milliStart;
	private Session session;
	private boolean running;
	private CountDownLatch latch;

	public ConsumerThread(ConsumerTool ct, int threadID, CountDownLatch latch) {
		this.ct = ct;
		this.threadID = threadID;
		this.latch = latch;
	}

	public void run() {

		running = true;
		boolean listener = false;

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out
						.println(getThreadID()
								+ ":Shutting down - total messages read = "
								+ countLast);
			}
		});

		try {
			System.out.println("Consumer " + getThreadID() + " has started");

			log("Creating connection...");
			connection = getConnectionFactory().createConnection();
			if (connection == null) {
				System.out.println(getThreadID()
						+ ": Error, unable to acquire connection");
				return;
			}

			if (isDurable() && isTopic() && getClientId() != null
					&& !getClientId().isEmpty()
					&& !"null".equals(getClientId())) {
				log(getThreadID() + ":setting clientID = " + getClientId()
						+ "...");
				connection.setClientID(getConsumerName() + getThreadID());
			}

			connection.setExceptionListener(this);

			log("Starting connection...");
			connection.start();
			log("Connection started");

			// the ack mode is ignored if the session is transacted
			log("Creating session...");

			session = (isTransacted()) ? connection.createSession(true, 0)
					: connection.createSession(false, getAckMode());

			destination = (isTopic()) ? session.createTopic(getSubject())
					: session.createQueue(getSubject());

			// Create a reply producer in case the producer requests a reply
			replyProducer = session.createProducer(null);
			if (isPersistent()) {
				replyProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
			} else {
				replyProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			}

			MessageConsumer consumer = null;
			if (isDurable() && isTopic()) {
				log("creating durable subscriber...");
				consumer = session.createDurableSubscriber((Topic) destination,
						getConsumerName() + getThreadID());
			} else {
				consumer = (getSelector() == null) ? session
						.createConsumer(destination) : session.createConsumer(
						destination, getSelector());
			}

			log("Ready to consume");

			if (getMaxMessages() > 0) {
				// receive until the specified max number of messages
				// has been received
				consumeMessagesAndClose(connection, session, consumer);
			} else if (getReceiveTimeOut() == 0) {
				// receive indefinitely
				consumer.setMessageListener(this);
				listener = true;
			} else {
				// consume indefinitely, as long as messages
				// continue to arrive within the specified
				// timeout period
				consumeMessagesAndClose(connection, session, consumer,
						getMaxMessages());
			}

		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		} finally {
			if (!listener) {
				latch.countDown();
			}
		}
	}

	public void onMessage(Message message) {
		try {

			if (isVerbose()) {
				if (message instanceof TextMessage) {
					TextMessage txtMsg = (TextMessage) message;
					String msg = txtMsg.getText();
					if (msg.length() > 50) {
						msg = msg.substring(0, 50) + "...";
					}
					log("Received: " + msg);
				} else {
					log("Received: " + message);
				}
			}

			++countLast;

			// if instructed to do so, sleep in between reads
			if (getSleepTime() > 0) {
				try {
					Thread.sleep(getSleepTime());
				} catch (InterruptedException e) {
				}
			}

			// By default, we operate in auto ack mode
			if (isTransacted()) {
				// if we've reached the transacted batch count,
				// then either rollback or commit the trx
				if (++transactedBatchCount == getTransactedBatchSize()) {
					// simulate a rollback if we're not supposed to
					// commit the transaction
					transactedBatchCount = 0;
					if (isRollback()) {
						// System.out.println("rolling back trx, no rollback");
						session.rollback();
					} else {
						// System.out.println("committing trx");
						session.commit();
					}
				}
			}
			// ack the message if we're in the client ack mode
			else if (getAckMode() == Session.CLIENT_ACKNOWLEDGE) {
				// do not acknowledge if instructed to do so
				if (!isRollback()) {
					message.acknowledge();
				}
			}

			// If requested, reply to the message, but only if the session is
			// not transacted.
			if (!isTransacted() && message.getJMSReplyTo() != null) {
				log("replying");
				replyProducer.send(
						message.getJMSReplyTo(),
						session.createTextMessage("Reply: "
								+ message.getJMSMessageID()));
			}

			// Start the clock if this is the first message in the sample
			// interval
			if (msgCount == 0) {
				milliStart = System.currentTimeMillis();
			}

			if (++msgCount == getSampleSize()) {
				double intervalTime = System.currentTimeMillis() - milliStart;
				System.out.println("intervalTime = " + intervalTime);
				if (intervalTime > 0L) {
					double intervalRate = (double) getSampleSize()
							/ (intervalTime / 1000.00);
					System.out.println("Messages per second = " + intervalRate);
				}
				msgCount = 0;
			}

		} catch (JMSException e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		}
	}

	public synchronized void onException(JMSException ex) {
		System.out.println("JMS Exception occured.  Shutting down client.");
		running = false;
	}

	synchronized boolean isRunning() {
		return running;
	}

	/**
	 * Receive messages up until the specifed max number of messages.
	 * 
	 * @param connection
	 * @param session
	 * @param consumer
	 * @throws JMSException
	 * @throws IOException
	 */
	protected void consumeMessagesAndClose(Connection connection,
			Session session, MessageConsumer consumer) throws JMSException,
			IOException {

		log("We are about to wait until we consume: " + getMaxMessages()
				+ " message(s), then we will shutdown");
		try {

			for (long i = 0; i < getMaxMessages() && isRunning();) {
				Message message = consumer.receive(1000);
				if (message != null) {
					i++;
					onMessage(message);
				}
			}
			log(getThreadID()
					+ ":read max number of messages, closing connection");
			consumer.close();
			if (isDurable() && isTopic() && isUnsubscribe()) {
				session.unsubscribe(getConsumerName() + getThreadID());
			}
		} finally {
			session.close();
			connection.close();
		}
	}

	/**
	 * Receive messages as long as they arrive within the timeout specified.
	 * 
	 * @param connection
	 * @param session
	 * @param consumer
	 * @param timeout
	 * @throws JMSException
	 * @throws IOException
	 */
	protected void consumeMessagesAndClose(Connection connection,
			Session session, MessageConsumer consumer, long timeout)
			throws JMSException, IOException {

		log("Consuming as long as messages continue to arrive within: "
				+ timeout + " ms");

		Message message = null;
		while ((message = consumer.receive(timeout)) != null && isRunning()) {
			onMessage(message);
		}
		System.out.println(getThreadID()
				+ ":Message has not arrived within specified time, "
				+ "shutting down.");
		consumer.close();
		if (isDurable() && isTopic() && isUnsubscribe()) {
			session.unsubscribe(getConsumerName());
		}
		session.close();
		connection.close();
	}

	private void log(String str) {
		if (getThreadID() == 1) {
			System.out.println(str);
		}
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return ct.getConnectionFactory();
	}

	public int getThreadID() {
		return threadID;
	}

	public void setThreadID(int threadID) {
		this.threadID = threadID;
	}

	public long getSampleSize() {
		return ct.getSampleSize();
	}

	public boolean isTransacted() {
		return ct.isTransacted();
	}

	public boolean isVerbose() {
		return ct.isVerbose();
	}

	public int getTransactedBatchSize() {
		return ct.getTransactedBatchSize();
	}

	public boolean isTopic() {
		return ct.isTopic();
	}

	public boolean isDurable() {
		return ct.isDurable();
	}

	public boolean isPersistent() {
		return ct.isPersistent();
	}

	public boolean isRollback() {
		return ct.isRollback();
	}

	public String getClientId() {
		return ct.getClientId();
	}

	public int getAckMode() {
		return ct.getAckMode();
	}

	public String getConsumerName() {
		return ct.getConsumerName();
	}

	public long getMaxMessages() {
		return ct.getMaxMessages();
	}

	public long getReceiveTimeOut() {
		return ct.getReceiveTimeOut();
	}

	public long getSleepTime() {
		return ct.getSleepTime();
	}

	public String getSubject() {
		return ct.getSubject();
	}

	public String getSelector() {
		return ct.getSelector();
	}

	public boolean isUnsubscribe() {
		return ct.isUnsubscribe();
	}

}
