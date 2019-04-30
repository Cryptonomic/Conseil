//add build information as an object in code
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit)
buildInfoPackage := "tech.cryptonomic.conseil"

//uses git tags to generate the project version
//see https://github.com/sbt/sbt-git#versioning-with-git
enablePlugins(GitVersioning)

/* The versioning scheme is
 *  - use a major number as the platform version
 *  - add a date reference in form of yyww (year + week in year)
 *  - use the latest git tag formatted as "ci-release-<xyz>" and take the numbers from xyz, increasing it
 * Compose the three separated by "dots" to have the version that will be released
 * The Git plugin will add a trailing "-SNAPSHOT" if there are locally uncommitted changes
 */
val majorVersion = 0

/* This allows to extract versions from past tags, not directly applied to
 * the current commit
 */
git.useGitDescribe := true

//defines how to extract the version from git tagging
git.gitTagToVersionNumber := { tag: String =>
  if(Versioning.releasePattern.findAllIn(tag).nonEmpty)
    Some(Versioning.generate(major = majorVersion, date = java.time.LocalDate.now, tag = tag))
  else
    None
}

//custom task to create a new release tag
lazy val prepareReleaseTag = taskKey[String]("Use the current version to define a git-tag for a new release")
prepareReleaseTag := Versioning.prepareReleaseTagDef.value

//read the command details for a description
lazy val gitTagCommand =
  Command.command(
    name = "gitTag",
    briefHelp = "will run the git tag command based on conseil versioning policy",
    detail =
    """ A command to call the "git tag" commands (from sbt-git) with custom args.
      | Allows any automated environment (e.g. jenkins, travis) to call
      | "sbt gitTag" when a new release has been just published, bumping the versioning tag,
      | ready for pushing to the git repo.
      | In turn, sbt will pick the newly-minted tag for the new version definition.
    """.stripMargin) {
      state =>
        val extracted = Project.extract(state)
        val (state2, tag) = extracted.runTask(prepareReleaseTag, state)
        //we might want to check out only for non-snapshots?
        println(s"About to tag the new release as '$tag'")
        //we might want to read the message from the env or from a local file
        val command = s"""git tag -a -m "release tagged using sbt gitTag" $tag"""
        Command.process(command, state2)
  }

ThisBuild / commands += gitTagCommand

useGpg := true

//sonatype publishing keys
sonatypeProfileName := "tech.cryptonomic"

organization := "tech.cryptonomic"
organizationName := "Cryptomonic"
organizationHomepage := Some(url("https://cryptonomic.tech/"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/Cryptonomic/Conseil"),
    "scm:git@github.com:Cryptonomic/Conseil.git"
  )
)
//should fill this up with whoever needs to appear there
developers := List(
  Developer(
    id = "Cryptonomic",
    name = "Cryptonomic Inc",
    email = "developers@cryptonomic.tech",
    url = url("https://cryptonomic.tech/")
  )
)

description := "Query API for the Tezos blockchain."
licenses := List("gpl-3.0" -> new URL("https://www.gnu.org/licenses/gpl-3.0.txt"))
homepage := Some(url("https://cryptonomic.tech/"))

// Remove all additional repository other than Maven Central from POM
pomIncludeRepository := { _ => false }
publishMavenStyle := true
publishTo := sonatypePublishTo.value
