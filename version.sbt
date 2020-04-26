// Temporary hack to distinguish 0.21 snapshots from master
dynverGitDescribeOutput in ThisBuild ~= { _.map(_.copy(ref = sbtdynver.GitRef("1.0.0-M0"))) }
