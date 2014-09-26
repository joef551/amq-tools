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

import java.util.Arrays;
import java.util.Date;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * A simple JMS tool for publishing messages
 */
public class ProducerTool implements Runnable {

	private static final String JMSXGROUPID = "JMSXGroupID";
	private Destination destination;
	private String user = "joef";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private String subject = "TOOL.DEFAULT";
	private String message;
	private String group;
	private boolean topic;
	private boolean transacted;
	private boolean persistent;
	private boolean verbose;
	private boolean rollback;
	private boolean help;
	private long milliStart;
	private long milliLast;
	private long msgsSent = 1L;
	private long messageCount = 10000L;
	private long sampleSize = 10000L;
	private long sleepTime;
	private long timeToLive;
	private int transactedBatchSize = 1;
	private int transactedBatchCount;
	private int messageSize = 255;
	private int priority = -1;

	// @formatter:off
		private final String Usage = "\nusage: java ProducerTool \n"
				+ "[[user=<userid>]                           default:" + user + "\n" 			
				+ "[password=<password>]                      default:" + password + "\n" 
				+ "[subject=<queue or topic name>]            default:" + subject + "\n"  
				+ "[url=<broker url>]                         default: " + url + "\n" 
				+ "[group=<group id>]                         default: " + "null" + "\n" 
				+ "[priority=<priority (0-9)>]                default: " + "not set" + "\n" 
				+ "[timeToLive=<msg time to live>]            default: " + "not set" + "\n" 
				+ "[sleepTime=<sleep time between each send>] default: " + sleepTime + "\n" 
				+ "[message=<msg-to-send>]                    default: one will be created\n" 
				+ "[messageSize=<size of msg to send>]        default: " + messageSize + "\n" 
				+ "[messageCount=<# of msgs to send>]         default: " + messageCount + "\n" 
				+ "[sampleSize=<# of msgs to sample>]         default: " + sampleSize + "\n" 
				+ "[transactedBatchSize=<trx batch size>]     default: 1\n" 					
				+ "[transacted]                               default: false\n" 
				+ "[durable]                                  default: false\n" 
				+ "[persistent]                               default: false \n" 
				+ "[topic]]                                   default: false\n";	
	// @formatter:on

	public static void main(String[] args) {
		ProducerTool producerTool = new ProducerTool();
		// Read in the command line options
		String[] unknown = CommandLineSupport.setOptions(producerTool, args);
		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Unknown options: " + Arrays.toString(unknown));
			System.exit(-1);
		}

		if (producerTool.isHelp()) {
			System.out.println(producerTool.Usage);
			return;
		}

