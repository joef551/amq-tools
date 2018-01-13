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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.redhat.amq.tools.ConsumerTool.isQpidUrl;

import javax.naming.InitialContext;
import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

import static org.redhat.amq.tools.CommandLineSupport.setOptions;
import static org.redhat.amq.tools.CommandLineSupport.readProps;

/**
 * A simple JMS producer tool for ActiveMQ
 */
public class ProducerTool {

	private ActiveMQConnectionFactory connectionFactory;
	private ConnectionFactory jmsConnectionFactory;
	private String user = "admin";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private String subject = "TOOL.DEFAULT";
	private String message;
	private String group;
	private String header;
	private String headerValue;
	private String props;
	private String headers;
	private Map<String, String> headersMap;
	private boolean topic;
	private boolean transacted;
	private boolean persistent;
	private boolean verbose;
	private boolean reply;
	private boolean rollback;
	private boolean help;
	private boolean syncSend;
	private boolean jndi;
	private boolean sharedDestination = true;
	private boolean qpid;
	private long messageCount = 5000L;
	private long sampleSize = 5000L;
	private long sleepTime;
	private long timeToLive;
	private long batchSleep;
	private long replyWaitTime = 5000L;
	private int transactedBatchSize = 1;
	private int messageSize = 256;
	private int batchCount = 1;
	private int priority = -1;
	private int threadCount = 1;
	private CountDownLatch latch;
	private ExecutorService threadPool;

	InitialContext initialContext;

	enum MessageType {
		TEXT, OBJECT, BYTES;
		public boolean isText() {
			return this == TEXT;
		}

		public boolean isObject() {
			return this == OBJECT;
		}

		public boolean isBytes() {
			return this == BYTES;
		}
	}

	private CyclicBarrier cyclicBarrier;

	// the default message type
	private MessageType messageType = MessageType.TEXT;

	// @formatter:off
	private final String Usage = "\nusage: java ProducerTool \n"
		+ "[[user=<userid>]                           default:" + getUser() + "\n" 			
		+ "[password=<password>]                      default:" + getPassword() + "\n" 
		+ "[subject=<queue or topic name>]            default:" + subject + "\n"  
		+ "[url=<broker url>]                         default: " + url + "\n" 
		+ "[group=<group id>]                         default: " + "null" + "\n" 
		+ "[priority=<priority (0-9)>]                default: " + "not set" + "\n" 
		+ "[timeToLive=<msg time to live>]            default: " + "not set" + "\n" 
		//+ "[header=<key:value>]                       default: not set\n" 
		+ "[headers=<key:value,...,key:value>]        default: not set\n" 
		+ "[sleepTime=<sleep time between each send>] default: " + sleepTime + "\n" 
		+ "[message=<msg-to-send>]                    default: one will be created\n" 
		+ "[messageSize=<size of msg to send>]        default: " + messageSize + "\n" 
		+ "[messageCount=<# of msgs to send>]         default: " + messageCount + "\n" 
		+ "[sampleSize=<# of msgs to sample>]         default: " + sampleSize + "\n"
		+ "[batchCount=<# of msg batches to send]     default: " + batchCount + "\n"
		+ "[batchSleep=<sleep time between batch>]    default: " + batchSleep + "\n"
		+ "[replyWaitTime=<time to wait for reply]    default: " + replyWaitTime + "\n"
		+ "[messageType=<message type to send]        default: " + messageType + "\n"
		+ "[threadCount=<# of producer threads]       default: " + threadCount + "\n" 
		+ "[transactedBatchSize=<trx batch size>]     default: " + transactedBatchSize + "\n"		
		+ "[syncSend]                                 default: " + syncSend + "\n" 
		+ "[props=<path to props file>]               default: not used\n" 
		+ "[transacted]                               default: " + transacted + "\n" 		
		+ "[persistent]                               default: " + persistent + "\n" 
		+ "[reply]                                    default: " + reply + "\n" 
		+ "[jndi]                                     default: " + jndi + "\n"	
	    + "[sharedDestination]                        default: " + sharedDestination + "\n" 
	    + "[qpid]]                                    default: " + qpid + "\n"	
		+ "[topic]]                                   default: " + topic + "\n";	
	
	// @formatter:on

