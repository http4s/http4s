import sbt._
import Keys._

import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.git.GitRunner
import sbtunidoc.Plugin._

import Http4sKeys.apiVersion

import scala.util.Properties

object Http4sSite {
  lazy val settings = site.settings ++ ghpages.settings ++ site.jekyllSupport() ++ Seq(
    siteMappings <++= (mappings in (ScalaUnidoc, packageDoc), apiVersion) map {
      case (m, (major, minor)) =>
        for ((f, d) <- m) yield (f, s"api/$major.$minor/$d")
    },
    includeFilter in makeSite := "*" -- "*~",
    cleanSite <<= cleanSite0,
    synchLocal <<= synchLocal0,
    git.remoteRepo := remoteRepo
  )

  // Copied from site plugin to avoid blowing away the old API
	def cleanSiteForRealz(dir: File, git: GitRunner, s: TaskStreams): Unit ={
		val toClean = IO.listFiles(dir).collect {
			case f if f.getName == "api" => new java.io.File(f, "0.2")
			case f if f.getName != ".git" => f
		}.map(_.getAbsolutePath).toList
		if (!toClean.isEmpty)
			git(("rm" :: "-r" :: "-f" :: "--ignore-unmatch" :: toClean) :_*)(dir, s.log)
		()
	}

	def cleanSite0 = (updatedRepository, gitRunner, streams) map { (dir, git, s) =>
		cleanSiteForRealz(dir, git, s)
	}

	def synchLocal0 = (privateMappings, updatedRepository, ghpagesNoJekyll, gitRunner, streams) map { (mappings, repo, noJekyll, git, s) =>
		// TODO - an sbt.Synch with cache of previous mappings to make this more efficient. */
		val betterMappings = mappings map { case (file, target) => (file, repo / target) }
		// First, remove 'stale' files.
		cleanSiteForRealz(repo, git, s)
		// Now copy files.
		IO.copy(betterMappings)
		if(noJekyll) IO.touch(repo / ".nojekyll")
			repo
	}

  def remoteRepo = Properties.envOrNone("GH_TOKEN").fold("git@github.com:http4s/http4s.git"){ token =>
    s"https://${token}@github.com/http4s/http4s.git"
  }
}