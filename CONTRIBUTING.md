## Development Setup

### Dependencies

A dev environment requires [Docker](https://www.docker.com/), [Docker Compose](https://docs.docker.com/compose/) and JDK 8 (e.g. [OpenJDK 8](http://openjdk.java.net/install/)).

### Getting Started

Start your dev server with

```
docker-compose up --build
```

Run the tests with

```
./activator test
```

Build a release with

```
./activator docker:publishLocal
```

## Simplified Akka Persistence Setup

We decided to use a simplified Akka persistence setup in which we rely on the
data storage for high availability and failover; by this, we avoid having to set
up an Akka cluster. At the same time, this means that in order to prevent
concurrent and conflicting persistent actor states between multiple servers, we
have to bring up a persistent actor in each request, replay its history, and
stop the actor after the request has been processed. The request processing flow
that is entailed by this decision is detailed in the following section.

## Http Request Processing Flow

![Toguru Request Flow](https://cloud.githubusercontent.com/assets/6724788/18165628/99d02770-7046-11e6-854f-57fef3071016.png)

Http requests that modify persistent data (i.e. POST and PUT) are processed in
the following way:

1. *request:* a [controller action](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleController.scala#L63)
is invoked with a request to process.
2. *create:* the persistent actor that is needed to process the request [is
created](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleController.scala#L41) -
as usual, indirectly through the Akka actor system.
3. *recover:* On actor initialization, Akka persistence replays all events from
the database to the actor, and [the actor recovers](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L47-L59)
its internal state based on
the replayed events.
4. *ask:* the controller [asks the actor](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleController.scala#L70)
(asynchronously) to execute a command, and the [actor validates](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L67-L70)
that the command can be executed.
5. *persist:* After successful validation, the [actor persists derived events](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L71)
to the journal. A [custom event adapter](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleEventSerialization.scala#L42)
(Tagging in the picture) tags the events for later persistent queries, and a
[custom ProtoBuf serializer](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleEventSerialization.scala#L15)
serializes the events [specified as protobuf messages](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/conf/protobuf/toggles.proto#L7-L12).
6. *recover:* Akka invokes the [receiveRecover method](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L48-L49)
to replay the successfully persisted events to the actor.
7. *update:* The actor [updates](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L48-L49) its [internal state](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L43)
based on the newly persisted events.
8. *reply:* A [reply is sent](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleActor.scala#L73)
to the controller that describes the result of the command.
9. *stop:* The controller [stops the actor](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleController.scala#L44),
since it has completed its task.
10. *result:* A http result [is send to the client](https://github.com/AutoScout24/toguru/blob/da8f1d70d3558c71eec923c1fc385a8324d3b33a/app/toguru/toggles/ToggleController.scala#L71-L77)
based on the reply from the persistent actor.

The processing of GET requests is similar; since it does not alter the database
and the actor state, the steps 5. (persist), 6. (recover), and 7. (update) can
be omitted in the request flow.
