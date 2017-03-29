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

import static org.redhat.amq.tools.CommandLineSupport.readProps;
import static org.redhat.amq.tools.CommandLineSupport.setOptions;
import static org.redhat.amq.tools.ConsumerTool.isQpidUrl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;

import javax.jms.ExceptionListener;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.QueueBrowser;
import javax.naming.InitialContext;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

/**
 * A simple AMQ browser.
 * 
 */
public class BrowserTool implements ExceptionListener {
	private String subject = "TOOL.DEFAULT";
	private String user = "admin";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private String consumerName = "Fred";
	private String selector;
	private Session session;
	private String props;
	private boolean help;
	private boolean verbose = true;
	private boolean qpid;
	private boolean nativeArtemis;
	private boolean jndi;

	private QueueBrowser browser;
	private ActiveMQConnectionFactory connectionFactory;
	private ConnectionFactory jmsConnectionFactory;
	private ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
	private int prefetch = 1;

	private Connection connection;
	private ExecutorService threadPool;

	InitialContext initialContext;

	// @formatter:off
	private final String Usage = "\nusage: java ConsumerTool \n"
			+ "[[user=<userid>]                          default: admin\n" 			
			+ "[password=<password>]                     default: admin\n" 
			+ "[consumerName=<consumer name>]            default: Fred\n" 
			+ "[subject=<queue or topic name>]           default: TOOL.DEFAULT\n"  
			+ "[selector=<header%20=%20%27value%27>]     default: null\n" 
			+ "[prefetch=<prefetch count>]               default: 1\n" 
			+ "[verbose]                                 default: true \n" 
			+ "[props=<path to props file>]              default: not used\n" 
			+ "[jndi]                                    default: " + jndi + "\n"	
			+ "[qpid]                                    default: " + qpid + "\n"
			+ "[url=<broker url>]                        default: " + ActiveMQConnection.DEFAULT_BROKER_URL + "\n";			
	// @formatter:on

	public static void main(String[] args) throws Exception {

		BrowserTool browserTool = new BrowserTool();

		// Read in the command line options
		String[] unknown = CommandLineSupport.setOptions(browserTool, args);

		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Unknown options: " + Arrays.toString(unknown));
			System.exit(-1);
		}

		// If 'help' request, then simply display usage string and exit
		if (browserTool.isHelp()) {
			System.out.println(browserTool.Usage);
			return;
		}

