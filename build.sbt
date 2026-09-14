ThisBuild / organization           := "io.janstenpickle"
ThisBuild / versionPolicyIntention := Compatibility.None

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .dependsOn(`trace4cats-kafka-client`)

lazy val `trace4cats-kafka-client` = module
  .settings(Test / fork := true)
