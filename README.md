

Simple Producer and Consumer Tool for ActiveMQ
======

The idea behind this project is to provide an ActiveMQ client tool that can be used to simulate a variety of different client use-cases. 

Run the following to do the initial build:

$ mvn clean install -Dmaven.test.skip=true

Use the 'runp' and 'runc' scripts to run the producer and consumer, respectively. Some examples:

1. To display all producer options

	$ runp help 

2. To display all consumer options

	$ runc help

3. Connect producer to broker at default URI with user 'fred' and password 'admin' and write persistent messages to default queue name of 'TOOL.DEFAULT'.

	$ runp user=fred password=admin persistent 

4. Same as above, but spawns 100 producer threads; each will create its own connection to the broker

	$ runp user=Fred password=admin persistent threadCount=100
	
5. Same as above, but writes to the default topic called 'TOOL.DEFAULT' instead of default queue

	$ runp user=Fred password=admin topic threadCount=100

6. Connect producer to broker and have it implement request-reply pattern using persistent messages. After each send, the producer will wait for a reply from the consumer. The producer will send the default number of messages (10000) each having default size (255).

    $ runp persistent request 

7. Connect consumer to broker as user Fred and password admin and consume from default subject with a selector of foo='bar'

	$ runc user=Fred password=admin selector=foo=%27bar%27

8. Same as above, but spawns 100 consumer threads; each will create its own connection to the broker

	$ runc user=Fred password=admin threadCount=100 selector=foo=%27bar%27
	

9.	Same as above, but spawns 100 consumer threads, where they all share one connectionto the broker

	$ runc user=Fred password=admin threadCount=100 selector=foo=%27bar%27 sharedConnection


10. Connect the consumer to the broker using the given credentials and **http** transport. This assumes the broker has been assigned an http transport. 

	$ runc user=admin password=admin url=http://127.0.0.1:62000
	
11.	Connect the consumer to the given URL and have it use a preFetch of 1. 

	$ runc user=admin password=admin url=http://10.0.1.3:62000?jms.prefetchPolicy.queuePrefetch=1

ActiveMQ Version
----
Note in the project's pom.xml that the version of the ActiveMQ client libraries being used is currently set at  `5.9.0.redhat-610379`. You will need to adjust it (i.e., comment and uncomment the lines below) according to the target ActiveMQ broker. 
<br><br>


`<version>5.9.0.redhat-610379</version>`

`<!-- <version>5.8.0.redhat-60024</version> -->`

`<!-- <version>5.8.0.redhat-60065</version> -->`
<br><br>

Consumer Options
----------------

 Option    | Default   | Description 
:------    | :------   | :-----------
ackMode    | AUTO_ACKNOWLEDGE  | The acknowledgement mode to be used by the consumer. Other possible values are CLIENT_ACKNOWLEDGE and DUPS_OK_ACKNOWLEDGE. **Ignored if transacted (see below) is set to true.**
consumerName | Fred | The client identification string to use when establishing a durable topic subscriber. Only used if durable and topic are set to true. 
selector | not used | Used for specifying a selector. For example, to specify the selector foo='bar', enter selector=foo=%27foobar%27, and to specify foo = 'bar', enter foo%20=%20%27bar%27. Note that single quotes are required. 
topic|	false|	Whether to receive from a topic or queue.
durable	| false	 | Whether or not this is a durable topic subscriber. Only valid if topic is set to true.
maxMessages	|0 (no limit) |	The maximum number of messages to receive after which the consumer terminates. If maxMessages is > 0, it defines a *batch*
batchCount	|1 | If maxMessages is > 0, batchCount governs the number of consumer objects used to read the batches. For example, if maxMessages is 10 and batchCount is 5, then a consumer object is terminated after it reads 10 messages and a new one is created to take its place; this cycling will occur 5 times and thus the client will only read a total of 50 messages. If set to 0, the cycling will occur indefinitely. persistent|	false |	Whether to send persistent or non-persistent (transient) reply messages back to the producer.receiveTimeOut|	0 (no time limit)|	Stop receiving messages and terminate if a message does not arrive within the specified number of milliseconds. 
transacted	|false|	Whether to receive messages within a local JMS transaction. transactedBatchSize	 |1 |	The number of messages to batch in a transaction. Only used if transacted is set to true.rollback|	false|	When set to true, the consumer rolls back the trx instead of committing it. sampleSize	|10000 |	The number of messages at which a sampling is taken and the results of which are displayed.  sleepTime |	0|	The number of milliseconds to sleep in between each message that is consumed. You can use this to mimic a slow consumer. Sleep occurs before message acknowledge or commit.subject	|TOOL.DEFAULT |	The name of the target destination (topic or queue).durable|false| Used for creating a durable subscriber. The 'topic' option must be specifiedurl	|failover://tcp://localhost:61616	|The url that is used for connecting with the ActiveMQ message broker.user |	joef |	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose |	false	| When set to true, each message that is consumed will be written out to the console. threadCount | 1 | The number of consumer threads to spawn. When more than one thread, only the first will log messages. 
sharedConnection | false | When set to false, all consumer threads will create their own separate connection. When set to true all consumer threads will share one common connection, from which they all create their sessions. 
help | false | use only for displaying all consumer options (e.g., runc help) 

Producer Options
----------------


 Option    | Default   | Description 
:------    | :------   | :-----------
messageCount	|10000|	The number of messages to produce.
messageSize |255 |	The size of the message that is produced.  persistent|	false |	Whether to send persistent or non-persistent messages.
priority|not set | The JMS priority to assign to the messagessampleSize |	10000|	The number of messages at which a sampling is taken and the results of which are displayed.  sleepTime |	0 |	The number of milliseconds to sleep in between each message produced.subject |	TOOL.DEFAULT |	The name of the target destination (topic or queue)timeToLive |	0 (does not expire)	|The message expiration time in milliseconds. topic |	false|	Whether to send to a topic or queue.transacted|	false|	Whether to send messages within a local JMS transactionreply | false | Whether to implement request-reply pattern. **NB: Ignored if (transacted == 'true' and (transactedBatchSize > 1 or rollback == 'true'))** transactedBatchSize |	1	| The number of messages to batch in a transaction. Only used if transacted is set to true.url	|failover://tcp://localhost:61616	|The url that is used for connecting with the ActiveMQ message broker.user	|joef|	The user name that is used for establishing a connection with ActiveMQ.
password |	admin |	The password that is used for establishing a connection with ActiveMQ.verbose|false|	When set to true, each message that is produced is written out to the console.
threadCount | 1 | The number of consumer threads to spawn. 
group | null | The group name to assign to the JMSXGROUPID header
help | false | use only for displaying all producer options (e.g., runp help) 


