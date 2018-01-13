

Simple Producer and Consumer Tool for ActiveMQ
======

The idea behind this project is to provide an ActiveMQ **JMS** client tool that can be used to simulate a variety of different client (producer or consumer) use cases. This JMS tool is capable of connecting to either an ActiveMQ 6.x or 7.x (a.k.a., Artemis) broker. It can use the ActiveMQ 6.x OpenWire protocol to connect to either version of the broker, AMQP to connect to ActiveMQ 7, and the ActiveMQ 7 native protocol. It can also use html to connect to an ActiveMQ 6.x broker; however, that is very seldom used.    

Run the following to build the project:
 
$ mvn clean package

The above will build two jar files in the project's target directory: amq-tools.jar and uber-amq-tools.jar. The former contains only the project's class files, while the latter also contains all of the project's dependencies. 

From the project's home or top-level directory, use the 'runp' and 'runc' bash shell scripts to run the producer and consumer, respectively. (TODO: create Windows versions of the two scripts). The scripts execute the uber version of the jar file, which is expected to reside in the ./target directory. There is a commented out line that can be used to run the non-uber version via maven.  

Some examples:

1. To display all producer options

	$ runp help 

2. To display all consumer options

	$ runc help

3. Connect producer to broker at default URI with user 'fred' and password 'admin' and write persistent messages to default queue name of 'TOOL.DEFAULT'.

	$ runp user=fred password=admin persistent 
	
	Note that the default URL to connect to the broker is <i>failover://tcp://localhost:61616</i>. The target broker can be be either a 6.x or 7.x broker. Also please note that by default, the producer sends non-persitent messages. 

4. Same as above, but spawns 100 producer threads; each will create its own connection to the broker and each will consume from the default TOOL.DEFAULT destination, which is a queue. 

	$ runp user=fred password=admin persistent threadCount=100
	
5. Same as above, but writes to the default topic called 'TOOL.DEFAULT' instead of default queue

	$ runp user=fred password=admin topic threadCount=100

6. Connect producer to broker and have it implement request-reply pattern using persistent messages. After each send, the producer will wait for a reply from the consumer. The producer will send the default number of messages (10000) each having a default size of 255.

    $ runp persistent request 

7. Connect consumer to broker as user Fred and password admin and consume from default queue with a selector of foo='bar'

	$ runc user=fred password=admin selector=foo=%27bar%27

8. Same as above, but spawns 100 consumer threads; each will create its own connection to the broker

	$ runc user=fred password=admin threadCount=100 selector=foo=%27bar%27
	

9.	Same as above, but spawns 100 consumer threads, where they all share one connection to the broker. Each thread creates its own session. 

	$ runc user=fred password=admin threadCount=100 selector=foo=%27bar%27 sharedConnection

10. Connect the consumer to the broker using the given credentials and **http** transport. This assumes the broker has been assigned an http transport. 

	$ runc user=admin password=admin url=http://127.0.0.1:62000
	
11.	Connect the consumer to the given URL and have it use a queue preFetch of 1. 

	$ runc user=admin password=admin url=http://10.0.1.3:62000?jms.prefetchPolicy.queuePrefetch=1
	

12.	Run the consumer and have it read in all options/properties from the "myprops" file that is located in the /tmp directory. 

	$ runc props=/tmp/myprops

13.	Run the consumer and have it derive the connection factory from the jndi.properties file. 

	$ runc jndi 

14. Run the consumer and have it connect to a A-MQ 7  (a.k.a., Artemis) broker via an Apache Qpid provided AMQP connection. This is an example of what you need to do to have the consumer connect to A-MQ 7 via AMQP.  

    $ runc url=amqp://10.0.1.21:5672 qpid

15. Run the **queue browser**, have it browse those messages on the default destination, and have it derive the connection factory from the jndi.properties file.  

    $ runb jndi 


The consumer will dump sampling statistics, such as the ones below, each time it reaches the sampleSize (default = 5000).

