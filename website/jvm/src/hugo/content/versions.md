---
menu: main
weight: 100
title: Versions
---

## Release lifecycle

* <span class="badge badge-warning">Milestone</span> releases are
  published for early adopters who need the latest dependencies or new
  features.  We will try to deprecate responsibly, but no binary
  compatibility is guaranteed.
* <span class="badge badge-success">Stable</span> releases are
  recommended for production use.  Backward binary compatibility is
  preserved across the minor version.  Patches will be released for
  bugs, or selectively for backports deemed low risk and high value.
* <span class="badge badge-secondary">EOL</span> releases are no
  longer supported by the http4s team.  Users will be advised to upgrade
  in the official support channels.  Patches may be released with
  a working pull request accompanied by a tale of woe.

## Which version is right for me?

* _I'll upgrade to Scala 3 before Cats-Effect 3:_ {{% latestInSeries "0.22" %}}
* _I'm ready for Cats-Effect 3:_ {{% latestInSeries "0.23" %}}
* _I'm new here, pick one:_ {{% latestInSeries "0.23" %}}
* _I live on the bleeding edge:_ {{% latestInSeries "1.0" %}}

##

<table class="table table-responsive table-hover">
  <thead>
	<tr>
	  <th>http4s</th>
	  <th class="text-center">Status</th>
	  <th class="text-center">Scala 2.10</th>
	  <th class="text-center">Scala 2.11</th>
	  <th class="text-center">Scala 2.12</th>
	  <th class="text-center">Scala 2.13</th>
	  <th class="text-center">Scala 3.0</th>
	  <th>Cats</th>
	  <th>FS2</th>
	  <th>JDK</th>
	</tr>
  </thead>
  <tbody>
	<tr>
	  <td><a href="/v1.0">{{% latestInSeries "1.0" %}}</a></td>
	  <td class="text-center"><span class="badge badge-warning">Milestone</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td>2.x</td>
	  <td>3.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.23">{{% latestInSeries "0.23" %}}</a></td>
	  <td class="text-center"><span class="badge badge-success">Stable</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td>2.x</td>
	  <td>3.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.22">{{% latestInSeries "0.22" %}}</a></td>
	  <td class="text-center"><span class="badge badge-success">Stable</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-gear"></i></td>
	  <td>2.x</td>
	  <td>2.x</td>
	  <td>1.8+</td>
	</tr>
    <tr>
	  <td><a href="/v0.21">{{% latestInSeries "0.21" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td>2.x</td>
	  <td>2.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.20">{{% latestInSeries "0.20" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td>1.x</td>
	  <td>1.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.19">{{% latestInSeries "0.19" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td>1.x</td>
	  <td>1.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.18">{{% latestInSeries "0.18" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td>1.x</td>
	  <td>0.10.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.17">{{% latestInSeries "0.17" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td>0.9.x</td>
	  <td>0.9.x</td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.16">{{% latestInSeries "0.16" %}}a</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.16">{{% latestInSeries "0.16" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.15">{{% latestInSeries "0.15" %}}a</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
	<tr>
	  <td><a href="/v0.15">{{% latestInSeries "0.15" %}}</a></td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.14" %}}a</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.14" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.13" %}}a</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.13" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.12" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.11" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.10" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.9" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.8+</td>
	</tr>
  </tbody>
  <tbody>
	<tr>
	  <td>{{% latestInSeries "0.8" %}}</td>
	  <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-check"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td class="text-center"><i class="fa fa-ban"></i></td>
	  <td></td>
	  <td></td>
	  <td>1.7+</td>
	</tr>
  </tbody>
</table>
