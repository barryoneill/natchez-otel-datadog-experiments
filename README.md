**ticket**: https://github.com/typelevel/natchez/issues/847

This is a sample app, with a single Main class which:

* creates a natchez-opentelemetry endpoint
* manually creates a span (A) using the endpoint directly
* starts and makes a trace-triggering POST to two http4s services
  * service 1 uses the natchez-opentelemetry middleware (B) and service 2 uses davenport's (C)
* then, shows that for the traces picked up by datadog:
  * A has a valid traceID
  * B and C (using the http4s middleware) don't

To run the problem example, you need to:

* set env vars DD_ENV and DD_SERVICE (to anything)
* download the [datadog agent JAR](https://dtdg.co/latest-java-tracer)
* Run the Main app with the following parameters `-javaagent:/path/to/dd-java-agent.jar -Ddd.trace.otel.enabled=true`

notes:
 - The output is a little wide, so maybe reduce your terminal font size:
 - You'll see a message like `Failed to connect to localhost/[0:0:0:0:0:0:0:1]:8126 (Will not log errors for 5 minutes)` in the logs which can be ignored (the demo has a crude interceptor to catch the 'outgoing' traces)


```
❯ export DD_ENV=fooEnv
❯ export DD_SERVICE=fooSvc
❯ export SBT_OPTS="-javaagent:/Users/barry/Downloads/dd-java-agent.jar -Ddd.trace.otel.enabled=true"
❯ sbt run

OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[dd.trace 2023-07-13 15:43:42:688 -0400] [dd-task-scheduler] INFO datadog.trace.agent.core.StatusLogger - DATADOG TRACER CONFIGURATION {"version":"1.16.3~3de9a1fa73","os_name":"Mac OS X","os_version":"13.4.1","architecture":"aarch64","lang":"jvm","lang_version":"17.0.7","jvm_vendor":"Amazon.com Inc.","jvm_version":"17.0.7+7-LTS","java_class_version":"61.0","http_nonProxyHosts":"local|*.local|169.254/16|*.169.254/16","http_proxyHost":"null","enabled":true,"service":"fooSvc","agent_url":"http://localhost:8126","agent_error":true,"debug":false,"trace_propagation_style_extract":["datadog"],"trace_propagation_style_inject":["datadog"],"analytics_enabled":false,"sampling_rules":[{},{}],"priority_sampling_enabled":true,"logs_correlation_enabled":true,"profiling_enabled":false,"remote_config_enabled":true,"debugger_enabled":false,"appsec_enabled":"ENABLED_INACTIVE","telemetry_enabled":true,"dd_version":"","health_checks_enabled":true,"configuration_file":"no config file present","runtime_id":"3fc5a447-09d8-4916-a267-9c0189ba6666","logging_settings":{"levelInBrackets":false,"dateTimeFormat":"'[dd.trace 'yyyy-MM-dd HH:mm:ss:SSS Z']'","logFile":"System.err","configurationFile":"simplelogger.properties","showShortLogName":false,"showDateTime":true,"showLogName":true,"showThreadName":true,"defaultLogLevel":"INFO","warnLevelString":"WARN","embedException":false},"cws_enabled":false,"cws_tls_refresh":5000,"datadog_profiler_enabled":true,"datadog_profiler_safe":true}
[info] welcome to sbt 1.9.0 (Amazon.com Inc. Java 17.0.7)
[dd.trace 2023-07-13 15:43:43:221 -0400] [dd-trace-processor] WARN datadog.trace.agent.common.writer.ddagent.DDAgentApi - Error while sending 5 (size=2KB) traces. Total: 5, Received: 5, Sent: 0, Failed: 5. java.net.ConnectException: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:8126 (Will not log errors for 5 minutes)
[info] running net.barryoneill.natchez_to_http4s.Main
15:43:46.344 INF appserver[dd.service=fooSvc, dd.env=fooEnv] direct use of entrypoint - Span Id:000000000000000014576a24b90b8d37, kernel:Kernel(Map())
15:43:47.013 INF appserver[dd.service=fooSvc, dd.env=fooEnv] http service executing 'business' logic
15:43:48.111 INF appserver[dd.service=fooSvc, dd.env=fooEnv] http service executing 'business' logic
15:43:48.605 INF appserver[dd.service=fooSvc, dd.env=fooEnv] Would be sending 5 span(s) to datadog:
+--------------+-----------------------+-----------------------+----------------------------------------+---------------------+---------------------+--------------------------------------------------------------------+--------------+-----------+---------------+
| When         | Operation Name        | Resource Name         | DD Trace Id                            | DD Span Id          | DDParentSpanId      | Tags                                                               | Service Name | Span Type | Duration (ms) |
+--------------+-----------------------+-----------------------+----------------------------------------+---------------------+---------------------+--------------------------------------------------------------------+--------------+-----------+---------------+
| 15:43:46.337 | Direct_Endpoint_Usage | Direct_Endpoint_Usage |  64b: 000000000000000014576a24b90b8d37 | 4054336080662395000 | 0                   | _dd.profiling.enabled=0                                            | fooSvc       | internal  | 6             |
|              |                       |                       |                                        |                     |                     | _dd.trace_span_attribute_schema=0                                  |              |           |               |
|              |                       |                       |                                        |                     |                     | env=fooEnv                                                         |              |           |               |
|              |                       |                       |                                        |                     |                     | language=jvm                                                       |              |           |               |
|              |                       |                       |                                        |                     |                     | process_id=48250                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | runtime-id=3fc5a447-09d8-4916-a267-9c0189ba6666                    |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.id=133                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.name=io-compute-7                                           |              |           |               |
| 15:43:46.352 | /biz                  | POST /biz             | 128b: 00000000000000000000000000000000 | 4308140243084585245 | 0                   | _dd.profiling.enabled=0                                            | fooSvc       | internal  | 961           |
|              |                       |                       |                                        |                     |                     | _dd.trace_span_attribute_schema=0                                  |              |           |               |
|              |                       |                       |                                        |                     |                     | env=fooEnv                                                         |              |           |               |
|              |                       |                       |                                        |                     |                     | http.method=POST                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | http.status_code=200                                               |              |           |               |
|              |                       |                       |                                        |                     |                     | http.url=/biz                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | language=jvm                                                       |              |           |               |
|              |                       |                       |                                        |                     |                     | process_id=48250                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | runtime-id=3fc5a447-09d8-4916-a267-9c0189ba6666                    |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.id=129                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.name=io-compute-3                                           |              |           |               |
| 15:43:46.597 | fakeBizLogic          | fakeBizLogic          | 128b: 00000000000000000000000000000000 | 5724611567296865609 | 4308140243084585245 | businessAttr=STONKS                                                | fooSvc       | internal  | 417           |
|              |                       |                       |                                        |                     |                     | env=fooEnv                                                         |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.id=129                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.name=io-compute-3                                           |              |           |               |
| 15:43:47.327 | Http Server - POST    | POST /biz             | 128b: 00000000000000000000000000000000 | 7224963246604818194 | 0                   | _dd.profiling.enabled=0                                            | fooSvc       | internal  | 1240          |
|              |                       |                       |                                        |                     |                     | _dd.trace_span_attribute_schema=0                                  |              |           |               |
|              |                       |                       |                                        |                     |                     | env=fooEnv                                                         |              |           |               |
|              |                       |                       |                                        |                     |                     | exit.case=succeeded                                                |              |           |               |
|              |                       |                       |                                        |                     |                     | http.client_ip=127.0.0.1                                           |              |           |               |
|              |                       |                       |                                        |                     |                     | http.flavor=1.1                                                    |              |           |               |
|              |                       |                       |                                        |                     |                     | http.host=localhost:9000                                           |              |           |               |
|              |                       |                       |                                        |                     |                     | http.method=POST                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.accept=text/*                           |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.connection=keep-alive                   |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.content_length=0                        |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.date=Thu, 13 Jul 2023 19:43:47 GMT      |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.host=localhost:9000                     |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request.header.string.user_agent=http4s-ember/0.23.22         |              |           |               |
|              |                       |                       |                                        |                     |                     | http.request_content_length=0                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | http.response.header.string.content_length=13                      |              |           |               |
|              |                       |                       |                                        |                     |                     | http.response.header.string.content_type=text/plain; charset=UTF-8 |              |           |               |
|              |                       |                       |                                        |                     |                     | http.response_content_length=13                                    |              |           |               |
|              |                       |                       |                                        |                     |                     | http.status_code=200                                               |              |           |               |
|              |                       |                       |                                        |                     |                     | http.target=/biz                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | http.url=/biz                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | http.user_agent=http4s-ember/0.23.22                               |              |           |               |
|              |                       |                       |                                        |                     |                     | language=jvm                                                       |              |           |               |
|              |                       |                       |                                        |                     |                     | net.peer.ip=127.0.0.1                                              |              |           |               |
|              |                       |                       |                                        |                     |                     | net.peer.port=56766                                                |              |           |               |
|              |                       |                       |                                        |                     |                     | process_id=48250                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | runtime-id=3fc5a447-09d8-4916-a267-9c0189ba6666                    |              |           |               |
|              |                       |                       |                                        |                     |                     | span.kind=server                                                   |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.id=135                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.name=io-compute-9                                           |              |           |               |
| 15:43:47.775 | fakeBizLogic          | fakeBizLogic          | 128b: 00000000000000000000000000000000 | 8328755773498611729 | 7224963246604818194 | businessAttr=STONKS                                                | fooSvc       | internal  | 336           |
|              |                       |                       |                                        |                     |                     | env=fooEnv                                                         |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.id=135                                                      |              |           |               |
|              |                       |                       |                                        |                     |                     | thread.name=io-compute-9                                           |              |           |               |
+--------------+-----------------------+-----------------------+----------------------------------------+---------------------+---------------------+--------------------------------------------------------------------+--------------+-----------+---------------+
15:43:48.606 ERR appserver[dd.service=fooSvc, dd.env=fooEnv] 4 out of 5 datadog spans had empty traceIds
```
