package org.redhat.amq.tools;

import org.apache.activemq.advisory.ProducerEvent;
import org.apache.activemq.advisory.ProducerEventSource;
import org.apache.activemq.advisory.ProducerListener;
import java.util.Arrays;
import javax.jms.Destination;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
/**
 * The ActiveMQ ProducerEventSource is an object that is be used to listen to the
 * number of active producers available on a given destination.
 */

public class ProducerEventTool implements ProducerListener {

	private String subject = "TOOL.DEFAULT";
	private String user = "admin";
	private String password = "admin";
	private String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	private Session session;
	private boolean help;
	private boolean topic;
	private long sleepTime = 20000L;
	private ActiveMQConnectionFactory connectionFactory;
	private ConnectionFactory jmsConnectionFactory;
	private Destination destination;
	private ProducerEventSource producerEventSource;
	private Connection connection;

	// @formatter:off
	private static final String Usage = "\nusage: java ProducerEventTool \n"
			+ "[[user=<userid>]                          default: admin\n" 			
			+ "[password=<password>]                     default: admin\n" 			
			+ "[subject=<queue or topic name>]           default: TOOL.DEFAULT\n"  			
			+ "[prefetch=<prefetch count>]               default: 1\n" 			
			+ "[topic]                                   default: false \n" 
			+ "[sleepTime=<sleep time between each rcv>] default: sleepTime\n" 
			+ "[url=<broker url>]                        default: " + ActiveMQConnection.DEFAULT_BROKER_URL + "\n";			
	// @formatter:on

	public static void main(String[] args) throws Exception {

		ProducerEventTool producerEvent = new ProducerEventTool();

		// Read in the command line options
		String[] unknown = CommandLineSupport.setOptions(producerEvent, args);

		// Exit if end user entered unknown options
		if (unknown.length > 0) {
			System.out.println("Unknown options: " + Arrays.toString(unknown));
			System.exit(-1);
		}

		// If 'help' request, then simply display usage string and exit
		if (producerEvent.isHelp()) {
			System.out.println(Usage);
			return;
		}

		// Else, start the producerEvent
		producerEvent.start();
	}

	public void start() throws Exception {

		// Display the current settings
		System.out.println("A-MQ ProducerEventTool");
		System.out.println("Connecting to URL   : " + getUrl());
		System.out.println("Destination         : " + getSubject());
		System.out.println("user                = " + getUser());
		System.out.println("password            = " + getPassword());
		System.out.println("topic               = " + isTopic());
		System.out.println("sleepTime           = " + getSleepTime());

		setConnectionFactory(new ActiveMQConnectionFactory(getUser(),
				getPassword(), getUrl()));

		connection = getConnectionFactory().createConnection();
		if (connection == null) {
			System.out.println("Error, unable to acquire connection");
			return;
		}

		connection.start();
		System.out.println("Connection started.");

		// grab a session from the connection
		session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
		setDestination((isTopic()) ? session.createTopic(getSubject())
				: session.createQueue(getSubject()));

		// create, initialize, and start the ProducerEventSource
		setProducerEventSource(new ProducerEventSource(connection,
				getDestination()));
		getProducerEventSource().setProducerListener(this);

		getProducerEventSource().start();

		// pause for the specified sleep time
		System.out.println("Sleeping .....");
		Thread.sleep(getSleepTime());
		System.out.println("Stopping .....");

		// shutdown
		getProducerEventSource().stop();
		session.close();
		connection.close();
	}

	public void onProducerEvent(ProducerEvent event) {
		System.out.println("Received: " + event.toString());
		System.out.println("Producer count = " + event.getProducerCount());
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

	public void setTopic(boolean topic) {
		this.topic = topic;
	}

	public boolean isTopic() {
		return topic;
	}

	public boolean isHelp() {
		return help;
	}

	public void setHelp(boolean help) {
		this.help = help;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public ActiveMQConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	public void setConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	public ConnectionFactory getJmsConnectionFactory() {
		return jmsConnectionFactory;
	}

	public void setJmsConnectionFactory(ConnectionFactory jmsConnectionFactory) {
		this.jmsConnectionFactory = jmsConnectionFactory;
	}

	public Destination getDestination() {
		return destination;
	}

	public void setDestination(Destination destination) {
		this.destination = destination;
	}

	public ProducerEventSource getProducerEventSource() {
		return producerEventSource;
	}

	/**
	 * The ProducerEventSource is an object that is be used to listen to the
	 * number of active producers available on a given destination.
	 * 
	 * @param producerEventSource
	 */
	public void setProducerEventSource(ProducerEventSource producerEventSource) {
		this.producerEventSource = producerEventSource;
	}

	public long getSleepTime() {
		return sleepTime;
	}

	public void setSleepTime(long sleepTime) {
		this.sleepTime = sleepTime;
	}

}
