val Http4sVersion = "0.23.32"
val CirceVersion = "0.14.1"
val CirceYamlVersion = "0.16.1"
val RefinedVersion = "0.9.27"
val Fs2Version = "2.2.0"
val CirisVersion = "3.11.0"
val ScalaCheckVersion = "1.14.1"
val LogbackVersion = "1.2.5"
val MunitVersion = "0.7.27"
val MunitCatsEffectVersion = "1.0.5"
val Fs2RabbitMqClientVersion = "5.4.4"
val TestcontainersVersion = "1.21.4"

lazy val commonSettings = Seq(
  organization := "bzh.ya2o",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.13.6",
  scalacOptions -= "-Xfatal-warnings", // disable fatal warnings
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "3.6.3",
    "io.circe" %% "circe-yaml" % CirceYamlVersion,
    "is.cir" %% "ciris" % CirisVersion,
    "is.cir" %% "ciris-refined" % CirisVersion,
    "is.cir" %% "ciris-circe-yaml" % CirisVersion,
    "org.http4s" %% "http4s-ember-server" % Http4sVersion,
    "org.http4s" %% "http4s-ember-client" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion,
    "io.circe" %% "circe-core" % CirceVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-parser" % CirceVersion,
    "io.circe" %% "circe-refined" % CirceVersion,
    "eu.timepit" %% "refined" % RefinedVersion,
    "eu.timepit" %% "refined-cats" % RefinedVersion,
    "com.github.fd4s" %% "fs2-kafka" % Fs2Version,
    "dev.profunktor" %% "fs2-rabbit" % Fs2RabbitMqClientVersion,
    "dev.profunktor" %% "fs2-rabbit-circe" % Fs2RabbitMqClientVersion,
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % Test,
    "org.scalameta" %% "munit" % MunitVersion % Test,
    "org.typelevel" %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test
  ),
  run / fork := false,
  cancelable in Global := false,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  testFrameworks += new TestFramework("munit.Framework")
)

lazy val serviceSettings = commonSettings ++ Seq(
  Compile / mainClass := Some("bzh.ya2o.telemetry.Main"),
  dockerBaseImage := "eclipse-temurin:17-jre"
)

lazy val root = (project in file("."))
  .aggregate(common, webApi, reportService)
  .settings(
    name := "telemetry",
    publish / skip := true
  )

lazy val common = (project in file("common"))
  .settings(commonSettings)
  .settings(name := "telemetry-common")

lazy val webApi = (project in file("web-api"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(common)
  .settings(serviceSettings)
  .settings(name := "telemetry-web-api")

lazy val reportService = (project in file("report-service"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(common)
  .settings(serviceSettings)
  .settings(
    name := "telemetry-report-service",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-testkit" % "3.6.3" % Test,
      "org.testcontainers" % "rabbitmq" % TestcontainersVersion % Test
    )
  )

addCommandAlias("validate", "clean; cleanFiles; reload; update; test; scalafmtCheck; scalafmtSbtCheck; doc")
addCommandAlias("dev", "test; scalafmtAll; scalafmtSbt")
//addCommandAlias("noWarnings", "; set compile/logLevel := Level.Error")
