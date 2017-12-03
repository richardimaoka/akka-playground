import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "akka-playground",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.6",
      "com.typesafe.akka" %% "akka-agent" % "2.5.6",
      "com.typesafe.akka" %% "akka-camel" % "2.5.6",
      "com.typesafe.akka" %% "akka-cluster" % "2.5.6",
      "com.typesafe.akka" %% "akka-cluster-metrics" % "2.5.6",
      "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.6",
      "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.6",
      "com.typesafe.akka" %% "akka-distributed-data" % "2.5.6",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.5.6",
      "com.typesafe.akka" %% "akka-osgi" % "2.5.6",
      "com.typesafe.akka" %% "akka-persistence" % "2.5.6",
      "com.typesafe.akka" %% "akka-persistence-query" % "2.5.6",
      "com.typesafe.akka" %% "akka-persistence-tck" % "2.5.6",
      "com.typesafe.akka" %% "akka-remote" % "2.5.6",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.6",
      "com.typesafe.akka" %% "akka-stream" % "2.5.6",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.6",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.6",
      "com.typesafe.akka" %% "akka-typed" % "2.5.6",
      "com.typesafe.akka" %% "akka-http" % "10.0.10",
      "com.typesafe.akka" %% "akka-contrib" % "2.5.6",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
      scalaTest % Test
    )
  )
