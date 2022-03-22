import Dependencies._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport.assemblyMergeStrategy

/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  SETTINGS DEFINITIONS                                    ##
   ##                                                                                          ##
   ##############################################################################################
 */

// integrationConfig and wipConfig are used to define separate test configurations for integration testing
// and work-in-progress testing
lazy val IntegrationConfig = config( "integration" ) extend ( Test )
lazy val WipConfig = config( "wip" ) extend ( Test )

lazy val commonSettings = {
    inConfig( IntegrationConfig )( Defaults.testTasks ) ++
    inConfig( WipConfig )( Defaults.testTasks ) ++
    Seq(
        organization := "com.twosixlabs.dart.tenants",
        scalaVersion := "2.12.7",
        resolvers ++= Seq( "Maven Central" at "https://repo1.maven.org/maven2/",
                           "JCenter" at "https://jcenter.bintray.com",
                           "Local Ivy Repository" at s"file://${System.getProperty( "user.home" )}/.ivy2/local/default" ),
        javacOptions ++= Seq( "-source", "1.8", "-target", "1.8" ),
        scalacOptions += "-target:jvm-1.8",
        scalacOptions += "-Ypartial-unification",
        useCoursier := false,
        libraryDependencies ++= logging ++
                                scalaTest ++
                                betterFiles ++
                                scalaMock ++
                                dartCommons,
        excludeDependencies ++= Seq( ExclusionRule( "org.slf4j", "slf4j-log4j12" ),
                                                                                  ExclusionRule( "org.slf4j", "log4j-over-slf4j" ),
                                                                                  ExclusionRule( "log4j", "log4j" ),
                                                                                  ExclusionRule( "org.apache.logging.log4j", "log4j-core" ) ),
        // `sbt test` should skip tests tagged IntegrationTest
        Test / testOptions := Seq( Tests.Argument( "-l", "annotations.IntegrationTest" ) ),
        // `sbt integration:test` should run only tests tagged IntegrationTest
        IntegrationConfig / parallelExecution := false,
        IntegrationConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.IntegrationTest" ) ),
        // `sbt wip:test` should run only tests tagged WipTest
        WipConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.WipTest" ) ) )
}

lazy val disablePublish = Seq( skip.in( publish ) := true )

lazy val assemblySettings = Seq(
    libraryDependencies ++= scalatra ++ jackson,
    assemblyMergeStrategy in assembly := {
        case PathList( "META-INF", "MANIFEST.MF" ) => MergeStrategy.discard
        case PathList( "reference.conf" ) => MergeStrategy.concat
        case x => MergeStrategy.last
    },
    Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/webapp",
    test in assembly := {},
    mainClass in(Compile, run) := Some( "Main" ) )

sonatypeProfileName := "com.twosixlabs"
inThisBuild(List(
    organization := "com.twosixlabs.dart.tenants",
    homepage := Some(url("https://github.com/twosixlabs-dart/dart-tenants")),
    licenses := List("GNU-Affero-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.en.html")),
    developers := List(
        Developer(
            "twosixlabs-dart",
            "Two Six Technologies",
            "",
            url("https://github.com/twosixlabs-dart")
        )
    )
))

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  PROJECT DEFINITIONS                                     ##
   ##                                                                                          ##
   ##############################################################################################
 */

lazy val root = ( project in file( "." ) )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .aggregate( tenantsApi, tenantsControllers, tenantsMicroservice, tenantsClient )
  .settings( name := "dart-tenants", disablePublish )

lazy val tenantsApi = ( project in file( "tenants-api" ) )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= jackson
                              ++ logging
                              ++ dartCommons
                              ++ dartRest
                              ++ scalaTest
                              ++ tapir
                              ++ json4s )

lazy val tenantsControllers = ( project in file( "tenants-controllers" ) )
  .dependsOn( tenantsApi )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings( commonSettings,
             libraryDependencies ++= dartRest ++ dartAuth ++ jackson ++ scalatra,
             dependencyOverrides ++= Seq( "com.twosixlabs.dart" %% "dart-auth-commons" % dartAuthVersion,
                                          "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
                                          "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
                                          "com.arangodb" %% "velocypack-module-scala" % "1.2.0",
                                          "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
                                          "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
                                          "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion,
                                          "com.sksamuel.elastic4s" %% "elastic4s-json-jackson" % elastic4sVersion ) )

lazy val tenantsMicroservice = ( project in file( "tenants-microservice" ) )
  .dependsOn( tenantsControllers )
  .configs( IntegrationConfig, WipConfig )
  .enablePlugins( JavaAppPackaging )
  .settings( commonSettings,
             assemblySettings,
             disablePublish,
             libraryDependencies ++= dartCommons ++ scalatra ++ jackson ++ typesafeConfig ++ dartEs ++ dartAuth,
             dependencyOverrides ++= Seq( "com.softwaremill.sttp.model" %% "core" % "1.1.4",
                                          "com.twosixlabs.dart" %% "dart-auth-commons" % dartAuthVersion,
                                          "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
                                          "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
                                          "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
                                          "com.arangodb" %% "velocypack-module-scala" % "1.2.0",
                                          "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
                                          "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion,
                                          "com.sksamuel.elastic4s" %% "elastic4s-json-jackson" % elastic4sVersion ) )

lazy val tenantsClient = ( project in file( "tenants-client" ) )
  .dependsOn( tenantsApi )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings( commonSettings,
             libraryDependencies ++= betterFiles ++ okhttp ++ jackson )

ThisBuild / useCoursier := false
