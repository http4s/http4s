---
type: common
weight: 100
title: Versions
---

<div>

  <!-- Nav tabs -->
  <ul class="nav nav-tabs" role="tablist">
    <li role="presentation" class="active"><a href="#compatibility" role="tab" data-toggle="tab">Compatibility</a></li>
    <li role="presentation"><a href="#changelog" role="tab" data-toggle="tab">Changelog</a></li>
  </ul>

  <!-- Tab panes -->
  <div class="tab-content">
    <div role="tabpanel" class="tab-pane active" id="compatibility">

<h2>Roadmap</h2>

<p>The last several versions of http4s have been built on scalaz-stream.  The library has served us well, but it is now deprecated in favor of fs2.  Meanwhile, our users and contributors increasingly desire first-class Cats support.  The http4s team has been maintaining two branches to serve existing users while looking forward to Cats and fs2.  This dual maintenance is taxing on the development team and was intended to be transitional rather than indefinite.</p>

After deliberations both internally and with <a href="https://github.com/http4s/fp-ecosystem">other projects in the Scala ecosystem</a>, we are bringing this transition to its conclusion:

<ul>
  <li><code>http4s-0.16</code> is the final release series based on <a href="https://github.com/scalaz/scalaz-stream">scalaz-stream</a>.  We will support this branch with bugfixes, but not new development.</li>
  <li><code>http4s-0.17</code> is the first official release on <a href="http://typelevel.org/cats/">Cats</a> and <a href="https://github.com/functional-streams-for-scala/fs2">fs2</a>.  Interop for Scalaz-based apps will be provided through <a href="https://github.com/djspiewak/shims">Shims</a> or <a href="https://github.com/shawjef3/Harmony">Harmony</a>.</li>
  <li>We intend to publish <code>http4s-1.0</code> on top of the eventual <code>cats-1.0</code> and <code>fs2-1.0</code></li>
</ul>

<h2>Matrix</h2>

	<ul>
	<li><span class="label label-primary">Stable</span> releases
	are recommended for production use and are maintained with
	backward, binary compatible bugfixes from the http4s
	team.</li>

	<li><span class="label label-warning">Development</span>
	releases are published as snapshots to Sonatype by Travis CI.
	API breakage may occur at any time.</li>

	<li><span class="label label-danger">Incomplete</span>
	releases are still missing features from the latest stable
	version.  We are working on feature parity, but publishing in
	current form for early adopters.</li>
		
	<li><span class="label label-default">EOL</span> releases are
	no longer actively maintained, but pull requests with a tale
	of woe may be considered.</li>
      </ul>

      <table class="table table-hover">
	<thead>
	  <tr>
	    <th>http4s</th>
	    <th class="text-center">Status</th>
	    <th class="text-center">Scala 2.10</th>
	    <th class="text-center">Scala 2.11</th>
	    <th class="text-center">Scala 2.12</th>
	    <th>FP</th>
	    <th>Streaming</th>
	    <th>JDK</th>
	  </tr>
	</thead>
	<tbody>
      <tr>
        <td>0.17.0-SNAPSHOT</td>
        <td class="text-center"><span class="label label-warning">Development</span></td>
        <td class="text-center"><i class="fa fa-ban"></i></td>
        <td class="text-center"><i class="fa fa-check"></i></td>
        <td class="text-center"><i class="fa fa-check"></i></td>
        <td>cats-0.9</td>
        <td>fs2-0.9</td>
        <td>1.8+</td>
      </tr>
	  <tr>
	    <td>0.16.0</td>
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.16.0</td>
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.15.16a</td>
	    <td class="text-center"><span class="label label-primary">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.15.16</td>
	    <td class="text-center"><span class="label label-primary">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.14.11a</td>
	    <td class="text-center"><span class="label label-primary">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.14.11</td>
	    <td class="text-center"><span class="label label-primary">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.13.3a</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.13.3</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.12.6</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.11.3</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.10.1</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.9.3</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.8.6</td>
	    <td class="text-center"><span class="label label-default">EOL</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.7a</td>
	    <td>1.7+</td>
	  </tr>
	</tbody>
      </table>
    </div>
    <div role="tabpanel" class="tab-pane" id="changelog">
      {{< readfile "content/CHANGELOG.md" >}} 
    </div>
  </div>
</div>