	public static void main(String[] args) throws Exception {

		ProducerTool producerTool = new ProducerTool();

		// Read in the command line options
		String[] unknown = setOptions(producerTool, args);

		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Found one or more unknown options: "
					+ Arrays.toString(unknown));
			System.exit(-1);
		}

		// If 'help' request, then simply display usage string and exit
		if (producerTool.isHelp()) {
			System.out.println(producerTool.Usage);
			return;
		}

		// if a props file was specified, then use the properties
		// specified in that file
		if (producerTool.getProps() != null) {
			ArrayList<String> props = readProps(producerTool.getProps());
			// if there were properties, add them
			if (props.size() > 0) {
				unknown = setOptions(producerTool,
						props.toArray(new String[props.size()]));
				// Exit if end user entered unknown options n properties file
				if (unknown.length > 0) {
					System.out.println("Unknown options: "
							+ Arrays.toString(unknown));
					System.exit(-1);
				}
			}
		}

		// start up the tool and worker threads
		producerTool.start();
	}

	public void start() {

		// if end-user has requested to use request-reply pattern and also
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
			System.out.println("user                 = " + getUser());
			System.out.println("password             = " + getPassword());
			System.out.println("subject              = " + subject);
			System.out.println("message              = " + getMessage());
			System.out.println("topic                = " + topic);	
			System.out.println("syncSend             = " + syncSend);
			System.out.println("group                = " + group);
			System.out.println("header               = " + header);
			System.out.println("headerValue          = " + headerValue);
			System.out.println("persistent           = " + persistent);
			System.out.println("transacted           = " + transacted);
			System.out.println("jndi                = " + jndi);
			System.out.println("transactedBatchSize  = " + transactedBatchSize);
			System.out.println("rollback             = " + rollback);			
			System.out.println("sampleSize           = " + sampleSize);		
			System.out.println("messageCount         = " + messageCount);	
			System.out.println("messageSize          = " + messageSize);
			System.out.println("batchCount           = " + batchCount);
			System.out.println("batchSleep           = " + batchSleep);
			System.out.println("replyWaitTime        = " + replyWaitTime);
			System.out.println("threadCount          = " + threadCount);
			System.out.println("sleepTime            = " + sleepTime);
			System.out.println("timeToLive           = " + timeToLive);
			System.out.println("priority             = " + priority);
			System.out.println("reply                = " + reply);
			System.out.println("messageType          = " + messageType);
			System.out.println("props                = " + props);
			System.out.println("qpid                 = " + qpid);
			System.out.println("sharedDestination    = " + sharedDestination);	
			// @formatter:on

			if (isVerbose()) {
				if (getHeadersMap() != null && !getHeadersMap().isEmpty()) {
					for (Map.Entry<String, String> entry : getHeadersMap()
							.entrySet()) {
						System.out.println("header key:value = "
								+ entry.getKey() + ":" + entry.getValue());

					}
				}
			}

			// Create the connection factory.

			if (isJndi()) {
				// if we've been told to use JNDI, then fetch the
				// connection factory from the JNDI
				initialContext = new InitialContext();
				setJmsConnectionFactory((ConnectionFactory) initialContext
						.lookup("ConnectionFactory"));
				if (getJmsConnectionFactory() instanceof ActiveMQConnectionFactory) {
					((ActiveMQConnectionFactory) getJmsConnectionFactory())
							.setUserName(getUser());
					((ActiveMQConnectionFactory) getJmsConnectionFactory())
							.setPassword(getPassword());
				} else if (getJmsConnectionFactory() instanceof org.apache.qpid.jms.JmsConnectionFactory) {
					((org.apache.qpid.jms.JmsConnectionFactory) getJmsConnectionFactory())
							.setUsername(getUser());
					((org.apache.qpid.jms.JmsConnectionFactory) getJmsConnectionFactory())
							.setPassword(getPassword());
				} else if (getJmsConnectionFactory() instanceof ActiveMQJMSConnectionFactory) {
					((ActiveMQJMSConnectionFactory) getJmsConnectionFactory())
							.setUser(getUser());
					((ActiveMQJMSConnectionFactory) getJmsConnectionFactory())
							.setPassword(getPassword());
				} else {
					throw new Exception("ERROR: unknown connection factory: "
							+ getJmsConnectionFactory().getClass().getName());
				}
				System.out.println("Connecting with JNDI context: "
						+ initialContext.getEnvironment());

			} else if (!isQpid()) {
				System.out.println("Connecting to URL: " + url);
				// if !qpid, then we're using the ActiveMQ connection factory
				connectionFactory = new ActiveMQConnectionFactory(getUser(),
						getPassword(), getUrl());

				if (isTopic() && isSyncSend()) {
					System.out.println("setting sync send for topic");
					connectionFactory.setAlwaysSyncSend(true);
				}
				setJmsConnectionFactory(connectionFactory);
			} else {
				System.out.println("Connecting to URL: " + url);
				// using a qpid connection factory
				// if URL is set to default openwire, then switch to default
				// qpid
				if (getUrl().equals(ActiveMQConnection.DEFAULT_BROKER_URL)) {
					setUrl("amqp://localhost:5672");
				} else {
					if (!isQpidUrl(url)) {
						System.out
								.println("ERROR: this url doesn't have a valid qpid scheme: "
										+ url);
						System.exit(1);
					}
				}
				setJmsConnectionFactory(new org.apache.qpid.jms.JmsConnectionFactory(
						getUser(), getPassword(), getUrl()));
			}

			// latch used to wait for producer threads to complete
			setLatch(new CountDownLatch(getThreadCount()));

			// use a cyclic barrier to have all threads start at the same time
			setCyclicBarrier(new CyclicBarrier(getThreadCount()));

			// create the thread pool and start the producer threads
			setThreadPool(Executors.newFixedThreadPool(getThreadCount()));

			// by default all threads write to the same destination (subject).
			// You can override this by setting sharedDestination to false.
			if (getThreadCount() == 1 || isSharedDestination()) {
				for (int i = 1; i <= getThreadCount(); i++) {
					getThreadPool().execute(
							new ProducerThread(this, i, getCyclicBarrier()));
				}
			} else {
				for (int i = 1; i <= getThreadCount(); i++) {
					getThreadPool().execute(
							new ProducerThread(this, i, getSubject()
									+ Integer.toString(i), this
									.getCyclicBarrier()));
				}
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

	public String getUrl() {
		return this.url;
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

	/**
	 * @return the header
	 */
	public String getHeader() {
		return header;
	}

	public void setSyncSend(boolean syncSend) {
		this.syncSend = syncSend;
	}

	public boolean isSyncSend() {
		return syncSend;
	}

	public boolean isJndi() {
		return jndi;
	}

	public void setJndi(boolean jndi) {
		this.jndi = jndi;
	}

	/**
	 * @param header
	 *            the header and its value to set
	 */
	public void setHeader(String header) throws IllegalArgumentException {

		if (header == null || header.isEmpty()) {
			return;
		}

		String[] tokens = header.split(":");

		if (tokens == null || tokens.length != 2) {
			throw new IllegalArgumentException("ERROR: invalid header: "
					+ header);
		} else if (tokens[0].isEmpty()) {
			throw new IllegalArgumentException("ERROR: invalid header key: "
					+ tokens[0]);
		} else if (tokens[1].isEmpty()) {
			throw new IllegalArgumentException("ERROR: invalid header value: "
					+ tokens[1]);
		}
		this.header = tokens[0].trim();
		this.headerValue = tokens[1].trim().replaceAll("%20", " ");
	}

	/**
	 * @return the headerValue
	 */
	public String getHeaderValue() {
		return headerValue;
	}

	/**
	 * @param headerValue
	 *            the headerValue to set
	 */
	public void setHeaderValue(String headerValue) {
		this.headerValue = headerValue;
	}

	/**
	 * @return the objectMessage
	 */
	public boolean isObjectMessage() {
		return messageType.isObject();
	}

	/**
	 * @return the bytesMessage
	 */
	public boolean isBytesMessage() {
		return messageType.isBytes();
	}

	/**
	 * @return the messageType
	 */
	public MessageType getMessageType() {
		return messageType;
	}

	/**
	 * @param messageType
	 *            the messageType to set
	 */
	public void setMessageType(String messageType)
			throws IllegalArgumentException {
		if (messageType == null || messageType.length() == 0) {
			throw new IllegalArgumentException("messageType is null or empty");
		}
		this.messageType = MessageType
				.valueOf(messageType.trim().toUpperCase());
	}

	/**
	 * @return the sharedDestination
	 */
	public boolean isSharedDestination() {
		return sharedDestination;
	}

	/**
	 * @param sharedDestination
	 *            the sharedDestination to set
	 */
	public void setSharedDestination(boolean sharedDestination) {
		this.sharedDestination = sharedDestination;
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

	/**
	 * @return the qpid
	 */
	public boolean isQpid() {
		return qpid;
	}

	/**
	 * @param qpid
	 *            the qpid to set
	 */
	public void setQpid(boolean qpid) {
		this.qpid = qpid;
	}

	/**
	 * @return the user
	 */
	private String getUser() {
		return user;
	}

	/**
	 * @return the password
	 */
	private String getPassword() {
		return password;
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
	 * @return the replyWaitTime
	 */
	public long getReplyWaitTime() {
		return replyWaitTime;
	}

	/**
	 * @param replyWaitTime
	 *            the replyWaitTime to set
	 */
	public void setReplyWaitTime(long replyWaitTime) {
		this.replyWaitTime = replyWaitTime;
	}

	/**
	 * @return the headers
	 */
	public String getHeaders() {
		return headers;
	}

	/**
	 * @param headers
	 *            the headers to set
	 */
	public void setHeaders(String headers) throws Exception {		
		this.headers = headers;
		if (headers == null || headers.isEmpty()) {
			return;
		}
		Map<String, String> map = new HashMap<String, String>();
		String[] kvs = headers.split(",");
		for (String kv : kvs) {
			String[] pair = kv.split(":");
			if (pair.length != 2) {
				System.out.println("ERROR: headers is not properly formatted");
				throw new Exception("headers is not properly formatted");
			}
			map.put(pair[0].trim(), pair[1].trim());
		}
		if (!map.isEmpty()) {
			this.setHeadersMap(map);
		}
	}

	/**
	 * @return the headersMap
	 */
	Map<String, String> getHeadersMap() {
		return headersMap;
	}

	/**
	 * @param headersMap
	 *            the headersMap to set
	 */
	void setHeadersMap(Map<String, String> headersMap) {
		this.headersMap = headersMap;
	}

}
