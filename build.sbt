import Settings._
import TemplateReplace.autoImport.mkdocsVariables

import scala.Console._

shellPrompt.in(ThisBuild) := (state => s"${CYAN}project:$GREEN${Project.extract(state).currentRef.project}$RESET> ")


lazy val commonSettings = Vector(
  organization := "io.rdbc.pool",
  organizationName := "rdbc contributors",
  scalaVersion := "2.12.6",
  crossScalaVersions := Vector(scalaVersion.value, "2.11.12"),

  licenses := Vector(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
  ),
  startYear := Some(Copyright.startYear),

  homepage := Some(url("https://github.com/rdbc-io/rdbc-pool")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/rdbc-io/rdbc-pool"),
      "scm:git@github.com:rdbc-io/rdbc-pool.git"
    )
  ),

  buildInfoKeys := Vector(version, scalaVersion, git.gitHeadCommit, BuildInfoKey.action("buildTime") {
    java.time.Instant.now()
  }),

  scalastyleFailOnError := true
) ++ compilationConf ++ scaladocConf ++ developersConf ++ publishConf ++ testConf

lazy val rdbcPoolRoot = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false
  )
  .aggregate(rdbcPoolScala, rdbcPoolJava)

lazy val rdbcPoolScala = (project in file("rdbc-pool-scala"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "rdbc-pool-scala",
    libraryDependencies ++= Vector(
      Library.unipoolScala,
      Library.rdbcScalaApi,
      Library.rdbcImplbase,
      Library.scalatest % Test,
      Library.scalamock % Test
    ),
    scalacOptions in(Compile, doc) ++= Vector(
      "-doc-title", "rdbc connection pool"
    ),
    buildInfoPackage := "io.rdbc.pool"
  )

lazy val rdbcPoolJava = (project in file("rdbc-pool-java"))
  .settings(commonSettings: _*)
  .settings(
    name := "rdbc-pool-java",
    libraryDependencies ++= Vector(
      Library.unipoolJava,
      Library.rdbcJavaApi,
      Library.slf4j,
      Library.immutables % Provided
    )
  ).dependsOn(rdbcPoolScala)

lazy val rdbcPoolDoc = (project in file("rdbc-pool-doc"))
  .enablePlugins(TemplateReplace)
  .settings(
    publishArtifact := false,
    mkdocsVariables := Map(
      "version" -> version.value,
      "rdbc_version" -> Library.rdbcScalaApi.revision
    )
  )
