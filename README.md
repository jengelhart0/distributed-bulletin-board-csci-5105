Documentation for CSci 5105 Distributed Bulletin Board System
Author: Joey Engelhart, engel429

Below is README for the publish-subscribe system (repository available here: https://github.com/jengelhart0/publish-subscribe-csci-5105), as the bulletin board uses it as its core messaging but is still in progress.

# System Description:

This is a simple publish subscribe messaging system that uses UDP and Java RMI to communicate.

The API allows client applications to remotely Publish(), Subscribe(), Join(), Unsubscribe(), Leave() and Ping().

## Client Structure

The client is relatively simple. Upon construction, it initializes a listener thread that listens on a UDP socket for messages from the remote server. It attempts to initialize remote communication with the specified
remote server. Upon successful completion of all startup routines, the spins an additional thread that enters
a ping() loop to indicate its continued operation to the remote server with which it has established communication.

## Server Structure

The remote messaging server has more moving parts. Upon initialization, the server creates a Dispatcher singleton, which serves as the gateway/replicatedPubSubServer for the system.

### Dispatcher

The Dispatcher is constructed to perform as little work as possible, so as to avoid being a bottleneck
for the system. Whenever a client Join()s, it adds that client's map that maps IP/Port to a ClientManager
for that Client. When a subsequent remote invocation for that client arrives, it simply looks up the ClientManager for that client, creates a new task for that ClientManager on the task queue, and immediately
returns success or failure to the client.

### ClientManagers and the Thread Pool Executor

The task queue is managed by a thread pool executor service. The basic structure is the following: after
looking up the correct ClientManager for that client, it adds a task to the Thread Pool for that ClientManager that is appropriate to the API call made by the client. If the Client called Publish(...), e.g., a call to clientManager.publish(...) will be added to the task queue. When a thread becomes available
for that task, it is immediately executed by the appropriate clientManager. This structure allows us to scale
out as needed when demand increases, because the Thread Pool adjusts its size to the demand.

### Communicating with the MessageStore

ClientManagers maintain subscription lists as well as past publications for its client. When a publish(...)
task is executed, the client manager communicates with the MessageStore to add the new publication, if it
doesn't already exist.

Retrieving subscriptions utilizes a pull approach: a single pullScheduler thread periodically wakes up and
adds a retrieve(...) task to the Thread Pool Executor for given ClientManagers. When a thread is available
for that retrieve(...) task, the ClientManager for that task asks the MessageStore for all matching publications that arrived after the last publication the ClientManager retrieved (which the ClientManager
keeps track of). Once retrieved, the ClientManager sends these back to the client.

### Messages, Protocols, and Matching

The entire system is defined generically, in the following sense: the protocol used for sending,
interpreting, and storing messages is injected as a parameter at the time of initialization. There are
three classes that support this structure: Message, Protocol, and Query. The effect of these three classes
is that you can completely changed the sending, interpreting, and storing format of all communication by
simply instantiating and injecting a different Protocol at initialization.

Matching in the MessageStore proceeds by a set-theoretic logic. The MessageStore is a ConcurrentHashMap that stores a String as its key. That key is a field-value combination for a given field in the Message's Protocol for the system. For example, a key can exist for "type_Sports" or "org_UMN". The values for the MessageStore's map is a PublicationList.

Every Message, publication or subscription, contains a Query. A query will store the appropriate key-value Strings under which that Message is categorized (e.g., "type_Sports" if the type for that message is sports) for each field in the protocol. A publication is added to a key's publication list if one of these field-value pairs of the Message match that key. Thus a reference for the publication "Sports;Me;UMN;content1" will be added to "type_Sports", "orginator_Me", etc.

Upon retrieval, we simply use the subscription Message's query to retrieve the publications that match that query's field-value pairs. We take the intersection of the publications matched from each of those keys, and return them to the client. This is fast because we only retrieve publications from a PublicationList that arrived after the lastReceived for that query field, and because these lookups are fast. We use TreeSets to hold the matches, so intersecting is reasonably fast as well (i.e., through O(logn) contains()).

# How to Build:

Navigate to the project root. Run ./gradlew clean build.

# How to Run:

## Server

Run the registry_test_server binary on the server machine.

Navigate to project root, cd to /build/classes/java/main. Run 'java ServerMain [server machine ip]'.
You can optionally run registry tests when starting the server with the following:

'java ServerMain [server machine ip] [testTeardown OR testGetList]'

## Client

Navigate to project root, cd to /build/classes/java/main. Run 'java ClientMain [server machine ip]'.

This begins interactive mode, where a menu will give you options.

To run tests on client run 'java ClientMain [server machine ip] [runAllTests]'.

Note that running tests takes awhile because the testing thread sleeps to ensure messages pulls
have arrived.

# Testing Description:

## Current Tests:
### Client Side:
* Run single publisher, single subscriber, with and without wildcard matching tests.
* Run multiple subscribers, with and without wildcard matching tests.
* Run invalid publication and invalid subscription tests.
* Run high load test.
* Run test leave.
### Server side:
* Deregister and GetLists with registry server.
