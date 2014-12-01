import Http4sDependencies._

name := "http4s-examples"

description := "Examples of using http4s on various backends"

seq(Revolver.settings: _*)

libraryDependencies ++= Seq(
  logbackClassic,
  jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
)

mainClass in Revolver.reStart := Some("com.example.http4s.blaze.BlazeExample")

fork := true

// Adds NPN to the boot classpath for Spdy support
javaOptions in run <++= (managedClasspath in Runtime) map { attList =>
   for {
     file <- attList.map(_.data)
     path = file.getAbsolutePath if path.contains("jetty.npn")
   } yield "-Xbootclasspath/p:" + path
}

publishArtifact := false
