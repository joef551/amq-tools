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
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import javax.jms.ExceptionListener;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.QueueBrowser;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;

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
	private boolean help;
	private boolean verbose = true;
	private QueueBrowser browser;
	private ActiveMQConnectionFactory connectionFactory;
	private ConnectionFactory jmsConnectionFactory;
	private ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
	private int prefetch = 1;

	private Connection connection;
	private ExecutorService threadPool;

	// @formatter:off
	private static final String Usage = "\nusage: java ConsumerTool \n"
			+ "[[user=<userid>]                          default: admin\n" 			
			+ "[password=<password>]                     default: admin\n" 
			+ "[consumerName=<consumer name>]            default: Fred\n" 
			+ "[subject=<queue or topic name>]           default: TOOL.DEFAULT\n"  
			+ "[selector=<header%20=%20%27value%27>]     default: null\n" 
			+ "[prefetch=<prefetch count>]               default: 1\n" 
			+ "[verbose]                                 default: true \n" 
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
			System.out.println(Usage);
			return;
		}

		// Else, start the tool
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

		setConnectionFactory(new ActiveMQConnectionFactory(user, password, url));

		// set the queue browser prefetch
		getPrefetchPolicy().setQueueBrowserPrefetch(getPrefetch());
		getConnectionFactory().setPrefetchPolicy(getPrefetchPolicy());

		connection = getConnectionFactory().createConnection();
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
				if (isVerbose()) {
					System.out.println("Message: " + tempMsg.toString() + "\n");
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

}