		// if a props file was specified, then use the properties
		// specified in that file
		if (browserTool.getProps() != null) {
			ArrayList<String> props = readProps(browserTool.getProps());
			// if there were properties, add them
			if (props.size() > 0) {
				unknown = setOptions(browserTool,
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
		browserTool.start();
	}

	public void start() throws Exception {

		// Display the current settings
		System.out.println("A-MQ BrowserTool");
		System.out.println("Connecting to URL   : " + url);
		System.out.println("Browsing queue      : " + subject);
		System.out.println("consumerName        = " + consumerName);
		System.out.println("user                = " + user);
		System.out.println("password            = " + password);
		System.out.println("selector            = " + selector);
		System.out.println("verbose             = " + verbose);
		System.out.println("prefetch            = " + prefetch);
		System.out.println("props               = " + props);
		System.out.println("jndi                = " + jndi);
		System.out.println("qpid                = " + qpid);

		getPrefetchPolicy().setQueueBrowserPrefetch(getPrefetch());

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
				((ActiveMQConnectionFactory) getJmsConnectionFactory())
						.setPrefetchPolicy(getPrefetchPolicy());
			} else if (getJmsConnectionFactory() instanceof org.apache.qpid.jms.JmsConnectionFactory) {
				((org.apache.qpid.jms.JmsConnectionFactory) getJmsConnectionFactory())
						.setUsername(getUser());
				((org.apache.qpid.jms.JmsConnectionFactory) getJmsConnectionFactory())
						.setPassword(getPassword());
				setQpid(true);
			} else if (getJmsConnectionFactory() instanceof ActiveMQJMSConnectionFactory) {
				((ActiveMQJMSConnectionFactory) getJmsConnectionFactory())
						.setUser(getUser());
				((ActiveMQJMSConnectionFactory) getJmsConnectionFactory())
						.setPassword(getPassword());
				setNativeArtemis(true);
			} else {
				throw new Exception("ERROR: unknown connection factory: "
						+ getJmsConnectionFactory().getClass().getName());
			}
			System.out.println("Connecting with JNDI context: "
					+ initialContext.getEnvironment());
		} else if (!isQpid()) {
			System.out.println("Connecting to URL: " + url);
			// if !qpid, then we're directly using the ActiveMQ 5.x connection
			// factory. The default prefetch of 1 precludes the browser from
			// hanging a bit after reading all messages.
			ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
					getUser(), getPassword(), getUrl());
			factory.setPrefetchPolicy(getPrefetchPolicy());
			setJmsConnectionFactory(factory);
		} else {
			System.out.println("Connecting to URL: " + url);
			// using a qpid connection factory
			// if URL is set to default openwire, then switch to default qpid
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

		connection = getJmsConnectionFactory().createConnection();
		if (connection == null) {
			System.out.println("Error, unable to acquire connection");
			return;
		}

		connection.start();
		System.out.println("Connection started.");

		// grab a session from the connection
		session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

		// from the session, create a browser with optional selector
		browser = (getSelector() == null) ? session.createBrowser(session
				.createQueue(getSubject())) : session.createBrowser(
				session.createQueue(getSubject()), getSelector());

		Enumeration msgs = browser.getEnumeration();

		int msgCount = 0;
		if (msgs != null) {
			while (msgs.hasMoreElements()) {
				Message tempMsg = (Message) msgs.nextElement();
				msgCount++;
				if (!isVerbose()) {
					continue;
				}
				// if (objMsg instanceof AmqpJmsMessageFacade) {
				if (isQpid()) {
					Enumeration<String> msgEnum = tempMsg.getPropertyNames();
					System.out.print("AMQP Message: { ");
					for (; msgEnum.hasMoreElements();) {
						String key = msgEnum.nextElement();
						Object value = tempMsg.getObjectProperty(key);
						System.out.print(key + "=" + value.toString() + ",  ");
					}
					System.out.println(" }");
					System.out.print("JMSDeliveryMode = "
							+ tempMsg.getJMSDeliveryMode() + ", ");
					System.out.print("JMSDeliveryTime = "
							+ tempMsg.getJMSDeliveryTime() + ", ");
					System.out.print("JMSPriority = "
							+ tempMsg.getJMSPriority() + ", ");
					System.out.print("JMSExpiration = "
							+ tempMsg.getJMSExpiration() + ", ");
					System.out
							.print("JMSType = " + tempMsg.getJMSType() + ", ");
					System.out.println("JMSMessageID = "
							+ tempMsg.getJMSMessageID() + "\n");
				} else if (isNativeArtemis()) {
					System.out.println("Artemis Message: " + tempMsg.toString()
							+ "\n");					
					System.out.print("JMSDeliveryMode = "
							+ tempMsg.getJMSDeliveryMode() + ", ");
					System.out.print("JMSDeliveryTime = "
							+ tempMsg.getJMSDeliveryTime() + ", ");
					System.out.print("JMSPriority = "
							+ tempMsg.getJMSPriority() + ", ");
					System.out.print("JMSExpiration = "
							+ tempMsg.getJMSExpiration() + ", ");
					System.out
							.print("JMSType = " + tempMsg.getJMSType() + ", ");
					System.out.println("JMSMessageID = "
							+ tempMsg.getJMSMessageID() + "\n");
				} else {
					// if its not qpid-jms, then its activemq (openwire)jms
					System.out.println("OpenWire Message: "
							+ tempMsg.toString() + "\n");
					// System.out.println("JMSMessageID = "
					// + tempMsg.getJMSMessageID() + "\n");
				}

			}
		}

		System.out.println("Number of messages returned = " + msgCount);
		session.close();
		connection.close();
	}

	public void onException(JMSException ex) {
		System.out.println("JMS Exception occured.  Shutting down client.");
		ex.printStackTrace();
		threadPool.shutdown();
		System.exit(1);
	}

	public void setConsumerName(String consumerName) {
		this.consumerName = consumerName;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public void setPassword(String pwd) {
		this.password = pwd;
	}

	public String getPassword() {
		return password;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
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
	 * @return the prefetchPolicy
	 */
	public ActiveMQPrefetchPolicy getPrefetchPolicy() {
		return prefetchPolicy;
	}

	/**
	 * @param prefetchPolicy
	 *            the prefetchPolicy to set
	 */
	public void setPrefetchPolicy(ActiveMQPrefetchPolicy prefetchPolicy) {
		this.prefetchPolicy = prefetchPolicy;
	}

	/**
	 * @return the prefetch
	 */
	public int getPrefetch() {
		return prefetch;
	}

	/**
	 * @param prefetch
	 *            the prefetch to set
	 */
	public void setPrefetch(int prefetch) {
		if (prefetch >= 0) {
			this.prefetch = prefetch;
		}
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
	 * @return the jndi
	 */
	public boolean isJndi() {
		return jndi;
	}

	/**
	 * @param jndi
	 *            the jndi to set
	 */
	public void setJndi(boolean jndi) {
		this.jndi = jndi;
	}

	/**
	 * @return the nativeArtemis
	 */
	public boolean isNativeArtemis() {
		return nativeArtemis;
	}

	/**
	 * @param nativeArtemis
	 *            the nativeArtemis to set
	 */
	public void setNativeArtemis(boolean nativeArtemis) {
		this.nativeArtemis = nativeArtemis;
	}

}
