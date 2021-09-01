import sbt._

object Dependencies {
  object Versions {
    val scala212 = "2.12.14"
    val scala213 = "2.13.6"
    val scala3 = "3.0.2"

    val trace4cats = "0.12.0"

    val fs2Kafka = "2.2.0"

    val kindProjector = "0.13.1"
    val betterMonadicFor = "0.3.1"
  }

  lazy val trace4catsFs2 = "io.janstenpickle"     %% "trace4cats-fs2"     % Versions.trace4cats
  lazy val trace4catsTestkit = "io.janstenpickle" %% "trace4cats-testkit" % Versions.trace4cats

  lazy val fs2Kafka = "com.github.fd4s" %% "fs2-kafka" % Versions.fs2Kafka

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % Versions.kindProjector).cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
}
