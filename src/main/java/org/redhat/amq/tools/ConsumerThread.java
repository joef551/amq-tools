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
import java.util.Random;
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

public class ConsumerThread extends Thread implements Runnable,
		MessageListener, ExceptionListener {

	private ConsumerTool ct;
	private MessageProducer replyProducer;
	private Destination destination;
	private int threadID;
	private Connection connection;
	private int countLast;
	private int countConsumed;
	private int transactedBatchCount;
	private long msgCount;
	private long milliStart;
	private Session session;
	private boolean running;
	private Random randomizer;
	private boolean listener;
	private MessageConsumer consumer;

	public ConsumerThread(ConsumerTool ct, int threadID, Connection connection) {
		this.ct = ct;
		this.threadID = threadID;
		this.connection = connection;
	}

	public void run() {

		running = true;

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println(getThreadID()
						+ ":Shutting down: total messages received = " + countLast
						+ ", total messages consumed = " + countConsumed);
			}
		});

		try {
			System.out.println("Consumer " + getThreadID() + " has started");

			if (connection == null) {
				log("Creating connection...");
				connection = getConnectionFactory().createConnection();
				if (connection == null) {
					System.out.println(getThreadID()
							+ ": Error, unable to acquire connection");
					return;
				}
			} else {
				log("Sharing connection...");
			}

			// TODO: You cannot share a connection and do the following!!
			if (isDurable() && isTopic() && getClientId() != null
					&& !getClientId().isEmpty()
					&& !"null".equals(getClientId())) {
				log(getThreadID() + ":setting clientID = " + getClientId()
						+ "...");
				connection.setClientID(getConsumerName() + getThreadID());
			}

			if (!isShareConnection()) {
				connection.setExceptionListener(this);
				log("Starting connection...");
				connection.start();
				log("Connection started");
			}

			log("Creating session...");

			// the ack mode is ignored if the session is transacted
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

			log("Ready to consume");
			if (getMaxMessages() > 0) {
				// receive until the specified max number of messages
				// has been received. note that there may be many batches to
				// consume with different consumers
				int batchCounter = getBatchCount();
				do {
					consumeMessagesAndClose(connection, session,
							createConsumer());
				} while (--batchCounter != 0 && isRunning());
				log("closing");
				closeThings(session, connection);
			} else if (getReceiveTimeOut() == 0) {
				// receive indefinitely
				listener = true;
				consumer = createConsumer();
				consumer.setMessageListener(this);
			} else {
				// consume indefinitely, as long as messages
				// continue to arrive within the specified
				// timeout period
				consumeMessagesAndClose(connection, session, createConsumer(),
						getMaxMessages());
				closeThings(session, connection);
			}
		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		} finally {
			if (!listener) {
				getLatch().countDown();
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

			// Increment the total message count. The count keeps track of the
			// total number of messages consumed; regardless of whether they've
			// subsequently rolled back as part of a trx.
			++countLast;

			// keeps track of only those messages that are consumed and not
			// rolled back
			++countConsumed;

			// if instructed to do so, sleep in between reads
			// TODO: give this a random option
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
					if (isRollback()) {
						// System.out.println("rolling back trx, no rollback");
						countConsumed -= transactedBatchCount;
						session.rollback();
					} else {
						session.commit();
					}
					transactedBatchCount = 0;
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
				if (isVerbose()) {
					log("replying");
				}
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
				long currentTime = System.currentTimeMillis();
				double intervalTime = currentTime - milliStart;
				if (intervalTime > 0L) {
					double intervalRate = (double) getSampleSize()
							/ (intervalTime / 1000.00);
					System.out.println("[" + getThreadID() + "]"
							+ " Interval time = " + intervalTime
							+ "\tTotal msg count = " + countLast
							+ "\tMgs per second = " + intervalRate);
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
		System.out.println(getThreadID()
				+ ":JMS Exception occured.  Shutting down consumer thread.");
		if (getThreadID() == 1) {
			ex.printStackTrace();
		}
		running = false;
		if (listener) {
			if (getConsumer() != null) {
				try {
					getConsumer().close();
				} catch (Exception ignore) {
				}
			}
			getLatch().countDown();
		}
	}

	synchronized boolean isRunning() {
		return running;
	}

	private MessageConsumer createConsumer() throws Exception {
		MessageConsumer consumer;
		if (isDurable() && isTopic()) {
			log("creating durable subscriber...");
			consumer = session.createDurableSubscriber((Topic) destination,
					getConsumerName() + getThreadID());
		} else {
			consumer = (getSelector() == null) ? session
					.createConsumer(destination) : session.createConsumer(
					destination, getSelector());
		}
		return consumer;
	}

	private void closeThings(Session session, Connection connection) {
		try {
			if (session != null) {
				session.close();
			}
			if (!isShareConnection() && connection != null) {
				connection.close();
			}
		} catch (Exception ignore) {
		}
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

		// log("We are about to wait until we consume: " + getMaxMessages()
		// + " message(s), then we will shutdown");
		// if requested to do so, randomize the batch count a little
		long msgCount = getMaxMessages();
		if (isBatchRandom()) {
			msgCount += getRandomizer().nextInt(11);
		}

		for (long i = 0; i < msgCount && isRunning();) {
			Message message = consumer.receive(1000);
			if (message != null) {
				i++;
				onMessage(message);
			}
		}
		// log(getThreadID() + ":read max number of messages [" +
		// getMaxMessages()
		// + "], closing consumer");
		consumer.close();
		if (isDurable() && isTopic() && isUnsubscribe()) {
			session.unsubscribe(getConsumerName() + getThreadID());
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

		// log("Consuming as long as messages continue to arrive within: "
		// + timeout + " ms");

		Message message = null;
		while (isRunning() && (message = consumer.receive(timeout)) != null) {
			onMessage(message);
		}
		System.out.println(getThreadID()
				+ ":Message has not arrived within specified time, "
				+ "shutting down.");
		consumer.close();
		if (isDurable() && isTopic() && isUnsubscribe()) {
			session.unsubscribe(getConsumerName());
		}
		// session.close();
		// connection.close();
	}

	private void log(String str) {
		if (getThreadID() == 1) {
			System.out.println(str);
		}
	}

	private ActiveMQConnectionFactory getConnectionFactory() {
		return ct.getConnectionFactory();
	}

	private CountDownLatch getLatch() {
		return ct.getLatch();
	}

	private int getThreadID() {
		return threadID;
	}

	private long getSampleSize() {
		return ct.getSampleSize();
	}

	private boolean isTransacted() {
		return ct.isTransacted();
	}

	private boolean isVerbose() {
		return ct.isVerbose();
	}

	private int getTransactedBatchSize() {
		return ct.getTransactedBatchSize();
	}

	private boolean isTopic() {
		return ct.isTopic();
	}

	private boolean isDurable() {
		return ct.isDurable();
	}

	private boolean isPersistent() {
		return ct.isPersistent();
	}

	private boolean isRollback() {
		return ct.isRollback();
	}

	private String getClientId() {
		return ct.getClientId();
	}

	private int getAckMode() {
		return ct.getAckMode();
	}

	private String getConsumerName() {
		return ct.getConsumerName();
	}

	private long getMaxMessages() {
		return ct.getMaxMessages();
	}

	private long getReceiveTimeOut() {
		return ct.getReceiveTimeOut();
	}

	private long getSleepTime() {
		return ct.getSleepTime();
	}

	private String getSubject() {
		return ct.getSubject();
	}

	private String getSelector() {
		return ct.getSelector();
	}

	private boolean isUnsubscribe() {
		return ct.isUnsubscribe();
	}

	private int getBatchCount() {
		return ct.getBatchCount();
	}

	private boolean isShareConnection() {
		return ct.isShareConnection();
	}

	private boolean isBatchRandom() {
		return ct.isBatchRandom();
	}

	private Random getRandomizer() {
		if (randomizer == null) {
			randomizer = new Random();
		}
		return randomizer;
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
