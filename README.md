Documentation for CSci 5105 Distributed Bulletin Board System
Author: Joey Engelhart, engel429

# System Description:

This is a distributed pub-sub messaging system that uses UDP and Java RMI to communicate. Servers are replicated and  made
consistent according to a choice of one of three consistency policies. It builds upon the existing publish-subscribe system
from CSci 5105, project 1.

## Client Structure

Clients are structured similarly to the first project.

## Server Structure

The remote messaging server has more moving parts. Upon initialization, the server creates:

### PeerListManager

This class manages all peer replicas, and determines the coordinator. It uses the Client class to interface with other
servers: any discovered peer leads to the creation of a peer client, whose operations are used to enforce consistency policies.

### Coordinator

The Coordinator is kept as simple as possible to avoid being a bottleneck. It:
    * Is the single source for message ids (which are unique).
    * Is the single source for client ids (which are unique).
    * It provides publish order coordination for the sequential consistency policy.

### ConsistencyPolicy

A ReplicatedPubSubServer is passed a consistency policy at runtime. It enforces the basic consistency tenants specific
to each policy:
    * Read-your-writes: Whenever a client joins, this policy contacts the previous server of the client (if applicable)
      to request all existing messages from that server, to ensure that wherever a client goes, it can always read the effects
      of its earlier activity. Because this is the only time updates propagate in this particular implementation of RYW
      (make it a somewhat 'relaxed' or 'lazy' form of Read-your-writes), we are assured that other posts on which client
      writes at the previous server depended will only have had access to message published at that server, or through a
      transfer caused by a client moving to it.

    * Sequential: We use a primary-backup model to provide this consistency: All writes are made through the Coordinator,
      because any publication that arrives is sent to the coordinator from the replica that original received that publication.
      All messages are buffered by a replica until it has received all messages in the order the Coordinator specifies.

    * Quorum: The PeerListManager of a given replica provides the services of establishing a random subset of peers for
      read/write quora, and then uses the matching peerClients to carry out the needed operation. Reads require being made
      from a peer that is storing the max messageId in the system, which we are assured to have in our quorum if our quora
      sizes are large enough.

### Dispatcher

The Dispatcher is constructed to perform as little work as possible, so as to avoid being a bottleneck
for the system. Whenever a client Join()s, it adds that client's map that maps IP/Port to a ClientManager
for that Client. When a subsequent remote invocation for that client arrives, it simply looks up the ClientManager for that client, creates a new task for that ClientManager on the task queue, and immediately
returns success or failure to the client.

### ClientManagers and the Thread Pool Executor (Project 1)

The task queue is managed by a thread pool executor service. The basic structure is the following: after
looking up the correct ClientManager for that client, it adds a task to the Thread Pool for that ClientManager that is appropriate to the API call made by the client. If the Client called Publish(...), e.g., a call to clientManager.publish(...) will be added to the task queue. When a thread becomes available
for that task, it is immediately executed by the appropriate clientManager. This structure allows us to scale
out as needed when demand increases, because the Thread Pool adjusts its size to the demand.

### Communicating with the MessageStore (Project 1)

ClientManagers maintain subscription lists as well as past publications for its client. When a publish(...)
task is executed, the client manager communicates with the MessageStore to add the new publication, if it
doesn't already exist.

Retrieving subscriptions utilizes a pull approach: a single pullScheduler thread periodically wakes up and
adds a retrieve(...) task to the Thread Pool Executor for given ClientManagers. When a thread is available
for that retrieve(...) task, the ClientManager for that task asks the MessageStore for all matching publications that arrived after the last publication the ClientManager retrieved (which the ClientManager
keeps track of). Once retrieved, the ClientManager sends these back to the client.

### Messages, Protocols, and Matching (Project 1)

The entire system is defined generically, in the following sense: the protocol used for sending,
interpreting, and storing messages is injected as a parameter at the time of initialization. There are
three classes that support this structure: Message, Protocol, and Query. The effect of these three classes
is that you can completely changed the sending, interpreting, and storing format of all communication by
simply instantiating and injecting a different Protocol at initialization.

