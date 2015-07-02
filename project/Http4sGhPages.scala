import sbt._
import Keys._

import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.git.GitRunner

import Http4sBuild.apiVersion

// Copied from sbt-ghpages to avoid blowing away the old API
// https://github.com/sbt/sbt-ghpages/issues/10
object Http4sGhPages {
	def cleanSiteForRealz(dir: File, git: GitRunner, s: TaskStreams, apiVersion: (Int, Int)): Unit ={
		val toClean = IO.listFiles(dir).collect {
			case f if f.getName == "api" => new java.io.File(f, s"${apiVersion._1}.${apiVersion._2}")
			case f if f.getName != ".git" => f
		}.map(_.getAbsolutePath).toList
		if (!toClean.isEmpty)
			git(("rm" :: "-r" :: "-f" :: "--ignore-unmatch" :: toClean) :_*)(dir, s.log)
		()
	}

	def cleanSite0 = (updatedRepository, gitRunner, streams, Http4sBuild.apiVersion) map { (dir, git, s, v) =>
		cleanSiteForRealz(dir, git, s, v)
	}

	def synchLocal0 = (privateMappings, updatedRepository, ghpagesNoJekyll, gitRunner, streams, apiVersion) map {
    (mappings, repo, noJekyll, git, s, v) =>
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