		producerTool.run();
	}

	public void run() {
		Connection connection = null;
		try {
			// creating a shutdown hook that displays the final overall message
			// count
			CustomShutdownHook customShutdownHook = new CustomShutdownHook();
			Runtime.getRuntime().addShutdownHook(customShutdownHook);

			System.out.println("A-MQ ProducerTool");

			// @formatter:off
			System.out.println("url                  = " + url);										
			System.out.println("user                 = " + user);
			System.out.println("password             = " + password);
			System.out.println("subject              = " + subject);
			System.out.println("message              = " + getMessage());
			System.out.println("topic                = " + topic);			
			System.out.println("group                = " + group);
			System.out.println("persistent           = " + persistent);
			System.out.println("transacted           = " + transacted);
			System.out.println("rollback             = " + rollback);			
			System.out.println("sampleSize           = " + sampleSize);		
			System.out.println("messageCount         = " + messageCount);	
			System.out.println("messageSize          = " + messageSize);
			System.out.println("sleepTime            = " + sleepTime);
			System.out.println("timeToLive           = " + timeToLive);
			System.out.println("priority             = " + priority);
			System.out.println("transactedBatchSize  = " + transactedBatchSize);
			// @formatter:on

			// Create the connection.
			ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
					user, password, url);

			System.out.print("Creating connection...");
			connection = connectionFactory.createConnection();

			System.out.println("Starting connection...");
			connection.start();

			// Create the session
			System.out.print("Connection started, creating session...");

			Session session = connection.createSession(transacted,
					Session.AUTO_ACKNOWLEDGE);

			destination = topic ? session.createTopic(subject) : session
					.createQueue(subject);

			// Create the producer.
			System.out.print("Creating producer...");

			MessageProducer producer = session.createProducer(destination);

			if (persistent) {
				producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			} else {
				producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			}

			if (timeToLive > 0) {
				producer.setTimeToLive(timeToLive);
			}

			if (getPriority() >= 0) {
				producer.setPriority(getPriority());
			}

			System.out.println("Producing");

			// Start sending messages
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
		}
	}

	protected void sendLoop(Session session, MessageProducer producer)
			throws Exception {
		milliStart = System.currentTimeMillis();
		milliLast = System.currentTimeMillis();
		long countLast = 0;
		boolean rolledBack = false;

		for (msgsSent = 1; msgsSent <= messageCount; msgsSent++) {

			TextMessage message = session
					.createTextMessage(createMessageText(msgsSent));

			if (verbose) {
				String msg = message.getText();
				if (msg.length() > 50) {
					msg = msg.substring(0, 50) + "...";
				}
				System.out.println("Sending : " + msg);
			}

			if (getGroup() != null) {
				message.setStringProperty(JMSXGROUPID, getGroup());
			}

			producer.send(message);

			if (transacted && (++transactedBatchCount == transactedBatchSize)) {
				transactedBatchCount = 0;
				if (rollback) {
					session.rollback();
					rolledBack = true;
				} else {
					session.commit();
				}
			}

			if (!rolledBack) {
				if (msgsSent % sampleSize == 0) {
					long milliCurrent = System.currentTimeMillis();
					long lastInterval = milliCurrent - milliLast;
					long rateInterval = lastInterval == 0 ? 0
							: (1000 * (msgsSent - countLast)) / lastInterval;
					long rateOverall = (1000 * msgsSent)
							/ (milliCurrent - milliStart);
					System.out.println(msgsSent
							+ " messages sent as of second "
							+ (milliCurrent - milliStart) / 1000
							+ ", sample rate " + rateInterval
							+ "/sec, overall rate " + rateOverall + "/sec");
					System.out.flush();
					milliLast = System.currentTimeMillis();
					countLast = msgsSent;
				}
			} else {
				rolledBack = false;
			}

			if (sleepTime > 0) {
				Thread.sleep(sleepTime);
			}

		}

	}

	private static StringBuffer strBuffer = null;

	private String createMessageText(long index) {

		if (getMessage() != null) {
			return getMessage();
		}

		if (strBuffer == null) {
			strBuffer = new StringBuffer(messageSize);
		} else if (strBuffer.length() > 0) {
			strBuffer.delete(0, strBuffer.length());
		}

		strBuffer.append("Message: " + index + " sent at: " + new Date());
		if (strBuffer.length() > messageSize) {
			return strBuffer.substring(0, messageSize);
		}
		for (int i = strBuffer.length(); i < messageSize; i++) {
			strBuffer.append(' ');
		}
		return strBuffer.toString();
	}

	public void setPersistent(boolean durable) {
		this.persistent = durable;
	}

	public void setRollback(boolean rback) {
		this.rollback = rback;
	}

	public void setMessageCount(long messageCount) {
		this.messageCount = messageCount;
	}

	public void setMessageSize(int messageSize) {
		this.messageSize = messageSize;
	}

	public void setPassword(String pwd) {
		this.password = pwd;
	}

	public void setSleepTime(long sleepTime) {
		this.sleepTime = sleepTime;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public void setTimeToLive(long timeToLive) {
		this.timeToLive = timeToLive;
	}

	public void setTopic(boolean topic) {
		this.topic = topic;
	}

	public void setQueue(boolean queue) {
		this.topic = !queue;
	}

	public void setTransacted(boolean transacted) {
		this.transacted = transacted;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setTransactedBatchSize(int size) {
		this.transactedBatchSize = size;
	}

	public void setSampleSize(long sampleSize) {
		this.sampleSize = sampleSize;
	}

	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}

	/**
	 * @param help
	 *            the help to set
	 */
	public void setHelp(boolean help) {
		this.help = help;
	}

	/**
	 * @return the group
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * @param group
	 *            the group to set
	 */
	public void setGroup(String group) {
		this.group = group;
	}

	/**
	 * @return the priority
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * @param priority
	 *            the priority to set
	 */
	public void setPriority(int priority) throws IllegalArgumentException {
		if (priority >= 0 && priority <= 9) {
			this.priority = priority;
		} else {
			throw new IllegalArgumentException("invalid priority value = "
					+ priority);
		}
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message
	 *            the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	public class CustomShutdownHook extends Thread {

		@Override
		public void run() {
			System.out.println("Shutdown detected - total messages sent = "
					+ (msgsSent - 1));
		}
	}
}
