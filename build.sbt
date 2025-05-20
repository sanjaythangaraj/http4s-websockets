ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "http4s-websockets"
  )

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % "1.0.0-M44",
  "org.http4s" %% "http4s-circe" % "1.0.0-M44",
  "io.circe" %% "circe-generic" % "0.14.13",
  "org.http4s" %% "http4s-dsl" % "1.0.0-M44",
  "org.typelevel" %% "cats-parse" % "1.1.0",
  "org.typelevel" %% "log4cats-core"    % "2.7.0",
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
  "ch.qos.logback" % "logback-classic" % "1.5.18"
)

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x                   => (assembly / assemblyMergeStrategy).value.apply(x)
}
assembly / mainClass             := Some("com.example.websockets.Program")
assembly / assemblyJarName       := "wschat.jar"
