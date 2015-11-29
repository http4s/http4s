name := "http4s-examples"

description := "Common code for http4s examples on various backends"

seq(Revolver.settings: _*)

libraryDependencies ++= Seq(
  logbackClassic,
  jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
)

// Adds ALPN to the boot classpath for HTTP/2 support
javaOptions in run <++= (managedClasspath in Runtime) map { attList =>
   for {
     file <- attList.map(_.data)
     path = file.getAbsolutePath if path.contains("jetty.alpn")
   } yield "-Xbootclasspath/p:" + path
}

publishArtifact := false
