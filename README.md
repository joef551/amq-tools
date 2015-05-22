

Simple Producer and Consumer Tool for ActiveMQ
======

The idea behind this project is to provide an ActiveMQ client tool that can be used to simulate a variety of different client (producer or consumer) use cases. 

Run the following to build the project:
 
$ mvn clean package

The above will build two jar files in the project's target directory: amq-tools.jar and uber-amq-tools.jar. The former contains only the project's class files, while the latter also contains all of the project's dependencies. 

From the project's home or top-level directory, use the 'runp' and 'runc' bash shell scripts to run the producer and consumer, respectively. (TODO: create Windows versions of the two scripts). The scripts execute the uber version of the jar files, which are expected to reside in the ./target directory. There is a commented out line that can be used to run the non-uber version via maven.  

Some examples:

1. To display all producer options

	$ runp help 

2. To display all consumer options

	$ runc help

3. Connect producer to broker at default URI with user 'fred' and password 'admin' and write persistent messages to default queue name of 'TOOL.DEFAULT'.

	$ runp user=fred password=admin persistent 
	
	Note that the default URL to connect to the broker is <i>failover://tcp://localhost:61616</i>.

4. Same as above, but spawns 100 producer threads; each will create its own connection to the broker

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

	<activemq.version>5.9.0.redhat-610379</activemq.version>
	<!-- <activemq.version>5.8.0.redhat-60024</activemq.version> -->
	<!-- <activemq.version>5.8.0.redhat-60065</activemq.version> -->
<br><br>

Consumer Options
----------------

 Option    | Default Value | Description 
:------    | :------   | :-----------
ackMode    | AUTO_ACKNOWLEDGE  | The acknowledgement mode to be used by the consumer. Other possible values are CLIENT_ACKNOWLEDGE and DUPS_OK_ACKNOWLEDGE. **Ignored if transacted (see below) is set to true.**
consumerName | admin | The client identification string to use when establishing a durable topic subscriber. Only used if durable and topic are set to true. 
selector | not used | Used for specifying a selector. For example, to specify the selector foo='bar', enter selector=foo=%27foobar%27, and to specify foo = 'bar', enter foo%20=%20%27bar%27. Note that single quotes are required. 
topic|	false|	Whether to receive from a topic or queue.
durable	| false	 | Whether or not this is a durable topic subscriber. Only valid if topic is set to true.
maxMessages	|0 (no limit) |	The maximum number of messages to receive after which the consumer terminates. If maxMessages is > 0, it defines a *batch*
batchCount	|1 | If maxMessages is > 0, batchCount governs the number of consumer objects used to read the batches. For example, if maxMessages is 10 and batchCount is 5, then a consumer object is terminated after it reads 10 messages and a new one is created to take its place; this cycling will occur 5 times and thus the client will only read a total of 50 messages. If set to 0, the cycling will occur indefinitely. persistent|	false |	Whether to send persistent or non-persistent (transient) reply messages back to the producer.receiveTimeout|	0 (no time limit)|	Stop receiving messages and terminate if a message does not arrive within the specified number of milliseconds. 
transacted	|false|	Whether to receive messages within a local JMS transaction. transactedBatchSize	 |1 |	The number of messages to batch in a transaction. Only used if transacted is set to true.rollback|	false|	When set to true, the consumer rolls back the trx instead of committing it. sampleSize	|5000 |	The number of messages at which a sampling is taken and the results of which are displayed. 
sampleResetTime	|10000 |	If a message is not received within this amount of time (ms), the sampling statistics are reset.   sleepTime |	0|	The number of milliseconds to sleep in between each message that is consumed. You can use this to mimic a slow consumer. Sleep occurs before message acknowledge or commit.subject	|TOOL.DEFAULT |	The name of the target destination (topic or queue).durable|false| Used for creating a durable subscriber. The 'topic' option must be specifiedurl	|failover://tcp://localhost:61616	|The url that is used for connecting to the ActiveMQ message broker. You can assign it all the options described [here](http://activemq.apache.org/connection-configuration-uri.html)user |	admin |	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose |	false	| When set to true, each message that is consumed will be written out to the console. threadCount | 1 | The number of consumer threads to spawn. When more than one thread, only the first will log messages. 
sharedConnection | false | When set to false, all consumer threads will create their own separate connection. When set to true all consumer threads will share one common connection, from which they all create their sessions and consumer objects. 
pooled|false|Whether to use a pooled connection factory. Only valid when sharedConnection is false. If the pooled connection factory is used, then these three options apply: 1) maxConnections, 2) idleTimeout, and 3) expiryTimeout.  For details on these three options click [here](http://activemq.apache.org/maven/apidocs/org/apache/activemq/pool/PooledConnectionFactory.html)
help | false | use only for displaying all consumer options (e.g., runc help) 

Producer Options
----------------


 Option    | Default Value  | Description 
:------    | :------   | :-----------
messageCount	|5000|	The number of messages to produce.
messageSize |256 |	The size of the message that is produced.  persistent|	false |	Whether to send persistent or non-persistent messages.
priority|not set | The JMS priority to assign to the messagessampleSize |	5000|	The number of messages at which a sampling is taken and the results of which are displayed.  sleepTime |	0 |	The number of milliseconds to sleep in between each message produced.subject |	TOOL.DEFAULT |	The name of the target destination (topic or queue)timeToLive |	0 (does not expire)	|The message expiration time in milliseconds. topic |	false (queue)|	Whether to send to a topic or queue.
syncSend |	false |	If sending to topic, whether to issue sync, as opposed to async, sends transacted|	false|	Whether to send messages within the context of a local JMS transactiontransactedBatchSize |	1	| The number of messages to batch in a transaction. Only used if transacted is set to true.rollback|	false|	When set to true, the producer rolls back the trx instead of committing it. 
reply | false | Whether to implement request-reply pattern. **NB: Ignored if (transacted == 'true' and (transactedBatchSize > 1 or rollback == 'true'))** url	|failover://tcp://localhost:61616	|The url that is used for connecting to the ActiveMQ message broker.user	|joef|	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose|false|	When set to true, each message that is produced is written out to the console.
threadCount | 1 | The number of consumer threads to spawn. 
group | null | The group name to assign to the JMSXGROUPID header
header|null|A custom header (property) to be assigned to each message produced. The supplied value must be in the form of "key:value". For example, header=foo:bar, where 'foo' is header key (name) and 'bar' is its value. If there us a space character in the header value, then use '%20'. For example header=fullName:Mary%20Smith
help | false | use only for displaying all producer options (e.g., runp help) 


