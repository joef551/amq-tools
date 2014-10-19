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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * A simple AMQ client for consuming messages
 * 
 */
public class ConsumerTool {
	private String subject = "TOOL.DEFAULT";
	private String user = "joef";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private String consumerName = "Fred";
	private String selector;
	private String clientId = "Fred";
	private int transactedBatchSize = 1;
	private int ackMode = Session.AUTO_ACKNOWLEDGE;
	private int threadCount = 1;
	private boolean transacted;
	private boolean durable;
	private boolean unsubscribe = true;
	private boolean persistent;
	private boolean help;
	private boolean rollback;
	private boolean pauseBeforeShutdown;
	private boolean verbose;
	private boolean topic;
	private long sleepTime;
	private long receiveTimeOut;
	private long milliStart;
	private long maxMessages;
	private long sampleSize = 10000L;
	private ActiveMQConnectionFactory connectionFactory;
	private ExecutorService threadPool;
	private CountDownLatch latch;

	// @formatter:off
	private static final String Usage = "\nusage: java ConsumerTool \n"
			+ "[[user=<userid>]                          default: joef\n" 			
			+ "[password=<password>]                     default: admin\n" 
			+ "[consumerName=<consumer name>]            default: Fred\n" 
			+ "[subject=<queue or topic name>]           default: TOOL.DEFAULT\n"  
			+ "[selector=<header%20=%20%27value%27>]     default: null\n"  
			+ "[url=<broker url>]                        default: " + ActiveMQConnection.DEFAULT_BROKER_URL + "\n" 
			+ "[clientId=<client id>]                    default: Fred\n" 
			+ "[sleepTime=<sleep time between each rcv>] default: 0\n" 
			+ "[maxMessages=<max msgs to rcv>]           default: infinite \n" 
			+ "[transactedBatchSize=<trx batch size>]    default: 1\n" 
			+ "[threadCount=<# of consumer threads]      default: 1\n" 
			+ "[sampleSize=<# of msgs to sample>]        default: 10000\n" 
			+ "[ackMode=<ack mode for receives>]         default: AUTO_ACKNOWLEDGE\n" 			
			+ "[transacted]                              default: false\n" 
			+ "[durable]                                 default: false\n" 
			+ "[unsubscribe]                             default: true \n" 
			+ "[persistent]                              default: false \n" 
			+ "[rollback]                                default: false \n" 
			+ "[topic]]                                  default: false\n";			
	// @formatter:on

	public static void main(String[] args) throws Exception {

		ConsumerTool consumerTool = new ConsumerTool();

		// Read in the command line options
		String[] unknown = CommandLineSupport.setOptions(consumerTool, args);

		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Unknown options: " + Arrays.toString(unknown));
			System.exit(-1);
		}

		// If 'help' request, then simply display usage string and exit
		if (consumerTool.isHelp()) {
			System.out.println(Usage);
			return;
		}

