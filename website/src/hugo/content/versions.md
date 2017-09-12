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
      <ul>
	<li><span class="label label-primary">Stable</span> releases
	are recommended for production use and are maintained with
	backward, binary compatible bugfixes from the http4s
	team.</li>

	<li><span class="label label-warning">Development</span>
	releases are published as snapshots to Sonatype by Travis CI.
	API breakage may occur at any time.</li>

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
        <td>0.16.0-cats-SNAPSHOT</td>
        <td class="text-center"><span class="label label-warning">Development</span></td>
        <td class="text-center"><i class="fa fa-ban"></i></td>
        <td class="text-center"><i class="fa fa-check"></i></td>
        <td class="text-center"><i class="fa fa-check"></i></td>
        <td>cats-0.9</td>
        <td>fs2-0.9</td>
        <td>1.8+</td>
      </tr>
	  <tr>
	    <td>0.16.0a-SNAPSHOT</td>
	    <td class="text-center"><span class="label label-warning">Development</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.16.0-SNAPSHOT</td>
	    <td class="text-center"><span class="label label-warning">Development</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.15.16a</td>
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td>scalaz-7.2</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	  <tr>
	    <td>0.15.16</td>
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
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
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.1</td>
	    <td>scalaz-stream-0.8a</td>
	    <td>1.8+</td>
	  </tr>
	</tbody>
	<tbody>
	  <tr>
	    <td>0.14.11</td>
	    <td class="text-center"><span class="label label-primary">Stable</span></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-check"></i></td>
	    <td class="text-center"><i class="fa fa-ban"></i></td>
	    <td>scalaz-7.2</td>
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

