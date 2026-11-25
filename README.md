# Telemetry

Proof of Concept for a HTTP server using Http4s, publishing telemetry to a publish/subscribe middleware, and
a consumer using fs2 stream that generates real-time reports from those telemetry inputs.

## About

This implementation is naive. But it gives a starting point for how to develop a data pipeline from clients
sending telemetry data to a server, to a consumer generating reports in real-time. The design goals are to
use the functional programing paradigm, to support better expressiveness, local reasoning and
compositionality. The Typelevel ecosystem helps with this task.

An important and complex issue with data pipelines is the need for backpressure, and handling data that
potentially doesn't fit in memory. Like for concurrency, you'd better find a good library to solve this 
problem instead of trying to reinvent the wheel. Fs2 to the rescue to solves this nicely!

## Instructions

Prerequisite: you need to have `sbt` installed on your computer.

Usage of the program is done in the CLI.

The build has 4 sbt projects, run from the repository root: `common` (code shared by the others),
`webApi` (folder `web-api`), `reportService` (folder `report-service`) and `simulator` (a load generator).

First, publish the Docker images, from the repository root:
```
sbt webApi/docker:publishLocal reportService/docker:publishLocal
```

With Docker compose:
```
docker compose down -v
docker compose build --no-cache
docker compose up --scale web_api=3 -d
```

To see the output of the report service:
```
docker logs -f report_service
```

Every 10 s, a report will be generated, counting the number of messages received, and counting the number of
messages with CPU in each of 10 divisions, i.e. 
```
division0 = [0, 10)
division10 = [10, 20)
...
division90 = [90, 100]
```
(note: we make the assumption that a CPU value must be strictly positive).

If you want to specify another value for the periodicity at which the report is generated, run the program
like e.g.:
`sbt -Dreport.periodSec=5 reportService/run`

To post telemetry data, you can post requests messages like this with `curl`:
```
curl -v 'http://localhost:8080/telemetry/cpu' --json '
{
  "timestamp": "2020-01-01T00:00:00Z",
  "deviceId": "device-1234",
  "clientVersion": "v1.2.3",
  "cpu": 83.2
}'
```

To send a steady load, run the simulator from the repository root:
```
sbt -Dsimulator.ratePerSec=500 simulator/run
```
It sends `simulator.ratePerSec` measurements per second (default 100) to `simulator.target` (default
`http://localhost:8080/telemetry/cpu`), with at most `simulator.concurrency` requests in flight (default 16).
Each measurement has the current time, 1 of 100 devices, 1 of 3 client versions and a random CPU value.
Stop it with Ctrl+C.

### Messaging settings

Both services read the RabbitMQ host from the `RABBITMQ_HOST` environment variable (`docker-compose.yml` sets
it to `rabbitmq`; the default is `localhost`). The other settings are system properties, e.g.
`-Dmessaging.port=5672`, `-Dmessaging.user=admin`, `-Dmessaging.password=admin`.

The report service acknowledges each message only after counting it, and lets at most
`messaging.prefetchCount` messages (default 50) be unacknowledged at once. When it is slower than the incoming
load, the backlog therefore stays in RabbitMQ, visible as "Ready" messages in the management UI at
http://localhost:15672 (user `admin`, password `admin`). A message that cannot be decoded is logged and
rejected; it is not requeued.

### Slow-down knob

To see a backlog build up, slow the report service down while it runs. It waits this long on each message
before counting it (0 to 60000 ms, default 0):
```
curl -X PUT localhost:8081/knob/delay --json '{"millis": 50}'
curl localhost:8081/knob/delay
```
At 50 ms per message it handles at most 20 messages/s, so with the simulator at 100/s the "Ready" count in
RabbitMQ grows. Set it back to 0 and the queue drains.

### Pipeline stats

Every second, the report service publishes what happened in the last second as Server-Sent Events:
```
curl -N localhost:8081/dashboard/events
```
```
event: stats
data: {"processedPerSec":20,"delayAvgMillis":8150,"delayMaxMillis":8900,"queue":{"ready":693,"unacked":50},"knobDelayMillis":50}
```
- `processedPerSec`: messages counted in the last second.
- `delayAvgMillis`, `delayMaxMillis`: time between a measurement's `timestamp` and the moment it was counted.
- `queue`: Ready and Unacked messages, read from RabbitMQ's management API (port `messaging.managementPort`,
  default 15672); `null` when that API does not answer within 800 ms.
- `knobDelayMillis`: the current slow-down knob.

`docker-compose.yml` mounts `rabbitmq/20-collect-statistics.conf`, which makes RabbitMQ refresh its queue figures
every second instead of every 5 (the file explains which settings that takes). After changing it, recreate the
RabbitMQ container: `docker compose up -d --force-recreate rabbitmq`.

### Tests

```
sbt test
```
The report service's messaging tests start a real RabbitMQ with Testcontainers, so Docker must be running.

## Notes

* Obviously, in real life, the server and the consumer should be deployed and run separately.

* When the number of telemetry requests becomes large, (which it should!), we would need to scale
horizontally. We need an infrastructure (in the "cloud") that supports elastic scale, i.e. adding/removing
nodes automatically.

* Caveat: the current implementation processes the messages in the order they are published to the middleware.
This order could differ significantly from the order of the actual time of the received telemetry
measurements. The reason is that we are here using HTTP for our communication between the clients and our
server, which can have latency issues. If we want to use the time from the measurements to order the messages,
the consumer stream should buffer its inputs for an arbitrary time (say 10 seconds), order them by timestamp,
and then process them. This would lead to slow messages being dropped(i.e. ignored), and more delay in the
reporting.

* If we want to avoid the aforementioned problems due to HTTP and its latency, one should consider using
WebSockets instead of HTTP. However, a word of caution: WebSockets make horizontal scaling difficult because
of their stateful nature.

* The middleware here is a very rough approximation of a message broker. We'd probably use Kafka in
production, for its nice scalability; its strong delivery guarantees and its persistence capabilities could be
nice to have, when/if the use case evolves. We would then handle messages in chunks, instead of one at a time,
for performance reasons.

## To do

* Scaling out the server; use Kubernetes.
* Fix the environment variable handling. I don't like the current hard-coded solution.
* Make it configurable to divide the CPU values in more or less divisions than the actual 10.
* Add tests. ScalaCheck generators...
* Create a simulator that can generate randomized inputs and query our server.
* Add more categories in telemetry messages, e.g.info about OS, hardware, etc. to get better understanding for
the correlations between categories and high CPU usage.
* Persistence of reports for later usage, or maybe for aggregating a "bigger" report (on a longer period), that
would be generated e.g. once a day. I am not sure which Database to use here, and we would need to investigate
the usual tradeoffs for combining scaling, consistency and availibilty. Another point is that we are bounded
to choose a database that we can integrate nicely with fs2. At first sight, I'd say that eventual consistency
is good enough, and Cassandra would be a nice match. We could have a look at Riak and CouchDB too, in case
there exists a driver/connector for fs2.
* If we need a "bigger" aggregated report on a longer period, we could:
  1. do as stated above, and persist small reports that we then create the big report from.
  2. have a second consumer on the same topic.
  3. be recursive: our consumer would send events containing the small reports data to a new topic, and then
another consumer (on this new topic) would create the "big" reports.
* Have a client to present the reports as graphs.
* API documentation. OpenAPI maybe? A bit overkill though, for one single and simple endpoint...
