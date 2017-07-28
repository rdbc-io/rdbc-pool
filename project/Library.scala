import sbt._

object Library {
  private val rdbcVersion = "0.0.68"

  val rdbcScalaApi = "io.rdbc" %% "rdbc-api-scala" % rdbcVersion
  val rdbcImplbase = "io.rdbc" %% "rdbc-implbase" % rdbcVersion
  val rdbcUtil = "io.rdbc" %% "rdbc-util" % rdbcVersion
  val stm = "org.scala-stm" %% "scala-stm" % "0.8"

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.3" % Test
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test
}
