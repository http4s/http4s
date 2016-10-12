import sbt._
import Keys._

import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.git.GitRunner

// Copied from sbt-ghpages to avoid blowing away the old API
// https://github.com/sbt/sbt-ghpages/issues/10
object Http4sGhPages {
  def buildMainSite: Boolean =
    // The main site should only build off master.  We don't want maintenance branches
    // overwriting progress on the master branch that doesn't get merged back.
    scala.util.Properties.envOrNone("TRAVIS_BRANCH") match {
      case Some("master") => true // This is the canonical way to publish the site
      case Some(_) => false // these are the Travis builds we want to suppress
      case None => true // a less surprising default for local builds.
    }

  def cleanSiteForRealz(dir: File, git: GitRunner, s: TaskStreams, apiVersion: (Int, Int)): Unit ={
    val toClean = IO.listFiles(dir).collect {
      case f if f.getName == "api" => new java.io.File(f, s"${apiVersion._1}.${apiVersion._2}")
      case f if f.getName == "docs" => new java.io.File(f, s"${apiVersion._1}.${apiVersion._2}")
      case f if f.getName != ".git" && buildMainSite => f
    }.map(_.getAbsolutePath).toList
    if (!toClean.isEmpty)
      git(("rm" :: "-r" :: "-f" :: "--ignore-unmatch" :: toClean) :_*)(dir, s.log)
    ()
  }

  def synchLocalForRealz(mappings: Seq[(File, String)], repo: File, noJekyll: Boolean, git: GitRunner, s: TaskStreams, v: (Int, Int)) = {
    // (privateMappings, updatedRepository, ghpagesNoJekyll, gitRunner, streams, apiVersion) map {
    // TODO - an sbt.Synch with cache of previous mappings to make this more efficient. */
    val betterMappings = mappings map { case (file, target) => (file, repo / target) }
    // First, remove 'stale' files.
    cleanSiteForRealz(repo, git, s, v)
    // Now copy files.
    IO.copy(betterMappings)
    if(noJekyll) IO.touch(repo / ".nojekyll")
    repo
  }
}
