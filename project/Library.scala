import sbt._

object Library {
  private val rdbcVersion = "0.0.77"
  private val unipoolVersion = "0.0.3"

  val unipoolScala = "io.github.povder.unipool" %% "unipool-scala" % unipoolVersion
  val unipoolJava = "io.github.povder.unipool" %% "unipool-java" % unipoolVersion
  val rdbcScalaApi = "io.rdbc" %% "rdbc-api-scala" % rdbcVersion
  val rdbcJavaApi = "io.rdbc" %% "rdbc-api-java" % rdbcVersion
  val rdbcImplbase = "io.rdbc" %% "rdbc-implbase" % rdbcVersion
  val rdbcJavaAdapter = "io.rdbc" %% "rdbc-java-adapter" % rdbcVersion
  val immutables = "org.immutables" % "value" % "2.5.6"
  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.25"

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0"
}
