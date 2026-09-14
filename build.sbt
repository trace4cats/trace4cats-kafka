lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Wconf:any:wv"
      case _ => "-Wconf:any:v"
    }
  },
  Test / fork := true,
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats Kafka")
  .aggregate(`kafka-client`)

lazy val `kafka-client` =
  (project in file("modules/kafka-client"))
    .settings(publishSettings)
    .settings(name := "trace4cats-kafka-client")