Matching in the MessageStore proceeds by a set-theoretic logic. The MessageStore is a ConcurrentHashMap that stores a String as its key. That key is a field-value combination for a given field in the Message's Protocol for the system. For example, a key can exist for "type_Sports" or "org_UMN". The values for the MessageStore's map is a PublicationList.

Every Message, publication or subscription, contains a Query. A query will store the appropriate key-value Strings under which that Message is categorized (e.g., "type_Sports" if the type for that message is sports) for each field in the protocol. A publication is added to a key's publication list if one of these field-value pairs of the Message match that key. Thus a reference for the publication "Sports;Me;UMN;content1" will be added to "type_Sports", "orginator_Me", etc.

Upon retrieval, we simply use the subscription Message's query to retrieve the publications that match that query's field-value pairs. We take the intersection of the publications matched from each of those keys, and return them to the client. This is fast because we only retrieve publications from a PublicationList that arrived after the lastReceived for that query field, and because these lookups are fast. We use TreeSets to hold the matches, so intersecting is reasonably fast as well (i.e., through O(logn) contains()).

# How to Build:

Navigate to the project root. Run:

    gradle clean build -x test.

# How to Run:

FIRST run the super_server_getlist_RMI_port (provided by Kartik Ramkrishnan) in the project root.

Next create replica servers on the command line:

## Server

To run a server:

Navigate to project root, cd to /build/classes/java/main.

The startup command is long: there are necessarily many options. All are required.

Be sure to take note of the order. Run:

'java BulletinBoardServerMain [server machine IP] [server port] [heartbeat port] [registry super server IP] [consistency policy] [num replicas]'

    * consistency policy should be one of: sequential, readyourwrites, quorum
    * Carefully choose the machine IPs: in general, this should not be a loopback address, it should be the 'actual' IP
      of the machine (e.g., that you would find via an <ifconfig -a>)

## Client

Navigate to project root, cd to /build/classes/java/main. Run 'java BulletinBoardClientMain'.

This begins interactive mode, where a menu will give you options. Be sure to type
'terminate' at end of session to end it!

# Testing Description:

## Current Tests:

### Replication / Consistency
* Test that all replicas agree on who the coordinator is for all consistency models.
* Read-your-writes: client publishes to one server, moves to another, and successfully reads effect of previous write at new server.
* Sequential consistency: after publishing (small and large version), clients at all servers read same order of message execution.
* Read quorum consistency: reads by clients at any server retrieve highest message id in system.
* Write quorum consistency: publication by clients at different servers causes all publications to be found at least N / 2 + 1servers.

### How to run

* The new tests for project 2 listed above are in JUnit, and you can use gradle
to run them.
* BE SURE the registry super server is running.
* NOT recommended: simply running gradle cleanTest test. This leads to all tests passing, HOWEVER, it also causes errors because JUnit will try to execute in a staggered manner, and this causes initialization/cleanup exceptions as one test's Before overlaps another's After methods.
* Instead, run each test class one at a time by running:
  <gradle cleanTest test --tests \*<TestClassName>
* For example: 'gradle cleanTest test -tests \*TestSequentialConsistency'
(the backslash is an escape char in markdown (not part of the command)).
* List of test classes:
  * TestReadYourWritesServers
  * TestSequentialServers
  * TestQuorumServers
  * TestReadYourWritesConsistency
  * TestSequentialConsistency
  * TestQuorumConsistency

## Basic Messaging Behavior (from project 1)

#### Client Side:
* Run single publisher, single subscriber, with and without wildcard matching tests.
* Run multiple subscribers, with and without wildcard matching tests.
* Run invalid publication and invalid subscription tests.
* Run high load test.
* Run test leave.
#### Server side:
* Deregister and GetLists with registry server.

#### To run basic message behavior tests (from project 1)

***(Tests from project 1, used only as regression tests)***

To run basic tests: first run the registry super server (as above) then in /build/classes/java/main run:
'java ServerMain [server machine ip]'
    and then:
'java ClientMain [server machine ip] [runAllTests]'.
