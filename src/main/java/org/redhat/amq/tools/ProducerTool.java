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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * A simple JMS tool for publishing messages
 */
public class ProducerTool {

	private ActiveMQConnectionFactory connectionFactory;
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
	private boolean reply;
	private boolean rollback;
	private boolean help;
	private long messageCount = 10000L;
	private long sampleSize = 5000L;
	private long sleepTime;
	private long timeToLive;
	private long batchSleep;
	private int transactedBatchSize = 1;
	private int messageSize = 255;
	private int batchCount = 1;
	private int priority = -1;
	private int threadCount = 1;
	private CountDownLatch latch;
	private ExecutorService threadPool;

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
		+ "[batchCount=<# of msg batches to send]     default: " + batchCount + "\n"
		+ "[batchSleep=<sleep time between batch>]    default: " + batchSleep + "\n"
		+ "[threadCount=<# of producer threads]       default: 1\n" 
		+ "[transactedBatchSize=<trx batch size>]     default: 1\n" 					
		+ "[transacted]                               default: false\n" 
		+ "[durable]                                  default: false\n" 
		+ "[persistent]                               default: false \n" 
		+ "[reply]                                    default: false \n" 
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

		// If 'help' request, then simply display usage string and exit
		if (producerTool.isHelp()) {
			System.out.println(producerTool.Usage);
			return;
		}

		// start up the tool and worker threads
		producerTool.start();
	}

	public void start() {

		// if end-user has request to use request-reply pattern and also
		// requested a transacted session, then set request-reply back to false
		// if rollback has been requested or transacted batch size is greater
		// than 1
		if (isReply() && isTransacted()
				&& (isRollback() || getTransactedBatchSize() > 1)) {
			setReply(false);
		}

		try {

			// display settings for this run
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
			System.out.println("batchCount           = " + batchCount);
			System.out.println("batchSleep           = " + batchSleep);
			System.out.println("threadCount          = " + threadCount);
			System.out.println("sleepTime            = " + sleepTime);
			System.out.println("timeToLive           = " + timeToLive);
			System.out.println("priority             = " + priority);
			System.out.println("reply                = " + reply);
			System.out.println("transactedBatchSize  = " + transactedBatchSize);
			// @formatter:on

			// Create the connection factory.
			connectionFactory = new ActiveMQConnectionFactory(user, password,
					url);

			// latch used to wait for consumer threads to complete
			setLatch(new CountDownLatch(getThreadCount()));

			// create the thread pool and start the consumer threads
			setThreadPool(Executors.newFixedThreadPool(getThreadCount()));
			for (int i = 1; i <= getThreadCount(); i++) {
				getThreadPool().execute(new ProducerThread(this, i, latch));
			}

			// wait for the producer threads to finish
			getLatch().await();

		} catch (Exception e) {
			System.out.println("Caught: " + e);
			e.printStackTrace();
		} finally {
			if (getThreadPool() != null) {
				getThreadPool().shutdown();
			}
		}
		System.out.println("Run completed");
	}

	public void setPersistent(boolean durable) {
		this.persistent = durable;
	}

	public boolean isPersistent() {
		return persistent;
	}

	public void setRollback(boolean rback) {
		this.rollback = rback;
	}

	public boolean isRollback() {
		return rollback;
	}

	public void setMessageCount(long messageCount) {
		this.messageCount = messageCount;
	}

	public long getMessageCount() {
		return messageCount;
	}

	public void setMessageSize(int messageSize) {
		this.messageSize = messageSize;
	}

	public int getMessageSize() {
		return messageSize;
	}

	public void setPassword(String pwd) {
		this.password = pwd;
	}

	public void setSleepTime(long sleepTime) {
		this.sleepTime = sleepTime;
	}

	public long getSleepTime() {
		return sleepTime;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
	}

	public void setTimeToLive(long timeToLive) {
		this.timeToLive = timeToLive;
	}

	public long getTimeToLive() {
		return timeToLive;
	}

	public void setTopic(boolean topic) {
		this.topic = topic;
	}

	public boolean isTopic() {
		return topic;
	}

	public void setQueue(boolean queue) {
		this.topic = !queue;
	}

	public boolean isQueue() {
		return !topic;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public void setTransacted(boolean transacted) {
		this.transacted = transacted;
	}

	public boolean isTransacted() {
		return transacted;
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

	public int getTransactedBatchSize() {
		return transactedBatchSize;
	}

	public void setSampleSize(long sampleSize) {
		this.sampleSize = sampleSize;
	}

	public long getSampleSize() {
		return sampleSize;
	}

	public boolean isHelp() {
		return help;
	}

	public void setHelp(boolean help) {
		this.help = help;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public int getPriority() {
		return priority;
	}

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

	public ActiveMQConnectionFactory getConnectionFactory() {
		return connectionFactory;
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
	 * @return the threadCount
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 * @param threadCount
	 *            the threadCount to set
	 */
	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	/**
	 * @return the threadPool
	 */
	public ExecutorService getThreadPool() {
		return threadPool;
	}

	/**
	 * @param threadPool
	 *            the threadPool to set
	 */
	public void setThreadPool(ExecutorService threadPool) {
		this.threadPool = threadPool;
	}

	/**
	 * @return the reply
	 */
	public boolean isReply() {
		return reply;
	}

	/**
	 * @param reply
	 *            the reply to set
	 */
	public void setReply(boolean reply) {
		this.reply = reply;
	}

	/**
	 * @return the batchCount
	 */
	public int getBatchCount() {
		return batchCount;
	}

	/**
	 * @param batchCount
	 *            the batchCount to set
	 */
	public void setBatchCount(int batchCount) {
		this.batchCount = batchCount;
	}

	/**
	 * @return the batchSleep
	 */
	public long getBatchSleep() {
		return batchSleep;
	}

	/**
	 * @param batchSleep
	 *            the batchSleep to set
	 */
	public void setBatchSleep(long batchSleep) {
		this.batchSleep = batchSleep;
	}

}