```
[1] Interval time (ms) = 785.0	Rate (mgs/sec) = 6369.426751592357
[1] Interval time (ms) = 535.0	Rate (mgs/sec) = 9345.794392523363
[1] Interval time (ms) = 532.0	Rate (mgs/sec) = 9398.496240601504
[1] Interval time (ms) = 337.0	Rate (mgs/sec) = 14836.795252225518
[1] Interval time (ms) = 306.0	Rate (mgs/sec) = 16339.869281045752
[1] Interval time (ms) = 312.0	Rate (mgs/sec) = 16025.641025641025
[1] Interval time (ms) = 260.0	Rate (mgs/sec) = 19230.76923076923
```
If the consumer does not receive a message within the sampleResetTime (default = 10000 ms), it resets the statistics. 

ActiveMQ Version
----
Note in the project's pom.xml that the version of the ActiveMQ client libraries being used is currently set at  `5.9.0.redhat-610379`. You will need to adjust it (i.e., comment and uncomment the lines below) according to the target ActiveMQ broker. 
<br><br>
	
	<!-- <activemq.version>5.8.0.redhat-60024</activemq.version> -->
	<!-- <activemq.version>5.8.0.redhat-60065</activemq.version> -->
	<activemq.version>5.9.0.redhat-610379</activemq.version>
	<!-- <activemq.version>5.9.0.redhat-611423</activemq.version> -->
	<!-- <activemq.version>5.11.0.redhat-621084</activemq.version> --> 
	
<br><br>

Consumer Options
----------------

 Option    | Default Value | Description 