		// Else, start the tool
		consumerTool.start();
	}

	public void start() throws Exception {

		// Display the current settings
		System.out.println("A-MQ ConsumerTool");
		System.out.println("Connecting to URL: " + url);
		System.out.println("Consuming " + (topic ? "topic" : "queue") + ": "
				+ subject);
		System.out.println("Using a " + (durable ? "durable" : "non-durable")
				+ " subscription");
		System.out.println("sleepTime        = " + sleepTime);
		System.out.println("consumerName     = " + consumerName);
		System.out.println("clientId         = " + clientId);
		System.out.println("user             = " + user);
		System.out.println("password         = " + password);
		System.out.println("maxMessages      = " + maxMessages);
		System.out.println("threadCount      = " + threadCount);
		System.out.println("receiveTimeOut   = " + receiveTimeOut);
		System.out.println("persistent       = " + persistent);
		System.out.println("transacted       = " + transacted);
		System.out.println("rollback         = " + rollback);
		System.out.println("unsubscribe      = " + unsubscribe);
		System.out.println("ackMode          = " + ackMode);
		System.out.println("sampleSize       = " + sampleSize);
		System.out.println("selector         = " + selector);

		// don't bother with this if we're in transacted mode
		if (!isTransacted()) {
			switch (ackMode) {
			case Session.CLIENT_ACKNOWLEDGE:
				System.out.println("ackMode          =  CLIENT_ACKNOWLEDGE");
				break;
			case Session.AUTO_ACKNOWLEDGE:
				System.out.println("ackMode          =  AUTO_ACKNOWLEDGE");
				break;
			case Session.DUPS_OK_ACKNOWLEDGE:
				System.out.println("ackMode          =  DUPS_OK_ACKNOWLEDGE");
				break;
			}
		} else {
			System.out.println("ackMode          =  SESSION_TRANSACTED");
		}

		// Create the connection factory used by the consumer threads
		connectionFactory = new ActiveMQConnectionFactory(user, password, url);

		// latch used to wait for consumer threads to complete
		latch = new CountDownLatch(getThreadCount());

		// create the thread pool and start the consumer threads
		threadPool = Executors.newFixedThreadPool(getThreadCount());
		for (int i = 1; i <= getThreadCount(); i++) {
			threadPool.execute(new ConsumerThread(this, i, latch));
		}

		// wait for the cosumers to finish
		latch.await();
		// shutdown the pool
		threadPool.shutdown();		
		System.out.println("Run completed");
	}

	public void setAckMode(String ackMode) {
		if ("CLIENT_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.CLIENT_ACKNOWLEDGE;
		}
		if ("AUTO_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.AUTO_ACKNOWLEDGE;
		}
		if ("DUPS_OK_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.DUPS_OK_ACKNOWLEDGE;
		}
		if ("SESSION_TRANSACTED".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.SESSION_TRANSACTED;
		}
	}

	public boolean isPersistent() {
		return persistent;
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	public int getAckMode() {
		return ackMode;
	}

	public void setClientId(String clientID) {
		this.clientId = clientID;
	}

	public String getClientId() {
		return clientId;
	}

	public void setConsumerName(String consumerName) {
		this.consumerName = consumerName;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public void setDurable(boolean durable) {
		this.durable = durable;
	}

	public boolean getDurable() {
		return durable;
	}

	public boolean isDurable() {
		return durable;
	}

	public long getMaxMessages() {
		return maxMessages;
	}

	public void setMaxMessages(long maximumMessages) {
		this.maxMessages = maximumMessages;
	}

	public void setMillis(long millis) {
		this.milliStart = millis;
	}

	public long getMillis() {
		return milliStart;
	}

	public void setPauseBeforeShutdown(boolean pauseBeforeShutdown) {
		this.pauseBeforeShutdown = pauseBeforeShutdown;
	}

	public boolean getPauseBeforeShutdown() {
		return pauseBeforeShutdown;
	}

	public void setPassword(String pwd) {
		this.password = pwd;
	}

	public String getPassword() {
		return password;
	}

	public void setPersistent(boolean persistent) {
		this.persistent = persistent;
	}

	public void setReceiveTimeOut(long receiveTimeOut) {
		this.receiveTimeOut = receiveTimeOut;
	}

	public long getReceiveTimeOut() {
		return receiveTimeOut;
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

	public void setTopic(boolean topic) {
		this.topic = topic;
	}

	public boolean isTopic() {
		return topic;
	}

	public void setQueue(boolean queue) {
		this.topic = !queue;
	}

	public boolean getQueue(boolean queue) {
		return topic;
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

	public String getUrl() {
		return url;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getUser() {
		return user;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public void setSampleSize(long sampleSize) {
		this.sampleSize = sampleSize;
	}

	public long getSampleSize() {
		return sampleSize;
	}

	public void setTransactedBatchSize(int size) {
		this.transactedBatchSize = size;
	}

	public int getTransactedBatchSize() {
		return transactedBatchSize;
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
	 * @return the noAck
	 */
	public boolean isRollback() {
		return rollback;
	}

	/**
	 * @param noAck
	 *            the noAck to set
	 */
	public void setRollback(boolean rollback) {
		this.rollback = rollback;
	}

	/**
	 * @return the selector
	 */
	public String getSelector() {
		return selector;
	}

	/**
	 * @param selector
	 *            the selector to set
	 */
	public void setSelector(String selector) {
		this.selector = selector.replaceAll("%20", " ");
		this.selector = this.selector.replaceAll("%27", "'");
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
		if (threadCount >= 1) {
			this.threadCount = threadCount;
		}
	}

	/**
	 * @return the unsubscribe
	 */
	public boolean isUnsubscribe() {
		return unsubscribe;
	}

	/**
	 * @param unsubscribe the unsubscribe to set
	 */
	public void setUnsubscribe(boolean unsubscribe) {
		this.unsubscribe = unsubscribe;
	}

}
