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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;

import javax.jms.ExceptionListener;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.pool.PooledConnectionFactory;

import static org.redhat.amq.tools.CommandLineSupport.setOptions;
import static org.redhat.amq.tools.CommandLineSupport.readProps;

/**
 * A simple AMQ client for consuming messages. Capable of spawning multiple
 * client consumer threads.
 * 
 */
public class ConsumerTool implements ExceptionListener {
	private String subject = "TOOL.DEFAULT";
	private String user = "admin";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private String consumerName = "Fred";
	private String selector;
	private String clientId = "Fred";
	private String props;
	private int transactedBatchSize = 1;
	private int ackMode = Session.AUTO_ACKNOWLEDGE;
	private int threadCount = 1;
	private int batchCount = 1;
	private int maxConnections = 2;
	private int idleTimeout;
	private int expiryTimeout;
	private boolean transacted;
	private boolean durable;
	private boolean unsubscribe = true;
	private boolean persistent;
	private boolean shareConnection;
	private boolean sharedDestination = true;
	private boolean help;
	private boolean rollback;
	private boolean pauseBeforeShutdown;
	private boolean verbose;
	private boolean topic;
	private boolean pooled;
	private boolean optimizeAcknowledge;
	private boolean dispatchAsync;
	private long sleepTime;
	private long receiveTimeout;
	private long sampleResetTime = 10000L;
	private long milliStart;
	private long maxMessages;
	private long sampleSize = 5000L;
	private ActiveMQConnectionFactory connectionFactory;
	private PooledConnectionFactory pooledConnectionFactory;
	private ConnectionFactory jmsConnectionFactory;

	private Connection connection;
	private ExecutorService threadPool;
	private CountDownLatch latch;

	// @formatter:off
	private final String Usage = "\nusage: java ConsumerTool \n"
			+ "[[user=<userid>]                          default: " + user + "\n" 			
			+ "[password=<password>]                     default: " + password + "\n" 
			+ "[consumerName=<consumer name>]            default: " + consumerName + "\n" 
			+ "[subject=<queue or topic name>]           default: " + subject + "\n"  
			+ "[selector=<header%20=%20%27value%27>]     default: null\n"  
			+ "[url=<broker url>]                        default: " + ActiveMQConnection.DEFAULT_BROKER_URL + "\n" 
			+ "[clientId=<client id>]                    default: " + clientId + "\n" 
			+ "[sleepTime=<sleep time between each rcv>] default: " + sleepTime + "\n" 
			+ "[maxMessages=<max msgs to rcv>]           default: infinite \n" 
			+ "[transactedBatchSize=<trx batch size>]    default: " + transactedBatchSize + "\n" 
			+ "[batchCount=<batch count>]                default: " + batchCount + "\n" 
			+ "[threadCount=<# of consumer threads]      default: " + threadCount + "\n" 
			+ "[sampleSize=<# of msgs to sample>]        default: " + sampleSize + "\n" 
			+ "[ackMode=<ack mode for receives>]         default: AUTO_ACKNOWLEDGE\n" 
			+ "[props=<path to props file>]              default: not used\n" 
			+ "[transacted]                              default: " + transacted + "\n" 
			+ "[durable]                                 default: " + durable + "\n" 
			+ "[unsubscribe]                             default: " + unsubscribe + "\n" 
			+ "[persistent]                              default: " + persistent + "\n" 
			+ "[rollback]                                default: " + rollback + "\n" 
			+ "[shareConnection]                         default: " + shareConnection + "\n" 
			+ "[pooled]                                  default: " + pooled + "\n" 
			+ "[sharedDestination]                       default: " + sharedDestination + "\n" 
			+ "[maxConnections]                          default: " + maxConnections + "\n" 
			+ "[idleTimeout]                             default: " + idleTimeout + "\n" 
			+ "[receiveTimeout]                          default: " + receiveTimeout + "\n" 
			+ "[expiryTimeout]                           default: " + expiryTimeout + "\n" 
			+ "[sampleResetTime]                         default: " + sampleResetTime + "\n" 
			+ "[topic]]                                  default: " + topic + "\n";			
	// @formatter:on

	@SuppressWarnings("null")
	public static void main(String[] args) throws Exception {

		ConsumerTool consumerTool = new ConsumerTool();

		// Read in the command line options
		String[] unknown = setOptions(consumerTool, args);

		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Unknown options: " + Arrays.toString(unknown));
			System.exit(-1);
		}

		// If 'help' request, then simply display usage string and exit
		if (consumerTool.isHelp()) {
			System.out.println(consumerTool.Usage);
			return;
		}

