ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "natchez-otel-datadog-experiments",
    libraryDependencies ++= Seq(
      // http
      "org.http4s" %% "http4s-dsl"          % "0.23.22",
      "org.http4s" %% "http4s-ember-server" % "0.23.22",
      "org.http4s" %% "http4s-ember-client" % "0.23.22",

      // logging
      "org.typelevel" %% "log4cats-slf4j"  % "2.6.0",
      "ch.qos.logback" % "logback-classic" % "1.4.8",
      "ch.qos.logback" % "logback-core"    % "1.4.8",

      // tracing
      "org.tpolecat"      %% "natchez-opentelemetry" % "0.3.3",
      "org.tpolecat"      %% "natchez-http4s"        % "0.5.0",
      "io.chrisdavenport" %% "natchez-http4s-otel"   % "0.3.0-RC1",
      "com.datadoghq"      % "dd-trace-api"          % "1.18.1",


      // rendering
      "com.github.freva" % "ascii-table" % "1.8.0"

    )
  )