:------    | :------   | :-----------
ackMode    | AUTO_ACKNOWLEDGE  | The acknowledgement mode to be used by the consumer. Other possible values are CLIENT_ACKNOWLEDGE, INDIVIDUAL__ACKNOWLEDGE and DUPS_OK_ACKNOWLEDGE. **This setting is ignored if transacted (see below) is set to true.**
consumerName | Fred | The client identification string to use when establishing a durable topic subscriber. Only used if durable and topic are set to true. 
selector | not used | Used for specifying a selector. For example, to specify the selector foo='bar', enter selector=foo=%27bar%27, and to specify foo = 'bar', enter foo%20=%20%27bar%27. Note that single quotes are required. If you specify this option in the properties/options file (see props option below), then these special encoded characters (i.e., %27) are not required. For example, you can simply use selector=foo='bar'
topic|	false|	Whether to receive from a topic or queue.
durable	| false	 | Whether or not this is a durable topic subscriber. Only valid if topic is set to true and sharedConnection is set to false. 
maxMessages	|0 (no limit) |	The maximum number of messages to receive after which the consumer terminates. If maxMessages is > 0, it defines a *batch*
batchCount	|1 | If maxMessages is > 0, batchCount governs the number of consumer objects used to read the batches. For example, if maxMessages is 10 and batchCount is 5, then a consumer object is terminated after it reads 10 messages and a new one is created to take its place; this cycling will occur 5 times and thus the client will only read a total of 50 messages. If set to 0, the cycling will occur indefinitely. persistent|	false |	Whether to send persistent or non-persistent (transient) reply messages back to the producer.receiveTimeout|	0 (no time limit)|	Stop receiving messages and terminate if a message does not arrive within the specified number of milliseconds. 
transacted	|false|	Whether to receive messages within a local JMS transaction. transactedBatchSize	 |1 |	The number of messages to batch in a transaction. Only used if transacted is set to true.rollback|	false|	When set to true, the consumer rolls back the trx instead of committing it. sampleSize	|5000 |	The number of messages at which a sampling is taken and the results of which are displayed. 
sampleResetTime	|10000 |	If a message is not received within this amount of time (ms), the sampling statistics are reset.   sleepTime |	0|	The number of milliseconds to sleep in between each message that is consumed. You can use this to mimic a slow consumer. Sleep occurs before message acknowledge or commit.subject	|TOOL.DEFAULT |	The name of the target destination (topic or queue).durable|false| Used for creating a durable subscriber. The 'topic' option must be specifiedurl	|failover://tcp://localhost:61616	|The url that is used for connecting to the ActiveMQ message broker. You can assign it all the options described [here](http://activemq.apache.org/connection-configuration-uri.html). This property is ignored when the **jndi** property is set to true.user |	admin |	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose |	false	| When set to true, each message that is consumed will be written out to the console. threadCount | 1 | The number of consumer threads to spawn. When more than one thread, only the first will log messages. 
shareConnection | false |  When set to false, all consumer threads create their own separate connection. When set to true all consumer threads will share one common connection, from which they all create their sessions and consumer objects. 
sharedDestination | true | When set to its default value of true, all consumer threads consume from the given **subject**. If the **threadCount** is greater than one and this property is set to true, you would effectively create a cluster of consumers all servicing the same destination. On the other hand, when set to false, each consumer thread consumes from its own distinct destination. This property can thus be used to simulate creating 100s of destinations, but it will require a corresponding number of threads. If **virtualTopic** (see below) is set to false and this property is also set to false, the name of each distinct destination is created by concatenating the subject with its threadCount. For example, if you have virtualTopic set to false, threadCount to 2, and this property to false, then 2 threads are invoked with thread one and two reading from TOOL.DEFAULT0 and TOOL.DEFAULT1, respectively. If, on the other hand, **virtualTopic** is set to true, the destination names are created as follows: "Consumer.<*thread#*>.VirtualTopic.<*subject*>". For example, assuming the default **subject** name and **threadCount** of 2: "Consumer.1.VirtualTopic.TOOL.DEFAULT" and "Consumer.2.VirtualTopic.TOOL.DEFAULT" would be created.  
virtualTopic|false| See **sharedDestination** above 
pooled|false|Whether to use a pooled connection factory. **Only valid when the sharedConnection, jndi, AND qpid properties are all set to false**. If the pooled connection factory is used, then these three options apply: 1) maxConnections, 2) idleTimeout, and 3) expiryTimeout.  For details on these three options click [here](http://activemq.apache.org/maven/apidocs/org/apache/activemq/pool/PooledConnectionFactory.html)
qpid|false|When set to true, the consumer will use the qpid connection factory provided by the 'org.apache.qpid.jms' package, which is made available by [Apache Qpid](https://qpid.apache.org). This property is ignored when the **jndi** property is set to true. If you're not using jndi and you'd like the client to connect to AMQ-7 (a.k.a., Artemis) via AMQP, then set this property to true and use a URL scheme of 'amqp' (e.g., url=amqp://...). Click [here](https://github.com/apache/qpid-jms/blob/master/qpid-jms-docs/Configuration.md) to learn more about the other valid amqp schems (e.g., amqps), configuration options,  and how to enable frame-level debug. It comes in quite handy when troubleshooting.   
props|null|Is used to specify a file that contains one or more options. For example, the file may contain the option 'url=tcp://myhost:61616'. Options found in this file override those options listed on the command line.
jndi|false|When set to true, the consumer will derive the connection factory and its url from the jndi.properties file. See the project's jndi.properties file for examples. If you have the JNDI set such that you're using the 
help | false | use only for displaying all consumer options (e.g., runc help) 

Producer Options
----------------


 Option    | Default Value  | Description 
:------    | :------   | :-----------
messageCount	|5000|	The number of messages to produce.
messageSize |256 |	The size of the message that is produced. 
messageType |text |	The JMS message type (TextMessage, BytesMessage, ObjectMessage) to produce.  Possible values are text, bytes, and object.persistent|	false |	Whether to send persistent or non-persistent messages.
priority|not set | The JMS priority to assign to the messagessampleSize |	5000|	The number of messages at which a sampling is taken and the results of which are displayed.  sleepTime |	0 |	The number of milliseconds to sleep in between each message produced.replyWaitTime |	5000 |	The max amount of time to wait for a reply when **reply** has been specified.subject |	TOOL.DEFAULT |	The name of the target destination (topic or queue)timeToLive |	0 (does not expire)	|The message expiration time in milliseconds. topic |	false (queue)|	Whether to send to a topic or queue.
syncSend |	false |	If sending to topic, whether to issue sync, as opposed to async, sends transacted|	false|	Whether to send messages within the context of a local JMS transactiontransactedBatchSize |	1	| The number of messages to batch in a transaction. Only used if transacted is set to true.rollback|	false|	When set to true, the producer rolls back the trx instead of committing it. 
reply | false | Whether to implement request-reply pattern. **NB: Ignored if (transacted == 'true' and (transactedBatchSize > 1 or rollback == 'true'))** url	|failover://tcp://localhost:61616	|The url that is used for connecting to the ActiveMQ message broker. This property is ignored when the **jndi** property is set to true.user	|admin|	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose|false|	When set to true, each message that is produced is written out to the console.
threadCount | 1 | The number of producer threads to spawn.
sharedDestination | true | When set to false, each producer thread produces to its own distinct destination. The name of the destination is created by concatenating the subject with its threadCount. For example, if you set threadCount to 2 and set this property to 'false', then 2 threads are invoked with thread one and two producing to TOOL.DEFAULT0 and TOOL.DEFAULT1, respectively.  
group | null | The group name to assign to the JMSXGROUPID header
headers|null|A list of comma-separated custom headers (i.e., JMS properties) to be assigned to each message produced. The supplied value must be in the form of "key:value,...,key:value". For example, headers=firstName:Joe,lastName:Fernandez, where 'firstName' is a header name and 'Joe' is its value. If there is a space character in the header value, then use '%20'. For example headers=fullName:Mary%20Smith
qpid|false|When set to true, the producer will use the qpid connection factory provided by the 'org.apache.qpid.jms' package, which is made available by [Apache Qpid](https://qpid.apache.org). This property is ignored when the **jndi** property is set to true. If you're not using jndi and you'd like the client to connect to AMQ-7 (a.k.a., Artemis) via AMQP, then set this property to true and use a URL scheme of 'amqp' (e.g., url=amqp://...). Click [here](https://github.com/apache/qpid-jms/blob/master/qpid-jms-docs/Configuration.md) to learn more about the other valid amqp schems (e.g., amqps), configuration options,  and how to enable frame-level debug. It comes in quite handy when troubleshooting.      
props|null|Is used to specify a file that contains one or more options. For example, the file may contain the option 'url=tcp://myhost:61616'. Options found in this file override those options listed on the command line. 
jndi | false | When set to true, the producer will derive the connection factory and its url from the jndi.properties file. See the project's jndi.properties file for examples.
help | false | use only for displaying all producer options (e.g., runp help) 

<br><br>

Browser Options
----------------

 Option    | Default Value | Description 
:------    | :------   | :-----------
selector | not used | Used for specifying a selector. For example, to specify the selector foo='bar', enter selector=foo=%27bar%27, and to specify foo = 'bar', enter foo%20=%20%27bar%27. Note that single quotes are required. If you specify this option in the properties/options file (see props option below), then these special encoded characters (i.e., %27) are not required. For example, you can simply use selector=foo='bar'
subject	|TOOL.DEFAULT |	The name of the target queue destination.url	|failover://tcp://localhost:61616	|The url that is used for connecting to the ActiveMQ message broker. You can assign it all the options described [here](http://activemq.apache.org/connection-configuration-uri.html). This property is ignored when the **jndi** property is set to true.user |	admin |	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose |	true	| When set to true, each message that is browsed will be written out to the console.
qpid| false | When set to true, the browser will use the qpid connection factory provided by the 'org.apache.qpid.jms' package, which is made available by [Apache Qpid](https://qpid.apache.org). This property is ignored when the **jndi** property is set to true. If you're not using jndi and you'd like the client to connect to AMQ-7 (a.k.a., Artemis) via AMQP, then set this property to true and use a URL scheme of 'amqp' (e.g., url=amqp://...). Click [here](https://github.com/apache/qpid-jms/blob/master/qpid-jms-docs/Configuration.md) to learn more about the other valid amqp schems (e.g., amqps), configuration options,  and how to enable frame-level debug. It comes in quite handy when troubleshooting.   
props|null|Is used to specify a file that contains one or more options. For example, the file may contain the option 'url=tcp://myhost:61616'. Options found in this file override those options listed on the command line.
jndi|false|When set to true, the browser will derive the connection factory and its url from the jndi.properties file. See the project's jndi.properties file for examples. If you have the JNDI set such that you're using the 
help | false | use only for displaying all browser options (e.g., runb help) 