		// if a props file was specified, then use the properties
		// specified in that file
		if (consumerTool.getProps() != null) {
			ArrayList<String> props = readProps(consumerTool.getProps());
			// if there were properties, add them
			if (props.size() > 0) {
				unknown = setOptions(consumerTool,
						props.toArray(new String[props.size()]));
				// Exit if end user entered unknown options n properties file
				if (unknown.length > 0) {
					System.out.println("Unknown options: "
							+ Arrays.toString(unknown));
					System.exit(-1);
				}
			}
		}

		// Start the tool
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
		System.out.println("sleepTime           = " + sleepTime);
		System.out.println("consumerName        = " + consumerName);
		System.out.println("clientId            = " + clientId);
		System.out.println("user                = " + user);
		System.out.println("password            = " + password);
		System.out.println("maxMessages         = " + maxMessages);
		System.out.println("threadCount         = " + threadCount);
		System.out.println("persistent          = " + persistent);
		System.out.println("transacted          = " + transacted);
		System.out.println("transactedBatchSize = " + transactedBatchSize);
		System.out.println("rollback            = " + rollback);
		System.out.println("unsubscribe         = " + unsubscribe);
		System.out.println("ackMode             = " + ackMode);
		System.out.println("props               = " + props);
		System.out.println("sampleSize          = " + sampleSize);
		System.out.println("selector            = " + selector);
		System.out.println("shareConnection     = " + shareConnection);
		System.out.println("batchCount          = " + batchCount);
		System.out.println("sharedDestination   = " + sharedDestination);
		System.out.println("pooled              = " + pooled);
		System.out.println("maxConnections      = " + maxConnections);
		System.out.println("idleTimeout         = " + idleTimeout);
		System.out.println("receiveTimeout      = " + receiveTimeout);
		System.out.println("expiryTimeout       = " + expiryTimeout);
		System.out.println("sampleResetTime     = " + sampleResetTime);

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
			case ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE:
				System.out
						.println("ackMode          =  INDIVIDUAL_ACKNOWLEDGE");
				break;
			}
		} else {
			System.out.println("ackMode          =  SESSION_TRANSACTED");
		}

		setConnectionFactory(new ActiveMQConnectionFactory(user, password, url));

		// Create the connection factory used by the consumer threads. Note that
		// it doesn't make sense to use a pooled connection factory if the
		// connection is shared
		if (!isShareConnection() && isPooled()) {
			setPooledConnectionFactory(new PooledConnectionFactory(
					getConnectionFactory()));
			getPooledConnectionFactory().setMaxConnections(getMaxConnections());
			getPooledConnectionFactory().setExpiryTimeout(getExpiryTimeout());
			getPooledConnectionFactory().setIdleTimeout(getIdleTimeout());
			setJmsConnectionFactory(getPooledConnectionFactory());
		} else {
			setJmsConnectionFactory(getConnectionFactory());
		}

		// if the connection is not to be shared amongst the worker threads,
		// then 'connection' will be null and the worker thread will create one
		// for itself.
		if (isShareConnection()) {
			connection = getJmsConnectionFactory().createConnection();
			connection.setExceptionListener(this);
			System.out.println("Starting shared connection...");
			connection.start();
			System.out.println("Shared connection started.");
		}

		// latch used to wait for consumer threads to complete
		setLatch(new CountDownLatch(getThreadCount()));

		// create the thread pool and start the consumer threads
		threadPool = Executors.newFixedThreadPool(getThreadCount());
		for (int i = 1; i <= getThreadCount(); i++) {
			if (getThreadCount() == 1 || isSharedDestination()) {
				threadPool.execute(new ConsumerThread(this, i, connection));
			} else {
				// create distinct destinations if there is more than one thread
				// to invoke and we've been asked not to have all the threads
				// share a destination; i.e., read from the same destination. in
				// this case, each distinct destination's name is constructed as
				// follows: "getSubject()<thread number>. For example,
				// TOOL.DEFAULT0, TOOL.DEFAULT1, etc.
				threadPool.execute(new ConsumerThread(this, i, connection,
						getSubject() + Integer.toString(i)));
			}
		}

		// wait for the cosumers to finish
		latch.await();
		// shutdown the pool
		threadPool.shutdownNow();

		if (isShareConnection()) {
			connection.close();
		}
		System.out.println("Run completed");
		// Force an exit!
		System.exit(0);
	}

	public void setAckMode(String ackMode) {

		if (ackMode == null || ackMode.isEmpty()) {
			return;
		} else if ("CLIENT_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.CLIENT_ACKNOWLEDGE;
		} else if ("AUTO_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.AUTO_ACKNOWLEDGE;
		} else if ("DUPS_OK_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.DUPS_OK_ACKNOWLEDGE;
		} else if ("SESSION_TRANSACTED".equalsIgnoreCase(ackMode)) {
			this.ackMode = Session.SESSION_TRANSACTED;
		} else if ("INDIVIDUAL_ACKNOWLEDGE".equalsIgnoreCase(ackMode)) {
			this.ackMode = ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE;
		}
	}

	public void onException(JMSException ex) {
		System.out.println("JMS Exception occured.  Shutting down client.");
		ex.printStackTrace();
		threadPool.shutdown();
		System.exit(1);

	}

	public boolean isPersistent() {
		return persistent;
	}

	public int getAckMode() {
		return ackMode;
	}

	public CountDownLatch getLatch() {
		return latch;
	}

	public void setLatch(CountDownLatch latch) {
		this.latch = latch;
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

	public void setReceiveTimeout(long receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}

	public long getReceiveTimeout() {
		return receiveTimeout;
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
	 * @param unsubscribe
	 *            the unsubscribe to set
	 */
	public void setUnsubscribe(boolean unsubscribe) {
		this.unsubscribe = unsubscribe;
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
	 * @return the shareConnection
	 */
	public boolean isShareConnection() {
		return shareConnection;
	}

	/**
	 * @param shareConnection
	 *            the shareConnection to set
	 */
	public void setShareConnection(boolean shareConnection) {
		this.shareConnection = shareConnection;
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
	 * @return the pooled
	 */
	public boolean isPooled() {
		return pooled;
	}

	/**
	 * @param pooled
	 *            the pooled to set
	 */
	public void setPooled(boolean pooled) {
		this.pooled = pooled;
	}

	/**
	 * @return the pooledConnectionFactory
	 */
	public PooledConnectionFactory getPooledConnectionFactory() {
		return pooledConnectionFactory;
	}

	/**
	 * @param pooledConnectionFactory
	 *            the pooledConnectionFactory to set
	 */
	public void setPooledConnectionFactory(
			PooledConnectionFactory pooledConnectionFactory) {
		this.pooledConnectionFactory = pooledConnectionFactory;
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	/**
	 * @param pooledConnectionFactory
	 *            the pooledConnectionFactory to set
	 */
	public void setConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	/**
	 * @return the jmsConnectionFactory
	 */
	public ConnectionFactory getJmsConnectionFactory() {
		return jmsConnectionFactory;
	}

	/**
	 * @param jmsConnectionFactory
	 *            the jmsConnectionFactory to set
	 */
	public void setJmsConnectionFactory(ConnectionFactory jmsConnectionFactory) {
		this.jmsConnectionFactory = jmsConnectionFactory;
	}

	/**
	 * @return the maxConnections
	 */
	public int getMaxConnections() {
		return maxConnections;
	}

	/**
	 * @param maxConnections
	 *            the maxConnections to set
	 */
	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	/**
	 * @return the idleTimeout
	 */
	public int getIdleTimeout() {
		return idleTimeout;
	}

	/**
	 * @param idleTimeout
	 *            the idleTimeout to set
	 */
	public void setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	/**
	 * @return the expiryTimeout
	 */
	public int getExpiryTimeout() {
		return expiryTimeout;
	}

	/**
	 * @param expiryTimeout
	 *            the expiryTimeout to set
	 */
	public void setExpiryTimeout(int expiryTimeout) {
		this.expiryTimeout = expiryTimeout;
	}

	/**
	 * @return the sampleTime
	 */
	public long getSampleResetTime() {
		return sampleResetTime;
	}

	/**
	 * @param sampleTime
	 *            the sampleTime to set
	 */
	public void setSampleResetTime(long sampleResetTime) {
		this.sampleResetTime = sampleResetTime;
	}

	/**
	 * @return the optimizeAcknowledge
	 */
	public boolean isOptimizeAcknowledge() {
		return optimizeAcknowledge;
	}

	/**
	 * @param optimizeAcknowledge
	 *            the optimizeAcknowledge to set
	 */
	public void setOptimizeAcknowledge(boolean optimizeAcknowledge) {
		this.optimizeAcknowledge = optimizeAcknowledge;
	}

	/**
	 * @return the dispatchAsync
	 */
	public boolean isDispatchAsync() {
		return dispatchAsync;
	}

	/**
	 * @param dispatchAsync
	 *            the dispatchAsync to set
	 */
	public void setDispatchAsync(boolean dispatchAsync) {
		this.dispatchAsync = dispatchAsync;
	}

	public boolean isSharedDestination() {
		return sharedDestination;
	}

	public void setSharedDestination(boolean sharedDestination) {
		this.sharedDestination = sharedDestination;
	}

	/**
	 * @return the props
	 */
	public String getProps() {
		return props;
	}

	/**
	 * @param props
	 *            the props to set
	 */
	public void setProps(String props) {
		this.props = props;
	}

}
