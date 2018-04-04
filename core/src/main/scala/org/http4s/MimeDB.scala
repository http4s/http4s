package org.http4s

private[http4s] object MimeDB {
  lazy val all
    : List[MediaType] = Nil ::: message.all ::: font.all ::: chemical.all ::: x_conference.all ::: image.all ::: model.all ::: audio.all ::: text.all ::: application.all ::: multipart.all ::: x_shader.all ::: video.all
  val Compressible: Boolean = true
  val Uncompressible: Boolean = true
  val Binary: Boolean = true
  val NotBinary: Boolean = true
  object message {
    val mainType: String = "message"
    lazy val `message/cpim`: MediaType = new MediaType(mainType, "cpim", Compressible, NotBinary)
    lazy val `message/delivery-status`: MediaType =
      new MediaType(mainType, "delivery-status", Compressible, NotBinary)
    lazy val `message/disposition-notification`: MediaType = new MediaType(
      mainType,
      "disposition-notification",
      Compressible,
      NotBinary,
      List("disposition-notification"))
    lazy val `message/external-body`: MediaType =
      new MediaType(mainType, "external-body", Compressible, NotBinary)
    lazy val `message/feedback-report`: MediaType =
      new MediaType(mainType, "feedback-report", Compressible, NotBinary)
    lazy val `message/global`: MediaType =
      new MediaType(mainType, "global", Compressible, NotBinary, List("u8msg"))
    lazy val `message/global-delivery-status`: MediaType =
      new MediaType(mainType, "global-delivery-status", Compressible, NotBinary, List("u8dsn"))
    lazy val `message/global-disposition-notification`: MediaType = new MediaType(
      mainType,
      "global-disposition-notification",
      Compressible,
      NotBinary,
      List("u8mdn"))
    lazy val `message/global-headers`: MediaType =
      new MediaType(mainType, "global-headers", Compressible, NotBinary, List("u8hdr"))
    lazy val `message/http`: MediaType = new MediaType(mainType, "http", Uncompressible, NotBinary)
    lazy val `message/imdn+xml`: MediaType =
      new MediaType(mainType, "imdn+xml", Compressible, NotBinary)
    lazy val `message/news`: MediaType = new MediaType(mainType, "news", Compressible, NotBinary)
    lazy val `message/partial`: MediaType =
      new MediaType(mainType, "partial", Uncompressible, NotBinary)
    lazy val `message/rfc822`: MediaType =
      new MediaType(mainType, "rfc822", Compressible, NotBinary, List("eml", "mime"))
    lazy val `message/s-http`: MediaType =
      new MediaType(mainType, "s-http", Compressible, NotBinary)
    lazy val `message/sip`: MediaType = new MediaType(mainType, "sip", Compressible, NotBinary)
    lazy val `message/sipfrag`: MediaType =
      new MediaType(mainType, "sipfrag", Compressible, NotBinary)
    lazy val `message/tracking-status`: MediaType =
      new MediaType(mainType, "tracking-status", Compressible, NotBinary)
    lazy val `message/vnd.si.simp`: MediaType =
      new MediaType(mainType, "vnd.si.simp", Compressible, NotBinary)
    lazy val `message/vnd.wfa.wsc`: MediaType =
      new MediaType(mainType, "vnd.wfa.wsc", Compressible, NotBinary, List("wsc"))
    lazy val all: List[MediaType] = List(
      `message/cpim`,
      `message/delivery-status`,
      `message/disposition-notification`,
      `message/external-body`,
      `message/feedback-report`,
      `message/global`,
      `message/global-delivery-status`,
      `message/global-disposition-notification`,
      `message/global-headers`,
      `message/http`,
      `message/imdn+xml`,
      `message/news`,
      `message/partial`,
      `message/rfc822`,
      `message/s-http`,
      `message/sip`,
      `message/sipfrag`,
      `message/tracking-status`,
      `message/vnd.si.simp`,
      `message/vnd.wfa.wsc`
    )
  }
  object font {
    val mainType: String = "font"
    lazy val `font/collection`: MediaType =
      new MediaType(mainType, "collection", Compressible, NotBinary, List("ttc"))
    lazy val `font/otf`: MediaType =
      new MediaType(mainType, "otf", Compressible, NotBinary, List("otf"))
    lazy val `font/sfnt`: MediaType = new MediaType(mainType, "sfnt", Compressible, NotBinary)
    lazy val `font/ttf`: MediaType =
      new MediaType(mainType, "ttf", Compressible, NotBinary, List("ttf"))
    lazy val `font/woff`: MediaType =
      new MediaType(mainType, "woff", Compressible, NotBinary, List("woff"))
    lazy val `font/woff2`: MediaType =
      new MediaType(mainType, "woff2", Compressible, NotBinary, List("woff2"))
    lazy val all: List[MediaType] =
      List(`font/collection`, `font/otf`, `font/sfnt`, `font/ttf`, `font/woff`, `font/woff2`)
  }
  object chemical {
    val mainType: String = "chemical"
    lazy val `chemical/x-cdx`: MediaType =
      new MediaType(mainType, "x-cdx", Compressible, NotBinary, List("cdx"))
    lazy val `chemical/x-cif`: MediaType =
      new MediaType(mainType, "x-cif", Compressible, NotBinary, List("cif"))
    lazy val `chemical/x-cmdf`: MediaType =
      new MediaType(mainType, "x-cmdf", Compressible, NotBinary, List("cmdf"))
    lazy val `chemical/x-cml`: MediaType =
      new MediaType(mainType, "x-cml", Compressible, NotBinary, List("cml"))
    lazy val `chemical/x-csml`: MediaType =
      new MediaType(mainType, "x-csml", Compressible, NotBinary, List("csml"))
    lazy val `chemical/x-pdb`: MediaType = new MediaType(mainType, "x-pdb", Compressible, NotBinary)
    lazy val `chemical/x-xyz`: MediaType =
      new MediaType(mainType, "x-xyz", Compressible, NotBinary, List("xyz"))
    lazy val all: List[MediaType] = List(
      `chemical/x-cdx`,
      `chemical/x-cif`,
      `chemical/x-cmdf`,
      `chemical/x-cml`,
      `chemical/x-csml`,
      `chemical/x-pdb`,
      `chemical/x-xyz`)
  }
  object x_conference {
    val mainType: String = "x-conference"
    lazy val `x-conference/x-cooltalk`: MediaType =
      new MediaType(mainType, "x-cooltalk", Compressible, NotBinary, List("ice"))
    lazy val all: List[MediaType] = List(`x-conference/x-cooltalk`)
  }
  object image {
    val mainType: String = "image"
    lazy val `image/aces`: MediaType = new MediaType(mainType, "aces", Compressible, Binary)
    lazy val `image/apng`: MediaType =
      new MediaType(mainType, "apng", Uncompressible, Binary, List("apng"))
    lazy val `image/bmp`: MediaType =
      new MediaType(mainType, "bmp", Compressible, Binary, List("bmp"))
    lazy val `image/cgm`: MediaType =
      new MediaType(mainType, "cgm", Compressible, Binary, List("cgm"))
    lazy val `image/dicom-rle`: MediaType =
      new MediaType(mainType, "dicom-rle", Compressible, Binary)
    lazy val `image/emf`: MediaType = new MediaType(mainType, "emf", Compressible, Binary)
    lazy val `image/fits`: MediaType = new MediaType(mainType, "fits", Compressible, Binary)
    lazy val `image/g3fax`: MediaType =
      new MediaType(mainType, "g3fax", Compressible, Binary, List("g3"))
    lazy val `image/gif`: MediaType =
      new MediaType(mainType, "gif", Uncompressible, Binary, List("gif"))
    lazy val `image/ief`: MediaType =
      new MediaType(mainType, "ief", Compressible, Binary, List("ief"))
    lazy val `image/jls`: MediaType = new MediaType(mainType, "jls", Compressible, Binary)
    lazy val `image/jp2`: MediaType =
      new MediaType(mainType, "jp2", Uncompressible, Binary, List("jp2", "jpg2"))
    lazy val `image/jpeg`: MediaType =
      new MediaType(mainType, "jpeg", Uncompressible, Binary, List("jpeg", "jpg", "jpe"))
    lazy val `image/jpm`: MediaType =
      new MediaType(mainType, "jpm", Uncompressible, Binary, List("jpm"))
    lazy val `image/jpx`: MediaType =
      new MediaType(mainType, "jpx", Uncompressible, Binary, List("jpx", "jpf"))
    lazy val `image/ktx`: MediaType =
      new MediaType(mainType, "ktx", Compressible, Binary, List("ktx"))
    lazy val `image/naplps`: MediaType = new MediaType(mainType, "naplps", Compressible, Binary)
    lazy val `image/pjpeg`: MediaType = new MediaType(mainType, "pjpeg", Uncompressible, Binary)
    lazy val `image/png`: MediaType =
      new MediaType(mainType, "png", Uncompressible, Binary, List("png"))
    lazy val `image/prs.btif`: MediaType =
      new MediaType(mainType, "prs.btif", Compressible, Binary, List("btif"))
    lazy val `image/prs.pti`: MediaType = new MediaType(mainType, "prs.pti", Compressible, Binary)
    lazy val `image/pwg-raster`: MediaType =
      new MediaType(mainType, "pwg-raster", Compressible, Binary)
    lazy val `image/sgi`: MediaType =
      new MediaType(mainType, "sgi", Compressible, Binary, List("sgi"))
    lazy val `image/svg+xml`: MediaType =
      new MediaType(mainType, "svg+xml", Compressible, Binary, List("svg", "svgz"))
    lazy val `image/t38`: MediaType = new MediaType(mainType, "t38", Compressible, Binary)
    lazy val `image/tiff`: MediaType =
      new MediaType(mainType, "tiff", Uncompressible, Binary, List("tiff", "tif"))
    lazy val `image/tiff-fx`: MediaType = new MediaType(mainType, "tiff-fx", Compressible, Binary)
    lazy val `image/vnd.adobe.photoshop`: MediaType =
      new MediaType(mainType, "vnd.adobe.photoshop", Compressible, Binary, List("psd"))
    lazy val `image/vnd.airzip.accelerator.azv`: MediaType =
      new MediaType(mainType, "vnd.airzip.accelerator.azv", Compressible, Binary)
    lazy val `image/vnd.cns.inf2`: MediaType =
      new MediaType(mainType, "vnd.cns.inf2", Compressible, Binary)
    lazy val `image/vnd.dece.graphic`: MediaType = new MediaType(
      mainType,
      "vnd.dece.graphic",
      Compressible,
      Binary,
      List("uvi", "uvvi", "uvg", "uvvg"))
    lazy val `image/vnd.djvu`: MediaType =
      new MediaType(mainType, "vnd.djvu", Compressible, Binary, List("djvu", "djv"))
    lazy val `image/vnd.dvb.subtitle`: MediaType =
      new MediaType(mainType, "vnd.dvb.subtitle", Compressible, Binary, List("sub"))
    lazy val `image/vnd.dwg`: MediaType =
      new MediaType(mainType, "vnd.dwg", Compressible, Binary, List("dwg"))
    lazy val `image/vnd.dxf`: MediaType =
      new MediaType(mainType, "vnd.dxf", Compressible, Binary, List("dxf"))
    lazy val `image/vnd.fastbidsheet`: MediaType =
      new MediaType(mainType, "vnd.fastbidsheet", Compressible, Binary, List("fbs"))
    lazy val `image/vnd.fpx`: MediaType =
      new MediaType(mainType, "vnd.fpx", Compressible, Binary, List("fpx"))
    lazy val `image/vnd.fst`: MediaType =
      new MediaType(mainType, "vnd.fst", Compressible, Binary, List("fst"))
    lazy val `image/vnd.fujixerox.edmics-mmr`: MediaType =
      new MediaType(mainType, "vnd.fujixerox.edmics-mmr", Compressible, Binary, List("mmr"))
    lazy val `image/vnd.fujixerox.edmics-rlc`: MediaType =
      new MediaType(mainType, "vnd.fujixerox.edmics-rlc", Compressible, Binary, List("rlc"))
    lazy val `image/vnd.globalgraphics.pgb`: MediaType =
      new MediaType(mainType, "vnd.globalgraphics.pgb", Compressible, Binary)
    lazy val `image/vnd.microsoft.icon`: MediaType =
      new MediaType(mainType, "vnd.microsoft.icon", Compressible, Binary)
    lazy val `image/vnd.mix`: MediaType = new MediaType(mainType, "vnd.mix", Compressible, Binary)
    lazy val `image/vnd.mozilla.apng`: MediaType =
      new MediaType(mainType, "vnd.mozilla.apng", Compressible, Binary)
    lazy val `image/vnd.ms-modi`: MediaType =
      new MediaType(mainType, "vnd.ms-modi", Compressible, Binary, List("mdi"))
    lazy val `image/vnd.ms-photo`: MediaType =
      new MediaType(mainType, "vnd.ms-photo", Compressible, Binary, List("wdp"))
    lazy val `image/vnd.net-fpx`: MediaType =
      new MediaType(mainType, "vnd.net-fpx", Compressible, Binary, List("npx"))
    lazy val `image/vnd.radiance`: MediaType =
      new MediaType(mainType, "vnd.radiance", Compressible, Binary)
    lazy val `image/vnd.sealed.png`: MediaType =
      new MediaType(mainType, "vnd.sealed.png", Compressible, Binary)
    lazy val `image/vnd.sealedmedia.softseal.gif`: MediaType =
      new MediaType(mainType, "vnd.sealedmedia.softseal.gif", Compressible, Binary)
    lazy val `image/vnd.sealedmedia.softseal.jpg`: MediaType =
      new MediaType(mainType, "vnd.sealedmedia.softseal.jpg", Compressible, Binary)
    lazy val `image/vnd.svf`: MediaType = new MediaType(mainType, "vnd.svf", Compressible, Binary)
    lazy val `image/vnd.tencent.tap`: MediaType =
      new MediaType(mainType, "vnd.tencent.tap", Compressible, Binary)
    lazy val `image/vnd.valve.source.texture`: MediaType =
      new MediaType(mainType, "vnd.valve.source.texture", Compressible, Binary)
    lazy val `image/vnd.wap.wbmp`: MediaType =
      new MediaType(mainType, "vnd.wap.wbmp", Compressible, Binary, List("wbmp"))
    lazy val `image/vnd.xiff`: MediaType =
      new MediaType(mainType, "vnd.xiff", Compressible, Binary, List("xif"))
    lazy val `image/vnd.zbrush.pcx`: MediaType =
      new MediaType(mainType, "vnd.zbrush.pcx", Compressible, Binary)
    lazy val `image/webp`: MediaType =
      new MediaType(mainType, "webp", Compressible, Binary, List("webp"))
    lazy val `image/wmf`: MediaType = new MediaType(mainType, "wmf", Compressible, Binary)
    lazy val `image/x-3ds`: MediaType =
      new MediaType(mainType, "x-3ds", Compressible, Binary, List("3ds"))
    lazy val `image/x-cmu-raster`: MediaType =
      new MediaType(mainType, "x-cmu-raster", Compressible, Binary, List("ras"))
    lazy val `image/x-cmx`: MediaType =
      new MediaType(mainType, "x-cmx", Compressible, Binary, List("cmx"))
    lazy val `image/x-freehand`: MediaType = new MediaType(
      mainType,
      "x-freehand",
      Compressible,
      Binary,
      List("fh", "fhc", "fh4", "fh5", "fh7"))
    lazy val `image/x-icon`: MediaType =
      new MediaType(mainType, "x-icon", Compressible, Binary, List("ico"))
    lazy val `image/x-jng`: MediaType =
      new MediaType(mainType, "x-jng", Compressible, Binary, List("jng"))
    lazy val `image/x-mrsid-image`: MediaType =
      new MediaType(mainType, "x-mrsid-image", Compressible, Binary, List("sid"))
    lazy val `image/x-ms-bmp`: MediaType =
      new MediaType(mainType, "x-ms-bmp", Compressible, Binary, List("bmp"))
    lazy val `image/x-pcx`: MediaType =
      new MediaType(mainType, "x-pcx", Compressible, Binary, List("pcx"))
    lazy val `image/x-pict`: MediaType =
      new MediaType(mainType, "x-pict", Compressible, Binary, List("pic", "pct"))
    lazy val `image/x-portable-anymap`: MediaType =
      new MediaType(mainType, "x-portable-anymap", Compressible, Binary, List("pnm"))
    lazy val `image/x-portable-bitmap`: MediaType =
      new MediaType(mainType, "x-portable-bitmap", Compressible, Binary, List("pbm"))
    lazy val `image/x-portable-graymap`: MediaType =
      new MediaType(mainType, "x-portable-graymap", Compressible, Binary, List("pgm"))
    lazy val `image/x-portable-pixmap`: MediaType =
      new MediaType(mainType, "x-portable-pixmap", Compressible, Binary, List("ppm"))
    lazy val `image/x-rgb`: MediaType =
      new MediaType(mainType, "x-rgb", Compressible, Binary, List("rgb"))
    lazy val `image/x-tga`: MediaType =
      new MediaType(mainType, "x-tga", Compressible, Binary, List("tga"))
    lazy val `image/x-xbitmap`: MediaType =
      new MediaType(mainType, "x-xbitmap", Compressible, Binary, List("xbm"))
    lazy val `image/x-xcf`: MediaType = new MediaType(mainType, "x-xcf", Uncompressible, Binary)
    lazy val `image/x-xpixmap`: MediaType =
      new MediaType(mainType, "x-xpixmap", Compressible, Binary, List("xpm"))
    lazy val `image/x-xwindowdump`: MediaType =
      new MediaType(mainType, "x-xwindowdump", Compressible, Binary, List("xwd"))
    lazy val all: List[MediaType] = List(
      `image/aces`,
      `image/apng`,
      `image/bmp`,
      `image/cgm`,
      `image/dicom-rle`,
      `image/emf`,
      `image/fits`,
      `image/g3fax`,
      `image/gif`,
      `image/ief`,
      `image/jls`,
      `image/jp2`,
      `image/jpeg`,
      `image/jpm`,
      `image/jpx`,
      `image/ktx`,
      `image/naplps`,
      `image/pjpeg`,
      `image/png`,
      `image/prs.btif`,
      `image/prs.pti`,
      `image/pwg-raster`,
      `image/sgi`,
      `image/svg+xml`,
      `image/t38`,
      `image/tiff`,
      `image/tiff-fx`,
      `image/vnd.adobe.photoshop`,
      `image/vnd.airzip.accelerator.azv`,
      `image/vnd.cns.inf2`,
      `image/vnd.dece.graphic`,
      `image/vnd.djvu`,
      `image/vnd.dvb.subtitle`,
      `image/vnd.dwg`,
      `image/vnd.dxf`,
      `image/vnd.fastbidsheet`,
      `image/vnd.fpx`,
      `image/vnd.fst`,
      `image/vnd.fujixerox.edmics-mmr`,
      `image/vnd.fujixerox.edmics-rlc`,
      `image/vnd.globalgraphics.pgb`,
      `image/vnd.microsoft.icon`,
      `image/vnd.mix`,
      `image/vnd.mozilla.apng`,
      `image/vnd.ms-modi`,
      `image/vnd.ms-photo`,
      `image/vnd.net-fpx`,
      `image/vnd.radiance`,
      `image/vnd.sealed.png`,
      `image/vnd.sealedmedia.softseal.gif`,
      `image/vnd.sealedmedia.softseal.jpg`,
      `image/vnd.svf`,
      `image/vnd.tencent.tap`,
      `image/vnd.valve.source.texture`,
      `image/vnd.wap.wbmp`,
      `image/vnd.xiff`,
      `image/vnd.zbrush.pcx`,
      `image/webp`,
      `image/wmf`,
      `image/x-3ds`,
      `image/x-cmu-raster`,
      `image/x-cmx`,
      `image/x-freehand`,
      `image/x-icon`,
      `image/x-jng`,
      `image/x-mrsid-image`,
      `image/x-ms-bmp`,
      `image/x-pcx`,
      `image/x-pict`,
      `image/x-portable-anymap`,
      `image/x-portable-bitmap`,
      `image/x-portable-graymap`,
      `image/x-portable-pixmap`,
      `image/x-rgb`,
      `image/x-tga`,
      `image/x-xbitmap`,
      `image/x-xcf`,
      `image/x-xpixmap`,
      `image/x-xwindowdump`
    )
  }
  object model {
    val mainType: String = "model"
    lazy val `model/3mf`: MediaType = new MediaType(mainType, "3mf", Compressible, NotBinary)
    lazy val `model/gltf+json`: MediaType =
      new MediaType(mainType, "gltf+json", Compressible, NotBinary, List("gltf"))
    lazy val `model/gltf-binary`: MediaType =
      new MediaType(mainType, "gltf-binary", Compressible, NotBinary, List("glb"))
    lazy val `model/iges`: MediaType =
      new MediaType(mainType, "iges", Uncompressible, NotBinary, List("igs", "iges"))
    lazy val `model/mesh`: MediaType =
      new MediaType(mainType, "mesh", Uncompressible, NotBinary, List("msh", "mesh", "silo"))
    lazy val `model/stl`: MediaType = new MediaType(mainType, "stl", Compressible, NotBinary)
    lazy val `model/vnd.collada+xml`: MediaType =
      new MediaType(mainType, "vnd.collada+xml", Compressible, NotBinary, List("dae"))
    lazy val `model/vnd.dwf`: MediaType =
      new MediaType(mainType, "vnd.dwf", Compressible, NotBinary, List("dwf"))
    lazy val `model/vnd.flatland.3dml`: MediaType =
      new MediaType(mainType, "vnd.flatland.3dml", Compressible, NotBinary)
    lazy val `model/vnd.gdl`: MediaType =
      new MediaType(mainType, "vnd.gdl", Compressible, NotBinary, List("gdl"))
    lazy val `model/vnd.gs-gdl`: MediaType =
      new MediaType(mainType, "vnd.gs-gdl", Compressible, NotBinary)
    lazy val `model/vnd.gs.gdl`: MediaType =
      new MediaType(mainType, "vnd.gs.gdl", Compressible, NotBinary)
    lazy val `model/vnd.gtw`: MediaType =
      new MediaType(mainType, "vnd.gtw", Compressible, NotBinary, List("gtw"))
    lazy val `model/vnd.moml+xml`: MediaType =
      new MediaType(mainType, "vnd.moml+xml", Compressible, NotBinary)
    lazy val `model/vnd.mts`: MediaType =
      new MediaType(mainType, "vnd.mts", Compressible, NotBinary, List("mts"))
    lazy val `model/vnd.opengex`: MediaType =
      new MediaType(mainType, "vnd.opengex", Compressible, NotBinary)
    lazy val `model/vnd.parasolid.transmit.binary`: MediaType =
      new MediaType(mainType, "vnd.parasolid.transmit.binary", Compressible, NotBinary)
    lazy val `model/vnd.parasolid.transmit.text`: MediaType =
      new MediaType(mainType, "vnd.parasolid.transmit.text", Compressible, NotBinary)
    lazy val `model/vnd.rosette.annotated-data-model`: MediaType =
      new MediaType(mainType, "vnd.rosette.annotated-data-model", Compressible, NotBinary)
    lazy val `model/vnd.valve.source.compiled-map`: MediaType =
      new MediaType(mainType, "vnd.valve.source.compiled-map", Compressible, NotBinary)
    lazy val `model/vnd.vtu`: MediaType =
      new MediaType(mainType, "vnd.vtu", Compressible, NotBinary, List("vtu"))
    lazy val `model/vrml`: MediaType =
      new MediaType(mainType, "vrml", Uncompressible, NotBinary, List("wrl", "vrml"))
    lazy val `model/x3d+binary`: MediaType =
      new MediaType(mainType, "x3d+binary", Uncompressible, NotBinary, List("x3db", "x3dbz"))
    lazy val `model/x3d+fastinfoset`: MediaType =
      new MediaType(mainType, "x3d+fastinfoset", Compressible, NotBinary)
    lazy val `model/x3d+vrml`: MediaType =
      new MediaType(mainType, "x3d+vrml", Uncompressible, NotBinary, List("x3dv", "x3dvz"))
    lazy val `model/x3d+xml`: MediaType =
      new MediaType(mainType, "x3d+xml", Compressible, NotBinary, List("x3d", "x3dz"))
    lazy val `model/x3d-vrml`: MediaType =
      new MediaType(mainType, "x3d-vrml", Compressible, NotBinary)
    lazy val all: List[MediaType] = List(
      `model/3mf`,
      `model/gltf+json`,
      `model/gltf-binary`,
      `model/iges`,
      `model/mesh`,
      `model/stl`,
      `model/vnd.collada+xml`,
      `model/vnd.dwf`,
      `model/vnd.flatland.3dml`,
      `model/vnd.gdl`,
      `model/vnd.gs-gdl`,
      `model/vnd.gs.gdl`,
      `model/vnd.gtw`,
      `model/vnd.moml+xml`,
      `model/vnd.mts`,
      `model/vnd.opengex`,
      `model/vnd.parasolid.transmit.binary`,
      `model/vnd.parasolid.transmit.text`,
      `model/vnd.rosette.annotated-data-model`,
      `model/vnd.valve.source.compiled-map`,
      `model/vnd.vtu`,
      `model/vrml`,
      `model/x3d+binary`,
      `model/x3d+fastinfoset`,
      `model/x3d+vrml`,
      `model/x3d+xml`,
      `model/x3d-vrml`
    )
  }
  object audio {
    val mainType: String = "audio"
    lazy val `audio/1d-interleaved-parityfec`: MediaType =
      new MediaType(mainType, "1d-interleaved-parityfec", Compressible, Binary)
    lazy val `audio/32kadpcm`: MediaType = new MediaType(mainType, "32kadpcm", Compressible, Binary)
    lazy val `audio/3gpp`: MediaType =
      new MediaType(mainType, "3gpp", Uncompressible, Binary, List("3gpp"))
    lazy val `audio/3gpp2`: MediaType = new MediaType(mainType, "3gpp2", Compressible, Binary)
    lazy val `audio/ac3`: MediaType = new MediaType(mainType, "ac3", Compressible, Binary)
    lazy val `audio/adpcm`: MediaType =
      new MediaType(mainType, "adpcm", Compressible, Binary, List("adp"))
    lazy val `audio/amr`: MediaType = new MediaType(mainType, "amr", Compressible, Binary)
    lazy val `audio/amr-wb`: MediaType = new MediaType(mainType, "amr-wb", Compressible, Binary)
    lazy val `audio/amr-wb+` : MediaType = new MediaType(mainType, "amr-wb+", Compressible, Binary)
    lazy val `audio/aptx`: MediaType = new MediaType(mainType, "aptx", Compressible, Binary)
    lazy val `audio/asc`: MediaType = new MediaType(mainType, "asc", Compressible, Binary)
    lazy val `audio/atrac-advanced-lossless`: MediaType =
      new MediaType(mainType, "atrac-advanced-lossless", Compressible, Binary)
    lazy val `audio/atrac-x`: MediaType = new MediaType(mainType, "atrac-x", Compressible, Binary)
    lazy val `audio/atrac3`: MediaType = new MediaType(mainType, "atrac3", Compressible, Binary)
    lazy val `audio/basic`: MediaType =
      new MediaType(mainType, "basic", Uncompressible, Binary, List("au", "snd"))
    lazy val `audio/bv16`: MediaType = new MediaType(mainType, "bv16", Compressible, Binary)
    lazy val `audio/bv32`: MediaType = new MediaType(mainType, "bv32", Compressible, Binary)
    lazy val `audio/clearmode`: MediaType =
      new MediaType(mainType, "clearmode", Compressible, Binary)
    lazy val `audio/cn`: MediaType = new MediaType(mainType, "cn", Compressible, Binary)
    lazy val `audio/dat12`: MediaType = new MediaType(mainType, "dat12", Compressible, Binary)
    lazy val `audio/dls`: MediaType = new MediaType(mainType, "dls", Compressible, Binary)
    lazy val `audio/dsr-es201108`: MediaType =
      new MediaType(mainType, "dsr-es201108", Compressible, Binary)
    lazy val `audio/dsr-es202050`: MediaType =
      new MediaType(mainType, "dsr-es202050", Compressible, Binary)
    lazy val `audio/dsr-es202211`: MediaType =
      new MediaType(mainType, "dsr-es202211", Compressible, Binary)
    lazy val `audio/dsr-es202212`: MediaType =
      new MediaType(mainType, "dsr-es202212", Compressible, Binary)
    lazy val `audio/dv`: MediaType = new MediaType(mainType, "dv", Compressible, Binary)
    lazy val `audio/dvi4`: MediaType = new MediaType(mainType, "dvi4", Compressible, Binary)
    lazy val `audio/eac3`: MediaType = new MediaType(mainType, "eac3", Compressible, Binary)
    lazy val `audio/encaprtp`: MediaType = new MediaType(mainType, "encaprtp", Compressible, Binary)
    lazy val `audio/evrc`: MediaType = new MediaType(mainType, "evrc", Compressible, Binary)
    lazy val `audio/evrc-qcp`: MediaType = new MediaType(mainType, "evrc-qcp", Compressible, Binary)
    lazy val `audio/evrc0`: MediaType = new MediaType(mainType, "evrc0", Compressible, Binary)
    lazy val `audio/evrc1`: MediaType = new MediaType(mainType, "evrc1", Compressible, Binary)
    lazy val `audio/evrcb`: MediaType = new MediaType(mainType, "evrcb", Compressible, Binary)
    lazy val `audio/evrcb0`: MediaType = new MediaType(mainType, "evrcb0", Compressible, Binary)
    lazy val `audio/evrcb1`: MediaType = new MediaType(mainType, "evrcb1", Compressible, Binary)
    lazy val `audio/evrcnw`: MediaType = new MediaType(mainType, "evrcnw", Compressible, Binary)
    lazy val `audio/evrcnw0`: MediaType = new MediaType(mainType, "evrcnw0", Compressible, Binary)
    lazy val `audio/evrcnw1`: MediaType = new MediaType(mainType, "evrcnw1", Compressible, Binary)
    lazy val `audio/evrcwb`: MediaType = new MediaType(mainType, "evrcwb", Compressible, Binary)
    lazy val `audio/evrcwb0`: MediaType = new MediaType(mainType, "evrcwb0", Compressible, Binary)
    lazy val `audio/evrcwb1`: MediaType = new MediaType(mainType, "evrcwb1", Compressible, Binary)
    lazy val `audio/evs`: MediaType = new MediaType(mainType, "evs", Compressible, Binary)
    lazy val `audio/fwdred`: MediaType = new MediaType(mainType, "fwdred", Compressible, Binary)
    lazy val `audio/g711-0`: MediaType = new MediaType(mainType, "g711-0", Compressible, Binary)
    lazy val `audio/g719`: MediaType = new MediaType(mainType, "g719", Compressible, Binary)
    lazy val `audio/g722`: MediaType = new MediaType(mainType, "g722", Compressible, Binary)
    lazy val `audio/g7221`: MediaType = new MediaType(mainType, "g7221", Compressible, Binary)
    lazy val `audio/g723`: MediaType = new MediaType(mainType, "g723", Compressible, Binary)
    lazy val `audio/g726-16`: MediaType = new MediaType(mainType, "g726-16", Compressible, Binary)
    lazy val `audio/g726-24`: MediaType = new MediaType(mainType, "g726-24", Compressible, Binary)
    lazy val `audio/g726-32`: MediaType = new MediaType(mainType, "g726-32", Compressible, Binary)
    lazy val `audio/g726-40`: MediaType = new MediaType(mainType, "g726-40", Compressible, Binary)
    lazy val `audio/g728`: MediaType = new MediaType(mainType, "g728", Compressible, Binary)
    lazy val `audio/g729`: MediaType = new MediaType(mainType, "g729", Compressible, Binary)
    lazy val `audio/g7291`: MediaType = new MediaType(mainType, "g7291", Compressible, Binary)
    lazy val `audio/g729d`: MediaType = new MediaType(mainType, "g729d", Compressible, Binary)
    lazy val `audio/g729e`: MediaType = new MediaType(mainType, "g729e", Compressible, Binary)
    lazy val `audio/gsm`: MediaType = new MediaType(mainType, "gsm", Compressible, Binary)
    lazy val `audio/gsm-efr`: MediaType = new MediaType(mainType, "gsm-efr", Compressible, Binary)
    lazy val `audio/gsm-hr-08`: MediaType =
      new MediaType(mainType, "gsm-hr-08", Compressible, Binary)
    lazy val `audio/ilbc`: MediaType = new MediaType(mainType, "ilbc", Compressible, Binary)
    lazy val `audio/ip-mr_v2.5`: MediaType =
      new MediaType(mainType, "ip-mr_v2.5", Compressible, Binary)
    lazy val `audio/isac`: MediaType = new MediaType(mainType, "isac", Compressible, Binary)
    lazy val `audio/l16`: MediaType = new MediaType(mainType, "l16", Compressible, Binary)
    lazy val `audio/l20`: MediaType = new MediaType(mainType, "l20", Compressible, Binary)
    lazy val `audio/l24`: MediaType = new MediaType(mainType, "l24", Uncompressible, Binary)
    lazy val `audio/l8`: MediaType = new MediaType(mainType, "l8", Compressible, Binary)
    lazy val `audio/lpc`: MediaType = new MediaType(mainType, "lpc", Compressible, Binary)
    lazy val `audio/melp`: MediaType = new MediaType(mainType, "melp", Compressible, Binary)
    lazy val `audio/melp1200`: MediaType = new MediaType(mainType, "melp1200", Compressible, Binary)
    lazy val `audio/melp2400`: MediaType = new MediaType(mainType, "melp2400", Compressible, Binary)
    lazy val `audio/melp600`: MediaType = new MediaType(mainType, "melp600", Compressible, Binary)
    lazy val `audio/midi`: MediaType =
      new MediaType(mainType, "midi", Compressible, Binary, List("mid", "midi", "kar", "rmi"))
    lazy val `audio/mobile-xmf`: MediaType =
      new MediaType(mainType, "mobile-xmf", Compressible, Binary)
    lazy val `audio/mp3`: MediaType =
      new MediaType(mainType, "mp3", Uncompressible, Binary, List("mp3"))
    lazy val `audio/mp4`: MediaType =
      new MediaType(mainType, "mp4", Uncompressible, Binary, List("m4a", "mp4a"))
    lazy val `audio/mp4a-latm`: MediaType =
      new MediaType(mainType, "mp4a-latm", Compressible, Binary)
    lazy val `audio/mpa`: MediaType = new MediaType(mainType, "mpa", Compressible, Binary)
    lazy val `audio/mpa-robust`: MediaType =
      new MediaType(mainType, "mpa-robust", Compressible, Binary)
    lazy val `audio/mpeg`: MediaType = new MediaType(
      mainType,
      "mpeg",
      Uncompressible,
      Binary,
      List("mpga", "mp2", "mp2a", "mp3", "m2a", "m3a"))
    lazy val `audio/mpeg4-generic`: MediaType =
      new MediaType(mainType, "mpeg4-generic", Compressible, Binary)
    lazy val `audio/musepack`: MediaType = new MediaType(mainType, "musepack", Compressible, Binary)
    lazy val `audio/ogg`: MediaType =
      new MediaType(mainType, "ogg", Uncompressible, Binary, List("oga", "ogg", "spx"))
    lazy val `audio/opus`: MediaType = new MediaType(mainType, "opus", Compressible, Binary)
    lazy val `audio/parityfec`: MediaType =
      new MediaType(mainType, "parityfec", Compressible, Binary)
    lazy val `audio/pcma`: MediaType = new MediaType(mainType, "pcma", Compressible, Binary)
    lazy val `audio/pcma-wb`: MediaType = new MediaType(mainType, "pcma-wb", Compressible, Binary)
    lazy val `audio/pcmu`: MediaType = new MediaType(mainType, "pcmu", Compressible, Binary)
    lazy val `audio/pcmu-wb`: MediaType = new MediaType(mainType, "pcmu-wb", Compressible, Binary)
    lazy val `audio/prs.sid`: MediaType = new MediaType(mainType, "prs.sid", Compressible, Binary)
    lazy val `audio/qcelp`: MediaType = new MediaType(mainType, "qcelp", Compressible, Binary)
    lazy val `audio/raptorfec`: MediaType =
      new MediaType(mainType, "raptorfec", Compressible, Binary)
    lazy val `audio/red`: MediaType = new MediaType(mainType, "red", Compressible, Binary)
    lazy val `audio/rtp-enc-aescm128`: MediaType =
      new MediaType(mainType, "rtp-enc-aescm128", Compressible, Binary)
    lazy val `audio/rtp-midi`: MediaType = new MediaType(mainType, "rtp-midi", Compressible, Binary)
    lazy val `audio/rtploopback`: MediaType =
      new MediaType(mainType, "rtploopback", Compressible, Binary)
    lazy val `audio/rtx`: MediaType = new MediaType(mainType, "rtx", Compressible, Binary)
    lazy val `audio/s3m`: MediaType =
      new MediaType(mainType, "s3m", Compressible, Binary, List("s3m"))
    lazy val `audio/silk`: MediaType =
      new MediaType(mainType, "silk", Compressible, Binary, List("sil"))
    lazy val `audio/smv`: MediaType = new MediaType(mainType, "smv", Compressible, Binary)
    lazy val `audio/smv-qcp`: MediaType = new MediaType(mainType, "smv-qcp", Compressible, Binary)
    lazy val `audio/smv0`: MediaType = new MediaType(mainType, "smv0", Compressible, Binary)
    lazy val `audio/sp-midi`: MediaType = new MediaType(mainType, "sp-midi", Compressible, Binary)
    lazy val `audio/speex`: MediaType = new MediaType(mainType, "speex", Compressible, Binary)
    lazy val `audio/t140c`: MediaType = new MediaType(mainType, "t140c", Compressible, Binary)
    lazy val `audio/t38`: MediaType = new MediaType(mainType, "t38", Compressible, Binary)
    lazy val `audio/telephone-event`: MediaType =
      new MediaType(mainType, "telephone-event", Compressible, Binary)
    lazy val `audio/tone`: MediaType = new MediaType(mainType, "tone", Compressible, Binary)
    lazy val `audio/uemclip`: MediaType = new MediaType(mainType, "uemclip", Compressible, Binary)
    lazy val `audio/ulpfec`: MediaType = new MediaType(mainType, "ulpfec", Compressible, Binary)
    lazy val `audio/usac`: MediaType = new MediaType(mainType, "usac", Compressible, Binary)
    lazy val `audio/vdvi`: MediaType = new MediaType(mainType, "vdvi", Compressible, Binary)
    lazy val `audio/vmr-wb`: MediaType = new MediaType(mainType, "vmr-wb", Compressible, Binary)
    lazy val `audio/vnd.3gpp.iufp`: MediaType =
      new MediaType(mainType, "vnd.3gpp.iufp", Compressible, Binary)
    lazy val `audio/vnd.4sb`: MediaType = new MediaType(mainType, "vnd.4sb", Compressible, Binary)
    lazy val `audio/vnd.audiokoz`: MediaType =
      new MediaType(mainType, "vnd.audiokoz", Compressible, Binary)
    lazy val `audio/vnd.celp`: MediaType = new MediaType(mainType, "vnd.celp", Compressible, Binary)
    lazy val `audio/vnd.cisco.nse`: MediaType =
      new MediaType(mainType, "vnd.cisco.nse", Compressible, Binary)
    lazy val `audio/vnd.cmles.radio-events`: MediaType =
      new MediaType(mainType, "vnd.cmles.radio-events", Compressible, Binary)
    lazy val `audio/vnd.cns.anp1`: MediaType =
      new MediaType(mainType, "vnd.cns.anp1", Compressible, Binary)
    lazy val `audio/vnd.cns.inf1`: MediaType =
      new MediaType(mainType, "vnd.cns.inf1", Compressible, Binary)
    lazy val `audio/vnd.dece.audio`: MediaType =
      new MediaType(mainType, "vnd.dece.audio", Compressible, Binary, List("uva", "uvva"))
    lazy val `audio/vnd.digital-winds`: MediaType =
      new MediaType(mainType, "vnd.digital-winds", Compressible, Binary, List("eol"))
    lazy val `audio/vnd.dlna.adts`: MediaType =
      new MediaType(mainType, "vnd.dlna.adts", Compressible, Binary)
    lazy val `audio/vnd.dolby.heaac.1`: MediaType =
      new MediaType(mainType, "vnd.dolby.heaac.1", Compressible, Binary)
    lazy val `audio/vnd.dolby.heaac.2`: MediaType =
      new MediaType(mainType, "vnd.dolby.heaac.2", Compressible, Binary)
    lazy val `audio/vnd.dolby.mlp`: MediaType =
      new MediaType(mainType, "vnd.dolby.mlp", Compressible, Binary)
    lazy val `audio/vnd.dolby.mps`: MediaType =
      new MediaType(mainType, "vnd.dolby.mps", Compressible, Binary)
    lazy val `audio/vnd.dolby.pl2`: MediaType =
      new MediaType(mainType, "vnd.dolby.pl2", Compressible, Binary)
    lazy val `audio/vnd.dolby.pl2x`: MediaType =
      new MediaType(mainType, "vnd.dolby.pl2x", Compressible, Binary)
    lazy val `audio/vnd.dolby.pl2z`: MediaType =
      new MediaType(mainType, "vnd.dolby.pl2z", Compressible, Binary)
    lazy val `audio/vnd.dolby.pulse.1`: MediaType =
      new MediaType(mainType, "vnd.dolby.pulse.1", Compressible, Binary)
    lazy val `audio/vnd.dra`: MediaType =
      new MediaType(mainType, "vnd.dra", Compressible, Binary, List("dra"))
    lazy val `audio/vnd.dts`: MediaType =
      new MediaType(mainType, "vnd.dts", Compressible, Binary, List("dts"))
    lazy val `audio/vnd.dts.hd`: MediaType =
      new MediaType(mainType, "vnd.dts.hd", Compressible, Binary, List("dtshd"))
    lazy val `audio/vnd.dvb.file`: MediaType =
      new MediaType(mainType, "vnd.dvb.file", Compressible, Binary)
    lazy val `audio/vnd.everad.plj`: MediaType =
      new MediaType(mainType, "vnd.everad.plj", Compressible, Binary)
    lazy val `audio/vnd.hns.audio`: MediaType =
      new MediaType(mainType, "vnd.hns.audio", Compressible, Binary)
    lazy val `audio/vnd.lucent.voice`: MediaType =
      new MediaType(mainType, "vnd.lucent.voice", Compressible, Binary, List("lvp"))
    lazy val `audio/vnd.ms-playready.media.pya`: MediaType =
      new MediaType(mainType, "vnd.ms-playready.media.pya", Compressible, Binary, List("pya"))
    lazy val `audio/vnd.nokia.mobile-xmf`: MediaType =
      new MediaType(mainType, "vnd.nokia.mobile-xmf", Compressible, Binary)
    lazy val `audio/vnd.nortel.vbk`: MediaType =
      new MediaType(mainType, "vnd.nortel.vbk", Compressible, Binary)
    lazy val `audio/vnd.nuera.ecelp4800`: MediaType =
      new MediaType(mainType, "vnd.nuera.ecelp4800", Compressible, Binary, List("ecelp4800"))
    lazy val `audio/vnd.nuera.ecelp7470`: MediaType =
      new MediaType(mainType, "vnd.nuera.ecelp7470", Compressible, Binary, List("ecelp7470"))
    lazy val `audio/vnd.nuera.ecelp9600`: MediaType =
      new MediaType(mainType, "vnd.nuera.ecelp9600", Compressible, Binary, List("ecelp9600"))
    lazy val `audio/vnd.octel.sbc`: MediaType =
      new MediaType(mainType, "vnd.octel.sbc", Compressible, Binary)
    lazy val `audio/vnd.presonus.multitrack`: MediaType =
      new MediaType(mainType, "vnd.presonus.multitrack", Compressible, Binary)
    lazy val `audio/vnd.qcelp`: MediaType =
      new MediaType(mainType, "vnd.qcelp", Compressible, Binary)
    lazy val `audio/vnd.rhetorex.32kadpcm`: MediaType =
      new MediaType(mainType, "vnd.rhetorex.32kadpcm", Compressible, Binary)
    lazy val `audio/vnd.rip`: MediaType =
      new MediaType(mainType, "vnd.rip", Compressible, Binary, List("rip"))
    lazy val `audio/vnd.rn-realaudio`: MediaType =
      new MediaType(mainType, "vnd.rn-realaudio", Uncompressible, Binary)
    lazy val `audio/vnd.sealedmedia.softseal.mpeg`: MediaType =
      new MediaType(mainType, "vnd.sealedmedia.softseal.mpeg", Compressible, Binary)
    lazy val `audio/vnd.vmx.cvsd`: MediaType =
      new MediaType(mainType, "vnd.vmx.cvsd", Compressible, Binary)
    lazy val `audio/vnd.wave`: MediaType =
      new MediaType(mainType, "vnd.wave", Uncompressible, Binary)
    lazy val `audio/vorbis`: MediaType = new MediaType(mainType, "vorbis", Uncompressible, Binary)
    lazy val `audio/vorbis-config`: MediaType =
      new MediaType(mainType, "vorbis-config", Compressible, Binary)
    lazy val `audio/wav`: MediaType =
      new MediaType(mainType, "wav", Uncompressible, Binary, List("wav"))
    lazy val `audio/wave`: MediaType =
      new MediaType(mainType, "wave", Uncompressible, Binary, List("wav"))
    lazy val `audio/webm`: MediaType =
      new MediaType(mainType, "webm", Uncompressible, Binary, List("weba"))
    lazy val `audio/x-aac`: MediaType =
      new MediaType(mainType, "x-aac", Uncompressible, Binary, List("aac"))
    lazy val `audio/x-aiff`: MediaType =
      new MediaType(mainType, "x-aiff", Compressible, Binary, List("aif", "aiff", "aifc"))
    lazy val `audio/x-caf`: MediaType =
      new MediaType(mainType, "x-caf", Uncompressible, Binary, List("caf"))
    lazy val `audio/x-flac`: MediaType =
      new MediaType(mainType, "x-flac", Compressible, Binary, List("flac"))
    lazy val `audio/x-m4a`: MediaType =
      new MediaType(mainType, "x-m4a", Compressible, Binary, List("m4a"))
    lazy val `audio/x-matroska`: MediaType =
      new MediaType(mainType, "x-matroska", Compressible, Binary, List("mka"))
    lazy val `audio/x-mpegurl`: MediaType =
      new MediaType(mainType, "x-mpegurl", Compressible, Binary, List("m3u"))
    lazy val `audio/x-ms-wax`: MediaType =
      new MediaType(mainType, "x-ms-wax", Compressible, Binary, List("wax"))
    lazy val `audio/x-ms-wma`: MediaType =
      new MediaType(mainType, "x-ms-wma", Compressible, Binary, List("wma"))
    lazy val `audio/x-pn-realaudio`: MediaType =
      new MediaType(mainType, "x-pn-realaudio", Compressible, Binary, List("ram", "ra"))
    lazy val `audio/x-pn-realaudio-plugin`: MediaType =
      new MediaType(mainType, "x-pn-realaudio-plugin", Compressible, Binary, List("rmp"))
    lazy val `audio/x-realaudio`: MediaType =
      new MediaType(mainType, "x-realaudio", Compressible, Binary, List("ra"))
    lazy val `audio/x-tta`: MediaType = new MediaType(mainType, "x-tta", Compressible, Binary)
    lazy val `audio/x-wav`: MediaType =
      new MediaType(mainType, "x-wav", Compressible, Binary, List("wav"))
    lazy val `audio/xm`: MediaType = new MediaType(mainType, "xm", Compressible, Binary, List("xm"))
    lazy val all: List[MediaType] = List(
      `audio/1d-interleaved-parityfec`,
      `audio/32kadpcm`,
      `audio/3gpp`,
      `audio/3gpp2`,
      `audio/ac3`,
      `audio/adpcm`,
      `audio/amr`,
      `audio/amr-wb`,
      `audio/amr-wb+`,
      `audio/aptx`,
      `audio/asc`,
      `audio/atrac-advanced-lossless`,
      `audio/atrac-x`,
      `audio/atrac3`,
      `audio/basic`,
      `audio/bv16`,
      `audio/bv32`,
      `audio/clearmode`,
      `audio/cn`,
      `audio/dat12`,
      `audio/dls`,
      `audio/dsr-es201108`,
      `audio/dsr-es202050`,
      `audio/dsr-es202211`,
      `audio/dsr-es202212`,
      `audio/dv`,
      `audio/dvi4`,
      `audio/eac3`,
      `audio/encaprtp`,
      `audio/evrc`,
      `audio/evrc-qcp`,
      `audio/evrc0`,
      `audio/evrc1`,
      `audio/evrcb`,
      `audio/evrcb0`,
      `audio/evrcb1`,
      `audio/evrcnw`,
      `audio/evrcnw0`,
      `audio/evrcnw1`,
      `audio/evrcwb`,
      `audio/evrcwb0`,
      `audio/evrcwb1`,
      `audio/evs`,
      `audio/fwdred`,
      `audio/g711-0`,
      `audio/g719`,
      `audio/g722`,
      `audio/g7221`,
      `audio/g723`,
      `audio/g726-16`,
      `audio/g726-24`,
      `audio/g726-32`,
      `audio/g726-40`,
      `audio/g728`,
      `audio/g729`,
      `audio/g7291`,
      `audio/g729d`,
      `audio/g729e`,
      `audio/gsm`,
      `audio/gsm-efr`,
      `audio/gsm-hr-08`,
      `audio/ilbc`,
      `audio/ip-mr_v2.5`,
      `audio/isac`,
      `audio/l16`,
      `audio/l20`,
      `audio/l24`,
      `audio/l8`,
      `audio/lpc`,
      `audio/melp`,
      `audio/melp1200`,
      `audio/melp2400`,
      `audio/melp600`,
      `audio/midi`,
      `audio/mobile-xmf`,
      `audio/mp3`,
      `audio/mp4`,
      `audio/mp4a-latm`,
      `audio/mpa`,
      `audio/mpa-robust`,
      `audio/mpeg`,
      `audio/mpeg4-generic`,
      `audio/musepack`,
      `audio/ogg`,
      `audio/opus`,
      `audio/parityfec`,
      `audio/pcma`,
      `audio/pcma-wb`,
      `audio/pcmu`,
      `audio/pcmu-wb`,
      `audio/prs.sid`,
      `audio/qcelp`,
      `audio/raptorfec`,
      `audio/red`,
      `audio/rtp-enc-aescm128`,
      `audio/rtp-midi`,
      `audio/rtploopback`,
      `audio/rtx`,
      `audio/s3m`,
      `audio/silk`,
      `audio/smv`,
      `audio/smv-qcp`,
      `audio/smv0`,
      `audio/sp-midi`,
      `audio/speex`,
      `audio/t140c`,
      `audio/t38`,
      `audio/telephone-event`,
      `audio/tone`,
      `audio/uemclip`,
      `audio/ulpfec`,
      `audio/usac`,
      `audio/vdvi`,
      `audio/vmr-wb`,
      `audio/vnd.3gpp.iufp`,
      `audio/vnd.4sb`,
      `audio/vnd.audiokoz`,
      `audio/vnd.celp`,
      `audio/vnd.cisco.nse`,
      `audio/vnd.cmles.radio-events`,
      `audio/vnd.cns.anp1`,
      `audio/vnd.cns.inf1`,
      `audio/vnd.dece.audio`,
      `audio/vnd.digital-winds`,
      `audio/vnd.dlna.adts`,
      `audio/vnd.dolby.heaac.1`,
      `audio/vnd.dolby.heaac.2`,
      `audio/vnd.dolby.mlp`,
      `audio/vnd.dolby.mps`,
      `audio/vnd.dolby.pl2`,
      `audio/vnd.dolby.pl2x`,
      `audio/vnd.dolby.pl2z`,
      `audio/vnd.dolby.pulse.1`,
      `audio/vnd.dra`,
      `audio/vnd.dts`,
      `audio/vnd.dts.hd`,
      `audio/vnd.dvb.file`,
      `audio/vnd.everad.plj`,
      `audio/vnd.hns.audio`,
      `audio/vnd.lucent.voice`,
      `audio/vnd.ms-playready.media.pya`,
      `audio/vnd.nokia.mobile-xmf`,
      `audio/vnd.nortel.vbk`,
      `audio/vnd.nuera.ecelp4800`,
      `audio/vnd.nuera.ecelp7470`,
      `audio/vnd.nuera.ecelp9600`,
      `audio/vnd.octel.sbc`,
      `audio/vnd.presonus.multitrack`,
      `audio/vnd.qcelp`,
      `audio/vnd.rhetorex.32kadpcm`,
      `audio/vnd.rip`,
      `audio/vnd.rn-realaudio`,
      `audio/vnd.sealedmedia.softseal.mpeg`,
      `audio/vnd.vmx.cvsd`,
      `audio/vnd.wave`,
      `audio/vorbis`,
      `audio/vorbis-config`,
      `audio/wav`,
      `audio/wave`,
      `audio/webm`,
      `audio/x-aac`,
      `audio/x-aiff`,
      `audio/x-caf`,
      `audio/x-flac`,
      `audio/x-m4a`,
      `audio/x-matroska`,
      `audio/x-mpegurl`,
      `audio/x-ms-wax`,
      `audio/x-ms-wma`,
      `audio/x-pn-realaudio`,
      `audio/x-pn-realaudio-plugin`,
      `audio/x-realaudio`,
      `audio/x-tta`,
      `audio/x-wav`,
      `audio/xm`
    )
  }
  object text {
    val mainType: String = "text"
    lazy val `text/1d-interleaved-parityfec`: MediaType =
      new MediaType(mainType, "1d-interleaved-parityfec", Compressible, NotBinary)
    lazy val `text/cache-manifest`: MediaType = new MediaType(
      mainType,
      "cache-manifest",
      Compressible,
      NotBinary,
      List("appcache", "manifest"))
    lazy val `text/calendar`: MediaType =
      new MediaType(mainType, "calendar", Compressible, NotBinary, List("ics", "ifb"))
    lazy val `text/calender`: MediaType =
      new MediaType(mainType, "calender", Compressible, NotBinary)
    lazy val `text/cmd`: MediaType = new MediaType(mainType, "cmd", Compressible, NotBinary)
    lazy val `text/coffeescript`: MediaType =
      new MediaType(mainType, "coffeescript", Compressible, NotBinary, List("coffee", "litcoffee"))
    lazy val `text/css`: MediaType =
      new MediaType(mainType, "css", Compressible, NotBinary, List("css"))
    lazy val `text/csv`: MediaType =
      new MediaType(mainType, "csv", Compressible, NotBinary, List("csv"))
    lazy val `text/csv-schema`: MediaType =
      new MediaType(mainType, "csv-schema", Compressible, NotBinary)
    lazy val `text/directory`: MediaType =
      new MediaType(mainType, "directory", Compressible, NotBinary)
    lazy val `text/dns`: MediaType = new MediaType(mainType, "dns", Compressible, NotBinary)
    lazy val `text/ecmascript`: MediaType =
      new MediaType(mainType, "ecmascript", Compressible, NotBinary)
    lazy val `text/encaprtp`: MediaType =
      new MediaType(mainType, "encaprtp", Compressible, NotBinary)
    lazy val `text/enriched`: MediaType =
      new MediaType(mainType, "enriched", Compressible, NotBinary)
    lazy val `text/fwdred`: MediaType = new MediaType(mainType, "fwdred", Compressible, NotBinary)
    lazy val `text/grammar-ref-list`: MediaType =
      new MediaType(mainType, "grammar-ref-list", Compressible, NotBinary)
    lazy val `text/html`: MediaType =
      new MediaType(mainType, "html", Compressible, NotBinary, List("html", "htm", "shtml"))
    lazy val `text/jade`: MediaType =
      new MediaType(mainType, "jade", Compressible, NotBinary, List("jade"))
    lazy val `text/javascript`: MediaType =
      new MediaType(mainType, "javascript", Compressible, NotBinary)
    lazy val `text/jcr-cnd`: MediaType = new MediaType(mainType, "jcr-cnd", Compressible, NotBinary)
    lazy val `text/jsx`: MediaType =
      new MediaType(mainType, "jsx", Compressible, NotBinary, List("jsx"))
    lazy val `text/less`: MediaType =
      new MediaType(mainType, "less", Compressible, NotBinary, List("less"))
    lazy val `text/markdown`: MediaType =
      new MediaType(mainType, "markdown", Compressible, NotBinary, List("markdown", "md"))
    lazy val `text/mathml`: MediaType =
      new MediaType(mainType, "mathml", Compressible, NotBinary, List("mml"))
    lazy val `text/mizar`: MediaType = new MediaType(mainType, "mizar", Compressible, NotBinary)
    lazy val `text/n3`: MediaType =
      new MediaType(mainType, "n3", Compressible, NotBinary, List("n3"))
    lazy val `text/parameters`: MediaType =
      new MediaType(mainType, "parameters", Compressible, NotBinary)
    lazy val `text/parityfec`: MediaType =
      new MediaType(mainType, "parityfec", Compressible, NotBinary)
    lazy val `text/plain`: MediaType = new MediaType(
      mainType,
      "plain",
      Compressible,
      NotBinary,
      List("txt", "text", "conf", "def", "list", "log", "in", "ini"))
    lazy val `text/provenance-notation`: MediaType =
      new MediaType(mainType, "provenance-notation", Compressible, NotBinary)
    lazy val `text/prs.fallenstein.rst`: MediaType =
      new MediaType(mainType, "prs.fallenstein.rst", Compressible, NotBinary)
    lazy val `text/prs.lines.tag`: MediaType =
      new MediaType(mainType, "prs.lines.tag", Compressible, NotBinary, List("dsc"))
    lazy val `text/prs.prop.logic`: MediaType =
      new MediaType(mainType, "prs.prop.logic", Compressible, NotBinary)
    lazy val `text/raptorfec`: MediaType =
      new MediaType(mainType, "raptorfec", Compressible, NotBinary)
    lazy val `text/red`: MediaType = new MediaType(mainType, "red", Compressible, NotBinary)
    lazy val `text/rfc822-headers`: MediaType =
      new MediaType(mainType, "rfc822-headers", Compressible, NotBinary)
    lazy val `text/richtext`: MediaType =
      new MediaType(mainType, "richtext", Compressible, NotBinary, List("rtx"))
    lazy val `text/rtf`: MediaType =
      new MediaType(mainType, "rtf", Compressible, NotBinary, List("rtf"))
    lazy val `text/rtp-enc-aescm128`: MediaType =
      new MediaType(mainType, "rtp-enc-aescm128", Compressible, NotBinary)
    lazy val `text/rtploopback`: MediaType =
      new MediaType(mainType, "rtploopback", Compressible, NotBinary)
    lazy val `text/rtx`: MediaType = new MediaType(mainType, "rtx", Compressible, NotBinary)
    lazy val `text/sgml`: MediaType =
      new MediaType(mainType, "sgml", Compressible, NotBinary, List("sgml", "sgm"))
    lazy val `text/shex`: MediaType =
      new MediaType(mainType, "shex", Compressible, NotBinary, List("shex"))
    lazy val `text/slim`: MediaType =
      new MediaType(mainType, "slim", Compressible, NotBinary, List("slim", "slm"))
    lazy val `text/strings`: MediaType = new MediaType(mainType, "strings", Compressible, NotBinary)
    lazy val `text/stylus`: MediaType =
      new MediaType(mainType, "stylus", Compressible, NotBinary, List("stylus", "styl"))
    lazy val `text/t140`: MediaType = new MediaType(mainType, "t140", Compressible, NotBinary)
    lazy val `text/tab-separated-values`: MediaType =
      new MediaType(mainType, "tab-separated-values", Compressible, NotBinary, List("tsv"))
    lazy val `text/troff`: MediaType = new MediaType(
      mainType,
      "troff",
      Compressible,
      NotBinary,
      List("t", "tr", "roff", "man", "me", "ms"))
    lazy val `text/turtle`: MediaType =
      new MediaType(mainType, "turtle", Compressible, NotBinary, List("ttl"))
    lazy val `text/ulpfec`: MediaType = new MediaType(mainType, "ulpfec", Compressible, NotBinary)
    lazy val `text/uri-list`: MediaType =
      new MediaType(mainType, "uri-list", Compressible, NotBinary, List("uri", "uris", "urls"))
    lazy val `text/vcard`: MediaType =
      new MediaType(mainType, "vcard", Compressible, NotBinary, List("vcard"))
    lazy val `text/vnd.a`: MediaType = new MediaType(mainType, "vnd.a", Compressible, NotBinary)
    lazy val `text/vnd.abc`: MediaType = new MediaType(mainType, "vnd.abc", Compressible, NotBinary)
    lazy val `text/vnd.ascii-art`: MediaType =
      new MediaType(mainType, "vnd.ascii-art", Compressible, NotBinary)
    lazy val `text/vnd.curl`: MediaType =
      new MediaType(mainType, "vnd.curl", Compressible, NotBinary, List("curl"))
    lazy val `text/vnd.curl.dcurl`: MediaType =
      new MediaType(mainType, "vnd.curl.dcurl", Compressible, NotBinary, List("dcurl"))
    lazy val `text/vnd.curl.mcurl`: MediaType =
      new MediaType(mainType, "vnd.curl.mcurl", Compressible, NotBinary, List("mcurl"))
    lazy val `text/vnd.curl.scurl`: MediaType =
      new MediaType(mainType, "vnd.curl.scurl", Compressible, NotBinary, List("scurl"))
    lazy val `text/vnd.debian.copyright`: MediaType =
      new MediaType(mainType, "vnd.debian.copyright", Compressible, NotBinary)
    lazy val `text/vnd.dmclientscript`: MediaType =
      new MediaType(mainType, "vnd.dmclientscript", Compressible, NotBinary)
    lazy val `text/vnd.dvb.subtitle`: MediaType =
      new MediaType(mainType, "vnd.dvb.subtitle", Compressible, NotBinary, List("sub"))
    lazy val `text/vnd.esmertec.theme-descriptor`: MediaType =
      new MediaType(mainType, "vnd.esmertec.theme-descriptor", Compressible, NotBinary)
    lazy val `text/vnd.fly`: MediaType =
      new MediaType(mainType, "vnd.fly", Compressible, NotBinary, List("fly"))
    lazy val `text/vnd.fmi.flexstor`: MediaType =
      new MediaType(mainType, "vnd.fmi.flexstor", Compressible, NotBinary, List("flx"))
    lazy val `text/vnd.graphviz`: MediaType =
      new MediaType(mainType, "vnd.graphviz", Compressible, NotBinary, List("gv"))
    lazy val `text/vnd.in3d.3dml`: MediaType =
      new MediaType(mainType, "vnd.in3d.3dml", Compressible, NotBinary, List("3dml"))
    lazy val `text/vnd.in3d.spot`: MediaType =
      new MediaType(mainType, "vnd.in3d.spot", Compressible, NotBinary, List("spot"))
    lazy val `text/vnd.iptc.newsml`: MediaType =
      new MediaType(mainType, "vnd.iptc.newsml", Compressible, NotBinary)
    lazy val `text/vnd.iptc.nitf`: MediaType =
      new MediaType(mainType, "vnd.iptc.nitf", Compressible, NotBinary)
    lazy val `text/vnd.latex-z`: MediaType =
      new MediaType(mainType, "vnd.latex-z", Compressible, NotBinary)
    lazy val `text/vnd.motorola.reflex`: MediaType =
      new MediaType(mainType, "vnd.motorola.reflex", Compressible, NotBinary)
    lazy val `text/vnd.ms-mediapackage`: MediaType =
      new MediaType(mainType, "vnd.ms-mediapackage", Compressible, NotBinary)
    lazy val `text/vnd.net2phone.commcenter.command`: MediaType =
      new MediaType(mainType, "vnd.net2phone.commcenter.command", Compressible, NotBinary)
    lazy val `text/vnd.radisys.msml-basic-layout`: MediaType =
      new MediaType(mainType, "vnd.radisys.msml-basic-layout", Compressible, NotBinary)
    lazy val `text/vnd.si.uricatalogue`: MediaType =
      new MediaType(mainType, "vnd.si.uricatalogue", Compressible, NotBinary)
    lazy val `text/vnd.sun.j2me.app-descriptor`: MediaType =
      new MediaType(mainType, "vnd.sun.j2me.app-descriptor", Compressible, NotBinary, List("jad"))
    lazy val `text/vnd.trolltech.linguist`: MediaType =
      new MediaType(mainType, "vnd.trolltech.linguist", Compressible, NotBinary)
    lazy val `text/vnd.wap.si`: MediaType =
      new MediaType(mainType, "vnd.wap.si", Compressible, NotBinary)
    lazy val `text/vnd.wap.sl`: MediaType =
      new MediaType(mainType, "vnd.wap.sl", Compressible, NotBinary)
    lazy val `text/vnd.wap.wml`: MediaType =
      new MediaType(mainType, "vnd.wap.wml", Compressible, NotBinary, List("wml"))
    lazy val `text/vnd.wap.wmlscript`: MediaType =
      new MediaType(mainType, "vnd.wap.wmlscript", Compressible, NotBinary, List("wmls"))
    lazy val `text/vtt`: MediaType =
      new MediaType(mainType, "vtt", Compressible, NotBinary, List("vtt"))
    lazy val `text/x-asm`: MediaType =
      new MediaType(mainType, "x-asm", Compressible, NotBinary, List("s", "asm"))
    lazy val `text/x-c`: MediaType = new MediaType(
      mainType,
      "x-c",
      Compressible,
      NotBinary,
      List("c", "cc", "cxx", "cpp", "h", "hh", "dic"))
    lazy val `text/x-component`: MediaType =
      new MediaType(mainType, "x-component", Compressible, NotBinary, List("htc"))
    lazy val `text/x-fortran`: MediaType =
      new MediaType(mainType, "x-fortran", Compressible, NotBinary, List("f", "for", "f77", "f90"))
    lazy val `text/x-gwt-rpc`: MediaType =
      new MediaType(mainType, "x-gwt-rpc", Compressible, NotBinary)
    lazy val `text/x-handlebars-template`: MediaType =
      new MediaType(mainType, "x-handlebars-template", Compressible, NotBinary, List("hbs"))
    lazy val `text/x-java-source`: MediaType =
      new MediaType(mainType, "x-java-source", Compressible, NotBinary, List("java"))
    lazy val `text/x-jquery-tmpl`: MediaType =
      new MediaType(mainType, "x-jquery-tmpl", Compressible, NotBinary)
    lazy val `text/x-lua`: MediaType =
      new MediaType(mainType, "x-lua", Compressible, NotBinary, List("lua"))
    lazy val `text/x-markdown`: MediaType =
      new MediaType(mainType, "x-markdown", Compressible, NotBinary, List("mkd"))
    lazy val `text/x-nfo`: MediaType =
      new MediaType(mainType, "x-nfo", Compressible, NotBinary, List("nfo"))
    lazy val `text/x-opml`: MediaType =
      new MediaType(mainType, "x-opml", Compressible, NotBinary, List("opml"))
    lazy val `text/x-org`: MediaType =
      new MediaType(mainType, "x-org", Compressible, NotBinary, List("org"))
    lazy val `text/x-pascal`: MediaType =
      new MediaType(mainType, "x-pascal", Compressible, NotBinary, List("p", "pas"))
    lazy val `text/x-processing`: MediaType =
      new MediaType(mainType, "x-processing", Compressible, NotBinary, List("pde"))
    lazy val `text/x-sass`: MediaType =
      new MediaType(mainType, "x-sass", Compressible, NotBinary, List("sass"))
    lazy val `text/x-scss`: MediaType =
      new MediaType(mainType, "x-scss", Compressible, NotBinary, List("scss"))
    lazy val `text/x-setext`: MediaType =
      new MediaType(mainType, "x-setext", Compressible, NotBinary, List("etx"))
    lazy val `text/x-sfv`: MediaType =
      new MediaType(mainType, "x-sfv", Compressible, NotBinary, List("sfv"))
    lazy val `text/x-suse-ymp`: MediaType =
      new MediaType(mainType, "x-suse-ymp", Compressible, NotBinary, List("ymp"))
    lazy val `text/x-uuencode`: MediaType =
      new MediaType(mainType, "x-uuencode", Compressible, NotBinary, List("uu"))
    lazy val `text/x-vcalendar`: MediaType =
      new MediaType(mainType, "x-vcalendar", Compressible, NotBinary, List("vcs"))
    lazy val `text/x-vcard`: MediaType =
      new MediaType(mainType, "x-vcard", Compressible, NotBinary, List("vcf"))
    lazy val `text/xml`: MediaType =
      new MediaType(mainType, "xml", Compressible, NotBinary, List("xml"))
    lazy val `text/xml-external-parsed-entity`: MediaType =
      new MediaType(mainType, "xml-external-parsed-entity", Compressible, NotBinary)
    lazy val `text/yaml`: MediaType =
      new MediaType(mainType, "yaml", Compressible, NotBinary, List("yaml", "yml"))
    lazy val all: List[MediaType] = List(
      `text/1d-interleaved-parityfec`,
      `text/cache-manifest`,
      `text/calendar`,
      `text/calender`,
      `text/cmd`,
      `text/coffeescript`,
      `text/css`,
      `text/csv`,
      `text/csv-schema`,
      `text/directory`,
      `text/dns`,
      `text/ecmascript`,
      `text/encaprtp`,
      `text/enriched`,
      `text/fwdred`,
      `text/grammar-ref-list`,
      `text/html`,
      `text/jade`,
      `text/javascript`,
      `text/jcr-cnd`,
      `text/jsx`,
      `text/less`,
      `text/markdown`,
      `text/mathml`,
      `text/mizar`,
      `text/n3`,
      `text/parameters`,
      `text/parityfec`,
      `text/plain`,
      `text/provenance-notation`,
      `text/prs.fallenstein.rst`,
      `text/prs.lines.tag`,
      `text/prs.prop.logic`,
      `text/raptorfec`,
      `text/red`,
      `text/rfc822-headers`,
      `text/richtext`,
      `text/rtf`,
      `text/rtp-enc-aescm128`,
      `text/rtploopback`,
      `text/rtx`,
      `text/sgml`,
      `text/shex`,
      `text/slim`,
      `text/strings`,
      `text/stylus`,
      `text/t140`,
      `text/tab-separated-values`,
      `text/troff`,
      `text/turtle`,
      `text/ulpfec`,
      `text/uri-list`,
      `text/vcard`,
      `text/vnd.a`,
      `text/vnd.abc`,
      `text/vnd.ascii-art`,
      `text/vnd.curl`,
      `text/vnd.curl.dcurl`,
      `text/vnd.curl.mcurl`,
      `text/vnd.curl.scurl`,
      `text/vnd.debian.copyright`,
      `text/vnd.dmclientscript`,
      `text/vnd.dvb.subtitle`,
      `text/vnd.esmertec.theme-descriptor`,
      `text/vnd.fly`,
      `text/vnd.fmi.flexstor`,
      `text/vnd.graphviz`,
      `text/vnd.in3d.3dml`,
      `text/vnd.in3d.spot`,
      `text/vnd.iptc.newsml`,
      `text/vnd.iptc.nitf`,
      `text/vnd.latex-z`,
      `text/vnd.motorola.reflex`,
      `text/vnd.ms-mediapackage`,
      `text/vnd.net2phone.commcenter.command`,
      `text/vnd.radisys.msml-basic-layout`,
      `text/vnd.si.uricatalogue`,
      `text/vnd.sun.j2me.app-descriptor`,
      `text/vnd.trolltech.linguist`,
      `text/vnd.wap.si`,
      `text/vnd.wap.sl`,
      `text/vnd.wap.wml`,
      `text/vnd.wap.wmlscript`,
      `text/vtt`,
      `text/x-asm`,
      `text/x-c`,
      `text/x-component`,
      `text/x-fortran`,
      `text/x-gwt-rpc`,
      `text/x-handlebars-template`,
      `text/x-java-source`,
      `text/x-jquery-tmpl`,
      `text/x-lua`,
      `text/x-markdown`,
      `text/x-nfo`,
      `text/x-opml`,
      `text/x-org`,
      `text/x-pascal`,
      `text/x-processing`,
      `text/x-sass`,
      `text/x-scss`,
      `text/x-setext`,
      `text/x-sfv`,
      `text/x-suse-ymp`,
      `text/x-uuencode`,
      `text/x-vcalendar`,
      `text/x-vcard`,
      `text/xml`,
      `text/xml-external-parsed-entity`,
      `text/yaml`
    )
  }
  object application {
    lazy val all
      : List[MediaType] = Nil ::: application.all ::: application_1.all ::: application_2.all
    object application {
      val mainType: String = "application"
      lazy val `application/1d-interleaved-parityfec`: MediaType =
        new MediaType(mainType, "1d-interleaved-parityfec", Compressible, NotBinary)
      lazy val `application/3gpdash-qoe-report+xml`: MediaType =
        new MediaType(mainType, "3gpdash-qoe-report+xml", Compressible, NotBinary)
      lazy val `application/3gpp-ims+xml`: MediaType =
        new MediaType(mainType, "3gpp-ims+xml", Compressible, NotBinary)
      lazy val `application/a2l`: MediaType =
        new MediaType(mainType, "a2l", Compressible, NotBinary)
      lazy val `application/activemessage`: MediaType =
        new MediaType(mainType, "activemessage", Compressible, NotBinary)
      lazy val `application/activity+json`: MediaType =
        new MediaType(mainType, "activity+json", Compressible, NotBinary)
      lazy val `application/alto-costmap+json`: MediaType =
        new MediaType(mainType, "alto-costmap+json", Compressible, NotBinary)
      lazy val `application/alto-costmapfilter+json`: MediaType =
        new MediaType(mainType, "alto-costmapfilter+json", Compressible, NotBinary)
      lazy val `application/alto-directory+json`: MediaType =
        new MediaType(mainType, "alto-directory+json", Compressible, NotBinary)
      lazy val `application/alto-endpointcost+json`: MediaType =
        new MediaType(mainType, "alto-endpointcost+json", Compressible, NotBinary)
      lazy val `application/alto-endpointcostparams+json`: MediaType =
        new MediaType(mainType, "alto-endpointcostparams+json", Compressible, NotBinary)
      lazy val `application/alto-endpointprop+json`: MediaType =
        new MediaType(mainType, "alto-endpointprop+json", Compressible, NotBinary)
      lazy val `application/alto-endpointpropparams+json`: MediaType =
        new MediaType(mainType, "alto-endpointpropparams+json", Compressible, NotBinary)
      lazy val `application/alto-error+json`: MediaType =
        new MediaType(mainType, "alto-error+json", Compressible, NotBinary)
      lazy val `application/alto-networkmap+json`: MediaType =
        new MediaType(mainType, "alto-networkmap+json", Compressible, NotBinary)
      lazy val `application/alto-networkmapfilter+json`: MediaType =
        new MediaType(mainType, "alto-networkmapfilter+json", Compressible, NotBinary)
      lazy val `application/aml`: MediaType =
        new MediaType(mainType, "aml", Compressible, NotBinary)
      lazy val `application/andrew-inset`: MediaType =
        new MediaType(mainType, "andrew-inset", Compressible, NotBinary, List("ez"))
      lazy val `application/applefile`: MediaType =
        new MediaType(mainType, "applefile", Compressible, NotBinary)
      lazy val `application/applixware`: MediaType =
        new MediaType(mainType, "applixware", Compressible, NotBinary, List("aw"))
      lazy val `application/atf`: MediaType =
        new MediaType(mainType, "atf", Compressible, NotBinary)
      lazy val `application/atfx`: MediaType =
        new MediaType(mainType, "atfx", Compressible, NotBinary)
      lazy val `application/atom+xml`: MediaType =
        new MediaType(mainType, "atom+xml", Compressible, NotBinary, List("atom"))
      lazy val `application/atomcat+xml`: MediaType =
        new MediaType(mainType, "atomcat+xml", Compressible, NotBinary, List("atomcat"))
      lazy val `application/atomdeleted+xml`: MediaType =
        new MediaType(mainType, "atomdeleted+xml", Compressible, NotBinary)
      lazy val `application/atomicmail`: MediaType =
        new MediaType(mainType, "atomicmail", Compressible, NotBinary)
      lazy val `application/atomsvc+xml`: MediaType =
        new MediaType(mainType, "atomsvc+xml", Compressible, NotBinary, List("atomsvc"))
      lazy val `application/atxml`: MediaType =
        new MediaType(mainType, "atxml", Compressible, NotBinary)
      lazy val `application/auth-policy+xml`: MediaType =
        new MediaType(mainType, "auth-policy+xml", Compressible, NotBinary)
      lazy val `application/bacnet-xdd+zip`: MediaType =
        new MediaType(mainType, "bacnet-xdd+zip", Compressible, NotBinary)
      lazy val `application/batch-smtp`: MediaType =
        new MediaType(mainType, "batch-smtp", Compressible, NotBinary)
      lazy val `application/bdoc`: MediaType =
        new MediaType(mainType, "bdoc", Uncompressible, NotBinary, List("bdoc"))
      lazy val `application/beep+xml`: MediaType =
        new MediaType(mainType, "beep+xml", Compressible, NotBinary)
      lazy val `application/calendar+json`: MediaType =
        new MediaType(mainType, "calendar+json", Compressible, NotBinary)
      lazy val `application/calendar+xml`: MediaType =
        new MediaType(mainType, "calendar+xml", Compressible, NotBinary)
      lazy val `application/call-completion`: MediaType =
        new MediaType(mainType, "call-completion", Compressible, NotBinary)
      lazy val `application/cals-1840`: MediaType =
        new MediaType(mainType, "cals-1840", Compressible, NotBinary)
      lazy val `application/cbor`: MediaType =
        new MediaType(mainType, "cbor", Compressible, NotBinary)
      lazy val `application/cccex`: MediaType =
        new MediaType(mainType, "cccex", Compressible, NotBinary)
      lazy val `application/ccmp+xml`: MediaType =
        new MediaType(mainType, "ccmp+xml", Compressible, NotBinary)
      lazy val `application/ccxml+xml`: MediaType =
        new MediaType(mainType, "ccxml+xml", Compressible, NotBinary, List("ccxml"))
      lazy val `application/cdfx+xml`: MediaType =
        new MediaType(mainType, "cdfx+xml", Compressible, NotBinary)
      lazy val `application/cdmi-capability`: MediaType =
        new MediaType(mainType, "cdmi-capability", Compressible, NotBinary, List("cdmia"))
      lazy val `application/cdmi-container`: MediaType =
        new MediaType(mainType, "cdmi-container", Compressible, NotBinary, List("cdmic"))
      lazy val `application/cdmi-domain`: MediaType =
        new MediaType(mainType, "cdmi-domain", Compressible, NotBinary, List("cdmid"))
      lazy val `application/cdmi-object`: MediaType =
        new MediaType(mainType, "cdmi-object", Compressible, NotBinary, List("cdmio"))
      lazy val `application/cdmi-queue`: MediaType =
        new MediaType(mainType, "cdmi-queue", Compressible, NotBinary, List("cdmiq"))
      lazy val `application/cdni`: MediaType =
        new MediaType(mainType, "cdni", Compressible, NotBinary)
      lazy val `application/cea`: MediaType =
        new MediaType(mainType, "cea", Compressible, NotBinary)
      lazy val `application/cea-2018+xml`: MediaType =
        new MediaType(mainType, "cea-2018+xml", Compressible, NotBinary)
      lazy val `application/cellml+xml`: MediaType =
        new MediaType(mainType, "cellml+xml", Compressible, NotBinary)
      lazy val `application/cfw`: MediaType =
        new MediaType(mainType, "cfw", Compressible, NotBinary)
      lazy val `application/clue_info+xml`: MediaType =
        new MediaType(mainType, "clue_info+xml", Compressible, NotBinary)
      lazy val `application/cms`: MediaType =
        new MediaType(mainType, "cms", Compressible, NotBinary)
      lazy val `application/cnrp+xml`: MediaType =
        new MediaType(mainType, "cnrp+xml", Compressible, NotBinary)
      lazy val `application/coap-group+json`: MediaType =
        new MediaType(mainType, "coap-group+json", Compressible, NotBinary)
      lazy val `application/coap-payload`: MediaType =
        new MediaType(mainType, "coap-payload", Compressible, NotBinary)
      lazy val `application/commonground`: MediaType =
        new MediaType(mainType, "commonground", Compressible, NotBinary)
      lazy val `application/conference-info+xml`: MediaType =
        new MediaType(mainType, "conference-info+xml", Compressible, NotBinary)
      lazy val `application/cose`: MediaType =
        new MediaType(mainType, "cose", Compressible, NotBinary)
      lazy val `application/cose-key`: MediaType =
        new MediaType(mainType, "cose-key", Compressible, NotBinary)
      lazy val `application/cose-key-set`: MediaType =
        new MediaType(mainType, "cose-key-set", Compressible, NotBinary)
      lazy val `application/cpl+xml`: MediaType =
        new MediaType(mainType, "cpl+xml", Compressible, NotBinary)
      lazy val `application/csrattrs`: MediaType =
        new MediaType(mainType, "csrattrs", Compressible, NotBinary)
      lazy val `application/csta+xml`: MediaType =
        new MediaType(mainType, "csta+xml", Compressible, NotBinary)
      lazy val `application/cstadata+xml`: MediaType =
        new MediaType(mainType, "cstadata+xml", Compressible, NotBinary)
      lazy val `application/csvm+json`: MediaType =
        new MediaType(mainType, "csvm+json", Compressible, NotBinary)
      lazy val `application/cu-seeme`: MediaType =
        new MediaType(mainType, "cu-seeme", Compressible, NotBinary, List("cu"))
      lazy val `application/cwt`: MediaType =
        new MediaType(mainType, "cwt", Compressible, NotBinary)
      lazy val `application/cybercash`: MediaType =
        new MediaType(mainType, "cybercash", Compressible, NotBinary)
      lazy val `application/dart`: MediaType =
        new MediaType(mainType, "dart", Compressible, NotBinary)
      lazy val `application/dash+xml`: MediaType =
        new MediaType(mainType, "dash+xml", Compressible, NotBinary, List("mpd"))
      lazy val `application/dashdelta`: MediaType =
        new MediaType(mainType, "dashdelta", Compressible, NotBinary)
      lazy val `application/davmount+xml`: MediaType =
        new MediaType(mainType, "davmount+xml", Compressible, NotBinary, List("davmount"))
      lazy val `application/dca-rft`: MediaType =
        new MediaType(mainType, "dca-rft", Compressible, NotBinary)
      lazy val `application/dcd`: MediaType =
        new MediaType(mainType, "dcd", Compressible, NotBinary)
      lazy val `application/dec-dx`: MediaType =
        new MediaType(mainType, "dec-dx", Compressible, NotBinary)
      lazy val `application/dialog-info+xml`: MediaType =
        new MediaType(mainType, "dialog-info+xml", Compressible, NotBinary)
      lazy val `application/dicom`: MediaType =
        new MediaType(mainType, "dicom", Compressible, NotBinary)
      lazy val `application/dicom+json`: MediaType =
        new MediaType(mainType, "dicom+json", Compressible, NotBinary)
      lazy val `application/dicom+xml`: MediaType =
        new MediaType(mainType, "dicom+xml", Compressible, NotBinary)
      lazy val `application/dii`: MediaType =
        new MediaType(mainType, "dii", Compressible, NotBinary)
      lazy val `application/dit`: MediaType =
        new MediaType(mainType, "dit", Compressible, NotBinary)
      lazy val `application/dns`: MediaType =
        new MediaType(mainType, "dns", Compressible, NotBinary)
      lazy val `application/docbook+xml`: MediaType =
        new MediaType(mainType, "docbook+xml", Compressible, NotBinary, List("dbk"))
      lazy val `application/dskpp+xml`: MediaType =
        new MediaType(mainType, "dskpp+xml", Compressible, NotBinary)
      lazy val `application/dssc+der`: MediaType =
        new MediaType(mainType, "dssc+der", Compressible, NotBinary, List("dssc"))
      lazy val `application/dssc+xml`: MediaType =
        new MediaType(mainType, "dssc+xml", Compressible, NotBinary, List("xdssc"))
      lazy val `application/dvcs`: MediaType =
        new MediaType(mainType, "dvcs", Compressible, NotBinary)
      lazy val `application/ecmascript`: MediaType =
        new MediaType(mainType, "ecmascript", Compressible, NotBinary, List("ecma", "es"))
      lazy val `application/edi-consent`: MediaType =
        new MediaType(mainType, "edi-consent", Compressible, NotBinary)
      lazy val `application/edi-x12`: MediaType =
        new MediaType(mainType, "edi-x12", Uncompressible, NotBinary)
      lazy val `application/edifact`: MediaType =
        new MediaType(mainType, "edifact", Uncompressible, NotBinary)
      lazy val `application/efi`: MediaType =
        new MediaType(mainType, "efi", Compressible, NotBinary)
      lazy val `application/emergencycalldata.comment+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.comment+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.control+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.control+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.deviceinfo+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.deviceinfo+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.ecall.msd`: MediaType =
        new MediaType(mainType, "emergencycalldata.ecall.msd", Compressible, NotBinary)
      lazy val `application/emergencycalldata.providerinfo+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.providerinfo+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.serviceinfo+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.serviceinfo+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.subscriberinfo+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.subscriberinfo+xml", Compressible, NotBinary)
      lazy val `application/emergencycalldata.veds+xml`: MediaType =
        new MediaType(mainType, "emergencycalldata.veds+xml", Compressible, NotBinary)
      lazy val `application/emma+xml`: MediaType =
        new MediaType(mainType, "emma+xml", Compressible, NotBinary, List("emma"))
      lazy val `application/emotionml+xml`: MediaType =
        new MediaType(mainType, "emotionml+xml", Compressible, NotBinary)
      lazy val `application/encaprtp`: MediaType =
        new MediaType(mainType, "encaprtp", Compressible, NotBinary)
      lazy val `application/epp+xml`: MediaType =
        new MediaType(mainType, "epp+xml", Compressible, NotBinary)
      lazy val `application/epub+zip`: MediaType =
        new MediaType(mainType, "epub+zip", Compressible, NotBinary, List("epub"))
      lazy val `application/eshop`: MediaType =
        new MediaType(mainType, "eshop", Compressible, NotBinary)
      lazy val `application/exi`: MediaType =
        new MediaType(mainType, "exi", Compressible, NotBinary, List("exi"))
      lazy val `application/fastinfoset`: MediaType =
        new MediaType(mainType, "fastinfoset", Compressible, NotBinary)
      lazy val `application/fastsoap`: MediaType =
        new MediaType(mainType, "fastsoap", Compressible, NotBinary)
      lazy val `application/fdt+xml`: MediaType =
        new MediaType(mainType, "fdt+xml", Compressible, NotBinary)
      lazy val `application/fhir+json`: MediaType =
        new MediaType(mainType, "fhir+json", Compressible, NotBinary)
      lazy val `application/fhir+xml`: MediaType =
        new MediaType(mainType, "fhir+xml", Compressible, NotBinary)
      lazy val `application/fido.trusted-apps+json`: MediaType =
        new MediaType(mainType, "fido.trusted-apps+json", Compressible, NotBinary)
      lazy val `application/fits`: MediaType =
        new MediaType(mainType, "fits", Compressible, NotBinary)
      lazy val `application/font-sfnt`: MediaType =
        new MediaType(mainType, "font-sfnt", Compressible, NotBinary)
      lazy val `application/font-tdpfr`: MediaType =
        new MediaType(mainType, "font-tdpfr", Compressible, NotBinary, List("pfr"))
      lazy val `application/font-woff`: MediaType =
        new MediaType(mainType, "font-woff", Uncompressible, Binary, List("woff"))
      lazy val `application/framework-attributes+xml`: MediaType =
        new MediaType(mainType, "framework-attributes+xml", Compressible, NotBinary)
      lazy val `application/geo+json`: MediaType =
        new MediaType(mainType, "geo+json", Compressible, NotBinary, List("geojson"))
      lazy val `application/geo+json-seq`: MediaType =
        new MediaType(mainType, "geo+json-seq", Compressible, NotBinary)
      lazy val `application/geoxacml+xml`: MediaType =
        new MediaType(mainType, "geoxacml+xml", Compressible, NotBinary)
      lazy val `application/gml+xml`: MediaType =
        new MediaType(mainType, "gml+xml", Compressible, NotBinary, List("gml"))
      lazy val `application/gpx+xml`: MediaType =
        new MediaType(mainType, "gpx+xml", Compressible, NotBinary, List("gpx"))
      lazy val `application/gxf`: MediaType =
        new MediaType(mainType, "gxf", Compressible, NotBinary, List("gxf"))
      lazy val `application/gzip`: MediaType =
        new MediaType(mainType, "gzip", Uncompressible, Binary, List("gz"))
      lazy val `application/h224`: MediaType =
        new MediaType(mainType, "h224", Compressible, NotBinary)
      lazy val `application/held+xml`: MediaType =
        new MediaType(mainType, "held+xml", Compressible, NotBinary)
      lazy val `application/hjson`: MediaType =
        new MediaType(mainType, "hjson", Compressible, NotBinary, List("hjson"))
      lazy val `application/http`: MediaType =
        new MediaType(mainType, "http", Compressible, NotBinary)
      lazy val `application/hyperstudio`: MediaType =
        new MediaType(mainType, "hyperstudio", Compressible, NotBinary, List("stk"))
      lazy val `application/ibe-key-request+xml`: MediaType =
        new MediaType(mainType, "ibe-key-request+xml", Compressible, NotBinary)
      lazy val `application/ibe-pkg-reply+xml`: MediaType =
        new MediaType(mainType, "ibe-pkg-reply+xml", Compressible, NotBinary)
      lazy val `application/ibe-pp-data`: MediaType =
        new MediaType(mainType, "ibe-pp-data", Compressible, NotBinary)
      lazy val `application/iges`: MediaType =
        new MediaType(mainType, "iges", Compressible, NotBinary)
      lazy val `application/im-iscomposing+xml`: MediaType =
        new MediaType(mainType, "im-iscomposing+xml", Compressible, NotBinary)
      lazy val `application/index`: MediaType =
        new MediaType(mainType, "index", Compressible, NotBinary)
      lazy val `application/index.cmd`: MediaType =
        new MediaType(mainType, "index.cmd", Compressible, NotBinary)
      lazy val `application/index.obj`: MediaType =
        new MediaType(mainType, "index.obj", Compressible, NotBinary)
      lazy val `application/index.response`: MediaType =
        new MediaType(mainType, "index.response", Compressible, NotBinary)
      lazy val `application/index.vnd`: MediaType =
        new MediaType(mainType, "index.vnd", Compressible, NotBinary)
      lazy val `application/inkml+xml`: MediaType =
        new MediaType(mainType, "inkml+xml", Compressible, NotBinary, List("ink", "inkml"))
      lazy val `application/iotp`: MediaType =
        new MediaType(mainType, "iotp", Compressible, NotBinary)
      lazy val `application/ipfix`: MediaType =
        new MediaType(mainType, "ipfix", Compressible, NotBinary, List("ipfix"))
      lazy val `application/ipp`: MediaType =
        new MediaType(mainType, "ipp", Compressible, NotBinary)
      lazy val `application/isup`: MediaType =
        new MediaType(mainType, "isup", Compressible, NotBinary)
      lazy val `application/its+xml`: MediaType =
        new MediaType(mainType, "its+xml", Compressible, NotBinary)
      lazy val `application/java-archive`: MediaType =
        new MediaType(mainType, "java-archive", Uncompressible, Binary, List("jar", "war", "ear"))
      lazy val `application/java-serialized-object`: MediaType =
        new MediaType(mainType, "java-serialized-object", Uncompressible, NotBinary, List("ser"))
      lazy val `application/java-vm`: MediaType =
        new MediaType(mainType, "java-vm", Uncompressible, NotBinary, List("class"))
      lazy val `application/javascript`: MediaType =
        new MediaType(mainType, "javascript", Compressible, NotBinary, List("js", "mjs"))
      lazy val `application/jf2feed+json`: MediaType =
        new MediaType(mainType, "jf2feed+json", Compressible, NotBinary)
      lazy val `application/jose`: MediaType =
        new MediaType(mainType, "jose", Compressible, NotBinary)
      lazy val `application/jose+json`: MediaType =
        new MediaType(mainType, "jose+json", Compressible, NotBinary)
      lazy val `application/jrd+json`: MediaType =
        new MediaType(mainType, "jrd+json", Compressible, NotBinary)
      lazy val `application/json`: MediaType =
        new MediaType(mainType, "json", Compressible, Binary, List("json", "map"))
      lazy val `application/json-patch+json`: MediaType =
        new MediaType(mainType, "json-patch+json", Compressible, NotBinary)
      lazy val `application/json-seq`: MediaType =
        new MediaType(mainType, "json-seq", Compressible, NotBinary)
      lazy val `application/json5`: MediaType =
        new MediaType(mainType, "json5", Compressible, NotBinary, List("json5"))
      lazy val `application/jsonml+json`: MediaType =
        new MediaType(mainType, "jsonml+json", Compressible, NotBinary, List("jsonml"))
      lazy val `application/jwk+json`: MediaType =
        new MediaType(mainType, "jwk+json", Compressible, NotBinary)
      lazy val `application/jwk-set+json`: MediaType =
        new MediaType(mainType, "jwk-set+json", Compressible, NotBinary)
      lazy val `application/jwt`: MediaType =
        new MediaType(mainType, "jwt", Compressible, NotBinary)
      lazy val `application/kpml-request+xml`: MediaType =
        new MediaType(mainType, "kpml-request+xml", Compressible, NotBinary)
      lazy val `application/kpml-response+xml`: MediaType =
        new MediaType(mainType, "kpml-response+xml", Compressible, NotBinary)
      lazy val `application/ld+json`: MediaType =
        new MediaType(mainType, "ld+json", Compressible, NotBinary, List("jsonld"))
      lazy val `application/lgr+xml`: MediaType =
        new MediaType(mainType, "lgr+xml", Compressible, NotBinary)
      lazy val `application/link-format`: MediaType =
        new MediaType(mainType, "link-format", Compressible, NotBinary)
      lazy val `application/load-control+xml`: MediaType =
        new MediaType(mainType, "load-control+xml", Compressible, NotBinary)
      lazy val `application/lost+xml`: MediaType =
        new MediaType(mainType, "lost+xml", Compressible, NotBinary, List("lostxml"))
      lazy val `application/lostsync+xml`: MediaType =
        new MediaType(mainType, "lostsync+xml", Compressible, NotBinary)
      lazy val `application/lxf`: MediaType =
        new MediaType(mainType, "lxf", Compressible, NotBinary)
      lazy val `application/mac-binhex40`: MediaType =
        new MediaType(mainType, "mac-binhex40", Compressible, NotBinary, List("hqx"))
      lazy val `application/mac-compactpro`: MediaType =
        new MediaType(mainType, "mac-compactpro", Compressible, NotBinary, List("cpt"))
      lazy val `application/macwriteii`: MediaType =
        new MediaType(mainType, "macwriteii", Compressible, NotBinary)
      lazy val `application/mads+xml`: MediaType =
        new MediaType(mainType, "mads+xml", Compressible, NotBinary, List("mads"))
      lazy val `application/manifest+json`: MediaType =
        new MediaType(mainType, "manifest+json", Compressible, NotBinary, List("webmanifest"))
      lazy val `application/marc`: MediaType =
        new MediaType(mainType, "marc", Compressible, NotBinary, List("mrc"))
      lazy val `application/marcxml+xml`: MediaType =
        new MediaType(mainType, "marcxml+xml", Compressible, NotBinary, List("mrcx"))
      lazy val `application/mathematica`: MediaType =
        new MediaType(mainType, "mathematica", Compressible, NotBinary, List("ma", "nb", "mb"))
      lazy val `application/mathml+xml`: MediaType =
        new MediaType(mainType, "mathml+xml", Compressible, NotBinary, List("mathml"))
      lazy val `application/mathml-content+xml`: MediaType =
        new MediaType(mainType, "mathml-content+xml", Compressible, NotBinary)
      lazy val `application/mathml-presentation+xml`: MediaType =
        new MediaType(mainType, "mathml-presentation+xml", Compressible, NotBinary)
      lazy val `application/mbms-associated-procedure-description+xml`: MediaType = new MediaType(
        mainType,
        "mbms-associated-procedure-description+xml",
        Compressible,
        NotBinary)
      lazy val `application/mbms-deregister+xml`: MediaType =
        new MediaType(mainType, "mbms-deregister+xml", Compressible, NotBinary)
      lazy val `application/mbms-envelope+xml`: MediaType =
        new MediaType(mainType, "mbms-envelope+xml", Compressible, NotBinary)
      lazy val `application/mbms-msk+xml`: MediaType =
        new MediaType(mainType, "mbms-msk+xml", Compressible, NotBinary)
      lazy val `application/mbms-msk-response+xml`: MediaType =
        new MediaType(mainType, "mbms-msk-response+xml", Compressible, NotBinary)
      lazy val `application/mbms-protection-description+xml`: MediaType =
        new MediaType(mainType, "mbms-protection-description+xml", Compressible, NotBinary)
      lazy val `application/mbms-reception-report+xml`: MediaType =
        new MediaType(mainType, "mbms-reception-report+xml", Compressible, NotBinary)
      lazy val `application/mbms-register+xml`: MediaType =
        new MediaType(mainType, "mbms-register+xml", Compressible, NotBinary)
      lazy val `application/mbms-register-response+xml`: MediaType =
        new MediaType(mainType, "mbms-register-response+xml", Compressible, NotBinary)
      lazy val `application/mbms-schedule+xml`: MediaType =
        new MediaType(mainType, "mbms-schedule+xml", Compressible, NotBinary)
      lazy val `application/mbms-user-service-description+xml`: MediaType =
        new MediaType(mainType, "mbms-user-service-description+xml", Compressible, NotBinary)
      lazy val `application/mbox`: MediaType =
        new MediaType(mainType, "mbox", Compressible, NotBinary, List("mbox"))
      lazy val `application/media-policy-dataset+xml`: MediaType =
        new MediaType(mainType, "media-policy-dataset+xml", Compressible, NotBinary)
      lazy val `application/media_control+xml`: MediaType =
        new MediaType(mainType, "media_control+xml", Compressible, NotBinary)
      lazy val `application/mediaservercontrol+xml`: MediaType =
        new MediaType(mainType, "mediaservercontrol+xml", Compressible, NotBinary, List("mscml"))
      lazy val `application/merge-patch+json`: MediaType =
        new MediaType(mainType, "merge-patch+json", Compressible, NotBinary)
      lazy val `application/metalink+xml`: MediaType =
        new MediaType(mainType, "metalink+xml", Compressible, NotBinary, List("metalink"))
      lazy val `application/metalink4+xml`: MediaType =
        new MediaType(mainType, "metalink4+xml", Compressible, NotBinary, List("meta4"))
      lazy val `application/mets+xml`: MediaType =
        new MediaType(mainType, "mets+xml", Compressible, NotBinary, List("mets"))
      lazy val `application/mf4`: MediaType =
        new MediaType(mainType, "mf4", Compressible, NotBinary)
      lazy val `application/mikey`: MediaType =
        new MediaType(mainType, "mikey", Compressible, NotBinary)
      lazy val `application/mmt-usd+xml`: MediaType =
        new MediaType(mainType, "mmt-usd+xml", Compressible, NotBinary)
      lazy val `application/mods+xml`: MediaType =
        new MediaType(mainType, "mods+xml", Compressible, NotBinary, List("mods"))
      lazy val `application/moss-keys`: MediaType =
        new MediaType(mainType, "moss-keys", Compressible, NotBinary)
      lazy val `application/moss-signature`: MediaType =
        new MediaType(mainType, "moss-signature", Compressible, NotBinary)
      lazy val `application/mosskey-data`: MediaType =
        new MediaType(mainType, "mosskey-data", Compressible, NotBinary)
      lazy val `application/mosskey-request`: MediaType =
        new MediaType(mainType, "mosskey-request", Compressible, NotBinary)
      lazy val `application/mp21`: MediaType =
        new MediaType(mainType, "mp21", Compressible, NotBinary, List("m21", "mp21"))
      lazy val `application/mp4`: MediaType =
        new MediaType(mainType, "mp4", Compressible, NotBinary, List("mp4s", "m4p"))
      lazy val `application/mpeg4-generic`: MediaType =
        new MediaType(mainType, "mpeg4-generic", Compressible, NotBinary)
      lazy val `application/mpeg4-iod`: MediaType =
        new MediaType(mainType, "mpeg4-iod", Compressible, NotBinary)
      lazy val `application/mpeg4-iod-xmt`: MediaType =
        new MediaType(mainType, "mpeg4-iod-xmt", Compressible, NotBinary)
      lazy val `application/mrb-consumer+xml`: MediaType =
        new MediaType(mainType, "mrb-consumer+xml", Compressible, NotBinary)
      lazy val `application/mrb-publish+xml`: MediaType =
        new MediaType(mainType, "mrb-publish+xml", Compressible, NotBinary)
      lazy val `application/msc-ivr+xml`: MediaType =
        new MediaType(mainType, "msc-ivr+xml", Compressible, NotBinary)
      lazy val `application/msc-mixer+xml`: MediaType =
        new MediaType(mainType, "msc-mixer+xml", Compressible, NotBinary)
      lazy val `application/msword`: MediaType =
        new MediaType(mainType, "msword", Uncompressible, Binary, List("doc", "dot"))
      lazy val `application/mud+json`: MediaType =
        new MediaType(mainType, "mud+json", Compressible, NotBinary)
      lazy val `application/mxf`: MediaType =
        new MediaType(mainType, "mxf", Compressible, NotBinary, List("mxf"))
      lazy val `application/n-quads`: MediaType =
        new MediaType(mainType, "n-quads", Compressible, NotBinary)
      lazy val `application/n-triples`: MediaType =
        new MediaType(mainType, "n-triples", Compressible, NotBinary)
      lazy val `application/nasdata`: MediaType =
        new MediaType(mainType, "nasdata", Compressible, NotBinary)
      lazy val `application/news-checkgroups`: MediaType =
        new MediaType(mainType, "news-checkgroups", Compressible, NotBinary)
      lazy val `application/news-groupinfo`: MediaType =
        new MediaType(mainType, "news-groupinfo", Compressible, NotBinary)
      lazy val `application/news-transmission`: MediaType =
        new MediaType(mainType, "news-transmission", Compressible, NotBinary)
      lazy val `application/nlsml+xml`: MediaType =
        new MediaType(mainType, "nlsml+xml", Compressible, NotBinary)
      lazy val `application/node`: MediaType =
        new MediaType(mainType, "node", Compressible, NotBinary)
      lazy val `application/nss`: MediaType =
        new MediaType(mainType, "nss", Compressible, NotBinary)
      lazy val `application/ocsp-request`: MediaType =
        new MediaType(mainType, "ocsp-request", Compressible, NotBinary)
      lazy val `application/ocsp-response`: MediaType =
        new MediaType(mainType, "ocsp-response", Compressible, NotBinary)
      lazy val `application/octet-stream`: MediaType = new MediaType(
        mainType,
        "octet-stream",
        Uncompressible,
        Binary,
        List(
          "bin",
          "dms",
          "lrf",
          "mar",
          "so",
          "dist",
          "distz",
          "pkg",
          "bpk",
          "dump",
          "elc",
          "deploy",
          "exe",
          "dll",
          "deb",
          "dmg",
          "iso",
          "img",
          "msi",
          "msp",
          "msm",
          "buffer")
      )
      lazy val `application/oda`: MediaType =
        new MediaType(mainType, "oda", Compressible, NotBinary, List("oda"))
      lazy val `application/odx`: MediaType =
        new MediaType(mainType, "odx", Compressible, NotBinary)
      lazy val `application/oebps-package+xml`: MediaType =
        new MediaType(mainType, "oebps-package+xml", Compressible, NotBinary, List("opf"))
      lazy val `application/ogg`: MediaType =
        new MediaType(mainType, "ogg", Uncompressible, NotBinary, List("ogx"))
      lazy val `application/omdoc+xml`: MediaType =
        new MediaType(mainType, "omdoc+xml", Compressible, NotBinary, List("omdoc"))
      lazy val `application/onenote`: MediaType = new MediaType(
        mainType,
        "onenote",
        Compressible,
        NotBinary,
        List("onetoc", "onetoc2", "onetmp", "onepkg"))
      lazy val `application/oxps`: MediaType =
        new MediaType(mainType, "oxps", Compressible, NotBinary, List("oxps"))
      lazy val `application/p2p-overlay+xml`: MediaType =
        new MediaType(mainType, "p2p-overlay+xml", Compressible, NotBinary)
      lazy val `application/parityfec`: MediaType =
        new MediaType(mainType, "parityfec", Compressible, NotBinary)
      lazy val `application/passport`: MediaType =
        new MediaType(mainType, "passport", Compressible, NotBinary)
      lazy val `application/patch-ops-error+xml`: MediaType =
        new MediaType(mainType, "patch-ops-error+xml", Compressible, NotBinary, List("xer"))
      lazy val `application/pdf`: MediaType =
        new MediaType(mainType, "pdf", Uncompressible, Binary, List("pdf"))
      lazy val `application/pdx`: MediaType =
        new MediaType(mainType, "pdx", Compressible, NotBinary)
      lazy val `application/pgp-encrypted`: MediaType =
        new MediaType(mainType, "pgp-encrypted", Uncompressible, NotBinary, List("pgp"))
      lazy val `application/pgp-keys`: MediaType =
        new MediaType(mainType, "pgp-keys", Compressible, NotBinary)
      lazy val `application/pgp-signature`: MediaType =
        new MediaType(mainType, "pgp-signature", Compressible, NotBinary, List("asc", "sig"))
      lazy val `application/pics-rules`: MediaType =
        new MediaType(mainType, "pics-rules", Compressible, NotBinary, List("prf"))
      lazy val `application/pidf+xml`: MediaType =
        new MediaType(mainType, "pidf+xml", Compressible, NotBinary)
      lazy val `application/pidf-diff+xml`: MediaType =
        new MediaType(mainType, "pidf-diff+xml", Compressible, NotBinary)
      lazy val `application/pkcs10`: MediaType =
        new MediaType(mainType, "pkcs10", Compressible, NotBinary, List("p10"))
      lazy val `application/pkcs12`: MediaType =
        new MediaType(mainType, "pkcs12", Compressible, NotBinary)
      lazy val `application/pkcs7-mime`: MediaType =
        new MediaType(mainType, "pkcs7-mime", Compressible, NotBinary, List("p7m", "p7c"))
      lazy val `application/pkcs7-signature`: MediaType =
        new MediaType(mainType, "pkcs7-signature", Compressible, NotBinary, List("p7s"))
      lazy val `application/pkcs8`: MediaType =
        new MediaType(mainType, "pkcs8", Compressible, NotBinary, List("p8"))
      lazy val `application/pkcs8-encrypted`: MediaType =
        new MediaType(mainType, "pkcs8-encrypted", Compressible, NotBinary)
      lazy val `application/pkix-attr-cert`: MediaType =
        new MediaType(mainType, "pkix-attr-cert", Compressible, NotBinary, List("ac"))
      lazy val `application/pkix-cert`: MediaType =
        new MediaType(mainType, "pkix-cert", Compressible, NotBinary, List("cer"))
      lazy val `application/pkix-crl`: MediaType =
        new MediaType(mainType, "pkix-crl", Compressible, NotBinary, List("crl"))
      lazy val `application/pkix-pkipath`: MediaType =
        new MediaType(mainType, "pkix-pkipath", Compressible, NotBinary, List("pkipath"))
      lazy val `application/pkixcmp`: MediaType =
        new MediaType(mainType, "pkixcmp", Compressible, NotBinary, List("pki"))
      lazy val `application/pls+xml`: MediaType =
        new MediaType(mainType, "pls+xml", Compressible, NotBinary, List("pls"))
      lazy val `application/poc-settings+xml`: MediaType =
        new MediaType(mainType, "poc-settings+xml", Compressible, NotBinary)
      lazy val `application/postscript`: MediaType =
        new MediaType(mainType, "postscript", Compressible, Binary, List("ai", "eps", "ps"))
      lazy val `application/ppsp-tracker+json`: MediaType =
        new MediaType(mainType, "ppsp-tracker+json", Compressible, NotBinary)
      lazy val `application/problem+json`: MediaType =
        new MediaType(mainType, "problem+json", Compressible, Binary)
      lazy val `application/problem+xml`: MediaType =
        new MediaType(mainType, "problem+xml", Compressible, NotBinary)
      lazy val `application/provenance+xml`: MediaType =
        new MediaType(mainType, "provenance+xml", Compressible, NotBinary)
      lazy val `application/prs.alvestrand.titrax-sheet`: MediaType =
        new MediaType(mainType, "prs.alvestrand.titrax-sheet", Compressible, NotBinary)
      lazy val `application/prs.cww`: MediaType =
        new MediaType(mainType, "prs.cww", Compressible, NotBinary, List("cww"))
      lazy val `application/prs.hpub+zip`: MediaType =
        new MediaType(mainType, "prs.hpub+zip", Compressible, NotBinary)
      lazy val `application/prs.nprend`: MediaType =
        new MediaType(mainType, "prs.nprend", Compressible, NotBinary)
      lazy val `application/prs.plucker`: MediaType =
        new MediaType(mainType, "prs.plucker", Compressible, NotBinary)
      lazy val `application/prs.rdf-xml-crypt`: MediaType =
        new MediaType(mainType, "prs.rdf-xml-crypt", Compressible, NotBinary)
      lazy val `application/prs.xsf+xml`: MediaType =
        new MediaType(mainType, "prs.xsf+xml", Compressible, NotBinary)
      lazy val `application/pskc+xml`: MediaType =
        new MediaType(mainType, "pskc+xml", Compressible, NotBinary, List("pskcxml"))
      lazy val `application/qsig`: MediaType =
        new MediaType(mainType, "qsig", Compressible, NotBinary)
      lazy val `application/raml+yaml`: MediaType =
        new MediaType(mainType, "raml+yaml", Compressible, NotBinary, List("raml"))
      lazy val `application/raptorfec`: MediaType =
        new MediaType(mainType, "raptorfec", Compressible, NotBinary)
      lazy val `application/rdap+json`: MediaType =
        new MediaType(mainType, "rdap+json", Compressible, NotBinary)
      lazy val `application/rdf+xml`: MediaType =
        new MediaType(mainType, "rdf+xml", Compressible, NotBinary, List("rdf"))
      lazy val `application/reginfo+xml`: MediaType =
        new MediaType(mainType, "reginfo+xml", Compressible, NotBinary, List("rif"))
      lazy val `application/relax-ng-compact-syntax`: MediaType =
        new MediaType(mainType, "relax-ng-compact-syntax", Compressible, NotBinary, List("rnc"))
      lazy val `application/remote-printing`: MediaType =
        new MediaType(mainType, "remote-printing", Compressible, NotBinary)
      lazy val `application/reputon+json`: MediaType =
        new MediaType(mainType, "reputon+json", Compressible, NotBinary)
      lazy val `application/resource-lists+xml`: MediaType =
        new MediaType(mainType, "resource-lists+xml", Compressible, NotBinary, List("rl"))
      lazy val `application/resource-lists-diff+xml`: MediaType =
        new MediaType(mainType, "resource-lists-diff+xml", Compressible, NotBinary, List("rld"))
      lazy val `application/rfc+xml`: MediaType =
        new MediaType(mainType, "rfc+xml", Compressible, NotBinary)
      lazy val `application/riscos`: MediaType =
        new MediaType(mainType, "riscos", Compressible, NotBinary)
      lazy val `application/rlmi+xml`: MediaType =
        new MediaType(mainType, "rlmi+xml", Compressible, NotBinary)
      lazy val `application/rls-services+xml`: MediaType =
        new MediaType(mainType, "rls-services+xml", Compressible, NotBinary, List("rs"))
      lazy val `application/route-apd+xml`: MediaType =
        new MediaType(mainType, "route-apd+xml", Compressible, NotBinary)
      lazy val `application/route-s-tsid+xml`: MediaType =
        new MediaType(mainType, "route-s-tsid+xml", Compressible, NotBinary)
      lazy val `application/route-usd+xml`: MediaType =
        new MediaType(mainType, "route-usd+xml", Compressible, NotBinary)
      lazy val `application/rpki-ghostbusters`: MediaType =
        new MediaType(mainType, "rpki-ghostbusters", Compressible, NotBinary, List("gbr"))
      lazy val `application/rpki-manifest`: MediaType =
        new MediaType(mainType, "rpki-manifest", Compressible, NotBinary, List("mft"))
      lazy val `application/rpki-publication`: MediaType =
        new MediaType(mainType, "rpki-publication", Compressible, NotBinary)
      lazy val `application/rpki-roa`: MediaType =
        new MediaType(mainType, "rpki-roa", Compressible, NotBinary, List("roa"))
      lazy val `application/rpki-updown`: MediaType =
        new MediaType(mainType, "rpki-updown", Compressible, NotBinary)
      lazy val `application/rsd+xml`: MediaType =
        new MediaType(mainType, "rsd+xml", Compressible, NotBinary, List("rsd"))
      lazy val `application/rss+xml`: MediaType =
        new MediaType(mainType, "rss+xml", Compressible, NotBinary, List("rss"))
      lazy val `application/rtf`: MediaType =
        new MediaType(mainType, "rtf", Compressible, NotBinary, List("rtf"))
      lazy val `application/rtploopback`: MediaType =
        new MediaType(mainType, "rtploopback", Compressible, NotBinary)
      lazy val `application/rtx`: MediaType =
        new MediaType(mainType, "rtx", Compressible, NotBinary)
      lazy val `application/samlassertion+xml`: MediaType =
        new MediaType(mainType, "samlassertion+xml", Compressible, NotBinary)
      lazy val `application/samlmetadata+xml`: MediaType =
        new MediaType(mainType, "samlmetadata+xml", Compressible, NotBinary)
      lazy val `application/sbml+xml`: MediaType =
        new MediaType(mainType, "sbml+xml", Compressible, NotBinary, List("sbml"))
      lazy val `application/scaip+xml`: MediaType =
        new MediaType(mainType, "scaip+xml", Compressible, NotBinary)
      lazy val `application/scim+json`: MediaType =
        new MediaType(mainType, "scim+json", Compressible, NotBinary)
      lazy val `application/scvp-cv-request`: MediaType =
        new MediaType(mainType, "scvp-cv-request", Compressible, NotBinary, List("scq"))
      lazy val `application/scvp-cv-response`: MediaType =
        new MediaType(mainType, "scvp-cv-response", Compressible, NotBinary, List("scs"))
      lazy val `application/scvp-vp-request`: MediaType =
        new MediaType(mainType, "scvp-vp-request", Compressible, NotBinary, List("spq"))
      lazy val `application/scvp-vp-response`: MediaType =
        new MediaType(mainType, "scvp-vp-response", Compressible, NotBinary, List("spp"))
      lazy val `application/sdp`: MediaType =
        new MediaType(mainType, "sdp", Compressible, NotBinary, List("sdp"))
      lazy val `application/sep+xml`: MediaType =
        new MediaType(mainType, "sep+xml", Compressible, NotBinary)
      lazy val `application/sep-exi`: MediaType =
        new MediaType(mainType, "sep-exi", Compressible, NotBinary)
      lazy val `application/session-info`: MediaType =
        new MediaType(mainType, "session-info", Compressible, NotBinary)
      lazy val `application/set-payment`: MediaType =
        new MediaType(mainType, "set-payment", Compressible, NotBinary)
      lazy val `application/set-payment-initiation`: MediaType =
        new MediaType(mainType, "set-payment-initiation", Compressible, NotBinary, List("setpay"))
      lazy val `application/set-registration`: MediaType =
        new MediaType(mainType, "set-registration", Compressible, NotBinary)
      lazy val `application/set-registration-initiation`: MediaType = new MediaType(
        mainType,
        "set-registration-initiation",
        Compressible,
        NotBinary,
        List("setreg"))
      lazy val `application/sgml`: MediaType =
        new MediaType(mainType, "sgml", Compressible, NotBinary)
      lazy val `application/sgml-open-catalog`: MediaType =
        new MediaType(mainType, "sgml-open-catalog", Compressible, NotBinary)
      lazy val `application/shf+xml`: MediaType =
        new MediaType(mainType, "shf+xml", Compressible, NotBinary, List("shf"))
      lazy val `application/sieve`: MediaType =
        new MediaType(mainType, "sieve", Compressible, NotBinary)
      lazy val `application/simple-filter+xml`: MediaType =
        new MediaType(mainType, "simple-filter+xml", Compressible, NotBinary)
      lazy val `application/simple-message-summary`: MediaType =
        new MediaType(mainType, "simple-message-summary", Compressible, NotBinary)
      lazy val `application/simplesymbolcontainer`: MediaType =
        new MediaType(mainType, "simplesymbolcontainer", Compressible, NotBinary)
      lazy val `application/slate`: MediaType =
        new MediaType(mainType, "slate", Compressible, NotBinary)
      lazy val `application/smil`: MediaType =
        new MediaType(mainType, "smil", Compressible, NotBinary)
      lazy val `application/smil+xml`: MediaType =
        new MediaType(mainType, "smil+xml", Compressible, NotBinary, List("smi", "smil"))
      lazy val `application/smpte336m`: MediaType =
        new MediaType(mainType, "smpte336m", Compressible, NotBinary)
      lazy val `application/soap+fastinfoset`: MediaType =
        new MediaType(mainType, "soap+fastinfoset", Compressible, NotBinary)
      lazy val `application/soap+xml`: MediaType =
        new MediaType(mainType, "soap+xml", Compressible, NotBinary)
      lazy val `application/sparql-query`: MediaType =
        new MediaType(mainType, "sparql-query", Compressible, NotBinary, List("rq"))
      lazy val `application/sparql-results+xml`: MediaType =
        new MediaType(mainType, "sparql-results+xml", Compressible, NotBinary, List("srx"))
      lazy val `application/spirits-event+xml`: MediaType =
        new MediaType(mainType, "spirits-event+xml", Compressible, NotBinary)
      lazy val `application/sql`: MediaType =
        new MediaType(mainType, "sql", Compressible, NotBinary)
      lazy val `application/srgs`: MediaType =
        new MediaType(mainType, "srgs", Compressible, NotBinary, List("gram"))
      lazy val `application/srgs+xml`: MediaType =
        new MediaType(mainType, "srgs+xml", Compressible, NotBinary, List("grxml"))
      lazy val `application/sru+xml`: MediaType =
        new MediaType(mainType, "sru+xml", Compressible, NotBinary, List("sru"))
      lazy val `application/ssdl+xml`: MediaType =
        new MediaType(mainType, "ssdl+xml", Compressible, NotBinary, List("ssdl"))
      lazy val `application/ssml+xml`: MediaType =
        new MediaType(mainType, "ssml+xml", Compressible, NotBinary, List("ssml"))
      lazy val `application/tamp-apex-update`: MediaType =
        new MediaType(mainType, "tamp-apex-update", Compressible, NotBinary)
      lazy val `application/tamp-apex-update-confirm`: MediaType =
        new MediaType(mainType, "tamp-apex-update-confirm", Compressible, NotBinary)
      lazy val `application/tamp-community-update`: MediaType =
        new MediaType(mainType, "tamp-community-update", Compressible, NotBinary)
      lazy val `application/tamp-community-update-confirm`: MediaType =
        new MediaType(mainType, "tamp-community-update-confirm", Compressible, NotBinary)
      lazy val `application/tamp-error`: MediaType =
        new MediaType(mainType, "tamp-error", Compressible, NotBinary)
      lazy val `application/tamp-sequence-adjust`: MediaType =
        new MediaType(mainType, "tamp-sequence-adjust", Compressible, NotBinary)
      lazy val `application/tamp-sequence-adjust-confirm`: MediaType =
        new MediaType(mainType, "tamp-sequence-adjust-confirm", Compressible, NotBinary)
      lazy val `application/tamp-status-query`: MediaType =
        new MediaType(mainType, "tamp-status-query", Compressible, NotBinary)
      lazy val `application/tamp-status-response`: MediaType =
        new MediaType(mainType, "tamp-status-response", Compressible, NotBinary)
      lazy val `application/tamp-update`: MediaType =
        new MediaType(mainType, "tamp-update", Compressible, NotBinary)
      lazy val `application/tamp-update-confirm`: MediaType =
        new MediaType(mainType, "tamp-update-confirm", Compressible, NotBinary)
      lazy val `application/tar`: MediaType =
        new MediaType(mainType, "tar", Compressible, NotBinary)
      lazy val `application/tei+xml`: MediaType =
        new MediaType(mainType, "tei+xml", Compressible, NotBinary, List("tei", "teicorpus"))
      lazy val `application/thraud+xml`: MediaType =
        new MediaType(mainType, "thraud+xml", Compressible, NotBinary, List("tfi"))
      lazy val `application/timestamp-query`: MediaType =
        new MediaType(mainType, "timestamp-query", Compressible, NotBinary)
      lazy val `application/timestamp-reply`: MediaType =
        new MediaType(mainType, "timestamp-reply", Compressible, NotBinary)
      lazy val `application/timestamped-data`: MediaType =
        new MediaType(mainType, "timestamped-data", Compressible, NotBinary, List("tsd"))
      lazy val `application/tnauthlist`: MediaType =
        new MediaType(mainType, "tnauthlist", Compressible, NotBinary)
      lazy val `application/trig`: MediaType =
        new MediaType(mainType, "trig", Compressible, NotBinary)
      lazy val `application/ttml+xml`: MediaType =
        new MediaType(mainType, "ttml+xml", Compressible, NotBinary)
      lazy val `application/tve-trigger`: MediaType =
        new MediaType(mainType, "tve-trigger", Compressible, NotBinary)
      lazy val `application/ulpfec`: MediaType =
        new MediaType(mainType, "ulpfec", Compressible, NotBinary)
      lazy val `application/urc-grpsheet+xml`: MediaType =
        new MediaType(mainType, "urc-grpsheet+xml", Compressible, NotBinary)
      lazy val `application/urc-ressheet+xml`: MediaType =
        new MediaType(mainType, "urc-ressheet+xml", Compressible, NotBinary)
      lazy val `application/urc-targetdesc+xml`: MediaType =
        new MediaType(mainType, "urc-targetdesc+xml", Compressible, NotBinary)
      lazy val `application/urc-uisocketdesc+xml`: MediaType =
        new MediaType(mainType, "urc-uisocketdesc+xml", Compressible, NotBinary)
      lazy val `application/vcard+json`: MediaType =
        new MediaType(mainType, "vcard+json", Compressible, NotBinary)
      lazy val `application/vcard+xml`: MediaType =
        new MediaType(mainType, "vcard+xml", Compressible, NotBinary)
      lazy val `application/vemmi`: MediaType =
        new MediaType(mainType, "vemmi", Compressible, NotBinary)
      lazy val `application/vividence.scriptfile`: MediaType =
        new MediaType(mainType, "vividence.scriptfile", Compressible, NotBinary)
      lazy val `application/vnd.1000minds.decision-model+xml`: MediaType =
        new MediaType(mainType, "vnd.1000minds.decision-model+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp-prose+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp-prose+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp-prose-pc3ch+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp-prose-pc3ch+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp-v2x-local-service-information`: MediaType =
        new MediaType(mainType, "vnd.3gpp-v2x-local-service-information", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.access-transfer-events+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.access-transfer-events+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.bsf+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.bsf+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.gmop+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.gmop+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-affiliation-command+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-affiliation-command+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-floor-request+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-floor-request+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-info+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-info+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-location-info+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-location-info+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-mbms-usage-info+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-mbms-usage-info+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mcptt-signed+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mcptt-signed+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.mid-call+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.mid-call+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.pic-bw-large`: MediaType =
        new MediaType(mainType, "vnd.3gpp.pic-bw-large", Compressible, NotBinary, List("plb"))
      lazy val `application/vnd.3gpp.pic-bw-small`: MediaType =
        new MediaType(mainType, "vnd.3gpp.pic-bw-small", Compressible, NotBinary, List("psb"))
      lazy val `application/vnd.3gpp.pic-bw-var`: MediaType =
        new MediaType(mainType, "vnd.3gpp.pic-bw-var", Compressible, NotBinary, List("pvb"))
      lazy val `application/vnd.3gpp.sms`: MediaType =
        new MediaType(mainType, "vnd.3gpp.sms", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.sms+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.sms+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.srvcc-ext+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.srvcc-ext+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.srvcc-info+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.srvcc-info+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.state-and-event-info+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.state-and-event-info+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp.ussd+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp.ussd+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp2.bcmcsinfo+xml`: MediaType =
        new MediaType(mainType, "vnd.3gpp2.bcmcsinfo+xml", Compressible, NotBinary)
      lazy val `application/vnd.3gpp2.sms`: MediaType =
        new MediaType(mainType, "vnd.3gpp2.sms", Compressible, NotBinary)
      lazy val `application/vnd.3gpp2.tcap`: MediaType =
        new MediaType(mainType, "vnd.3gpp2.tcap", Compressible, NotBinary, List("tcap"))
      lazy val `application/vnd.3lightssoftware.imagescal`: MediaType =
        new MediaType(mainType, "vnd.3lightssoftware.imagescal", Compressible, NotBinary)
      lazy val `application/vnd.3m.post-it-notes`: MediaType =
        new MediaType(mainType, "vnd.3m.post-it-notes", Compressible, NotBinary, List("pwn"))
      lazy val `application/vnd.accpac.simply.aso`: MediaType =
        new MediaType(mainType, "vnd.accpac.simply.aso", Compressible, NotBinary, List("aso"))
      lazy val `application/vnd.accpac.simply.imp`: MediaType =
        new MediaType(mainType, "vnd.accpac.simply.imp", Compressible, NotBinary, List("imp"))
      lazy val `application/vnd.acucobol`: MediaType =
        new MediaType(mainType, "vnd.acucobol", Compressible, NotBinary, List("acu"))
      lazy val `application/vnd.acucorp`: MediaType =
        new MediaType(mainType, "vnd.acucorp", Compressible, NotBinary, List("atc", "acutc"))
      lazy val `application/vnd.adobe.air-application-installer-package+zip`: MediaType =
        new MediaType(
          mainType,
          "vnd.adobe.air-application-installer-package+zip",
          Compressible,
          NotBinary,
          List("air"))
      lazy val `application/vnd.adobe.flash.movie`: MediaType =
        new MediaType(mainType, "vnd.adobe.flash.movie", Compressible, NotBinary)
      lazy val `application/vnd.adobe.formscentral.fcdt`: MediaType = new MediaType(
        mainType,
        "vnd.adobe.formscentral.fcdt",
        Compressible,
        NotBinary,
        List("fcdt"))
      lazy val `application/vnd.adobe.fxp`: MediaType =
        new MediaType(mainType, "vnd.adobe.fxp", Compressible, NotBinary, List("fxp", "fxpl"))
      lazy val `application/vnd.adobe.partial-upload`: MediaType =
        new MediaType(mainType, "vnd.adobe.partial-upload", Compressible, NotBinary)
      lazy val `application/vnd.adobe.xdp+xml`: MediaType =
        new MediaType(mainType, "vnd.adobe.xdp+xml", Compressible, NotBinary, List("xdp"))
      lazy val `application/vnd.adobe.xfdf`: MediaType =
        new MediaType(mainType, "vnd.adobe.xfdf", Compressible, NotBinary, List("xfdf"))
      lazy val `application/vnd.aether.imp`: MediaType =
        new MediaType(mainType, "vnd.aether.imp", Compressible, NotBinary)
      lazy val `application/vnd.ah-barcode`: MediaType =
        new MediaType(mainType, "vnd.ah-barcode", Compressible, NotBinary)
      lazy val `application/vnd.ahead.space`: MediaType =
        new MediaType(mainType, "vnd.ahead.space", Compressible, NotBinary, List("ahead"))
      lazy val `application/vnd.airzip.filesecure.azf`: MediaType =
        new MediaType(mainType, "vnd.airzip.filesecure.azf", Compressible, NotBinary, List("azf"))
      lazy val `application/vnd.airzip.filesecure.azs`: MediaType =
        new MediaType(mainType, "vnd.airzip.filesecure.azs", Compressible, NotBinary, List("azs"))
      lazy val `application/vnd.amadeus+json`: MediaType =
        new MediaType(mainType, "vnd.amadeus+json", Compressible, NotBinary)
      lazy val `application/vnd.amazon.ebook`: MediaType =
        new MediaType(mainType, "vnd.amazon.ebook", Compressible, NotBinary, List("azw"))
      lazy val `application/vnd.amazon.mobi8-ebook`: MediaType =
        new MediaType(mainType, "vnd.amazon.mobi8-ebook", Compressible, NotBinary)
      lazy val `application/vnd.americandynamics.acc`: MediaType =
        new MediaType(mainType, "vnd.americandynamics.acc", Compressible, NotBinary, List("acc"))
      lazy val `application/vnd.amiga.ami`: MediaType =
        new MediaType(mainType, "vnd.amiga.ami", Compressible, NotBinary, List("ami"))
      lazy val `application/vnd.amundsen.maze+xml`: MediaType =
        new MediaType(mainType, "vnd.amundsen.maze+xml", Compressible, NotBinary)
      lazy val `application/vnd.android.package-archive`: MediaType = new MediaType(
        mainType,
        "vnd.android.package-archive",
        Uncompressible,
        NotBinary,
        List("apk"))
      lazy val `application/vnd.anki`: MediaType =
        new MediaType(mainType, "vnd.anki", Compressible, NotBinary)
      lazy val `application/vnd.anser-web-certificate-issue-initiation`: MediaType = new MediaType(
        mainType,
        "vnd.anser-web-certificate-issue-initiation",
        Compressible,
        NotBinary,
        List("cii"))
      lazy val `application/vnd.anser-web-funds-transfer-initiation`: MediaType = new MediaType(
        mainType,
        "vnd.anser-web-funds-transfer-initiation",
        Compressible,
        NotBinary,
        List("fti"))
      lazy val `application/vnd.antix.game-component`: MediaType =
        new MediaType(mainType, "vnd.antix.game-component", Compressible, NotBinary, List("atx"))
      lazy val `application/vnd.apache.thrift.binary`: MediaType =
        new MediaType(mainType, "vnd.apache.thrift.binary", Compressible, NotBinary)
      lazy val `application/vnd.apache.thrift.compact`: MediaType =
        new MediaType(mainType, "vnd.apache.thrift.compact", Compressible, NotBinary)
      lazy val `application/vnd.apache.thrift.json`: MediaType =
        new MediaType(mainType, "vnd.apache.thrift.json", Compressible, NotBinary)
      lazy val `application/vnd.api+json`: MediaType =
        new MediaType(mainType, "vnd.api+json", Compressible, Binary)
      lazy val `application/vnd.apothekende.reservation+json`: MediaType =
        new MediaType(mainType, "vnd.apothekende.reservation+json", Compressible, NotBinary)
      lazy val `application/vnd.apple.installer+xml`: MediaType =
        new MediaType(mainType, "vnd.apple.installer+xml", Compressible, NotBinary, List("mpkg"))
      lazy val `application/vnd.apple.mpegurl`: MediaType =
        new MediaType(mainType, "vnd.apple.mpegurl", Compressible, NotBinary, List("m3u8"))
      lazy val `application/vnd.apple.pkpass`: MediaType =
        new MediaType(mainType, "vnd.apple.pkpass", Uncompressible, NotBinary, List("pkpass"))
      lazy val `application/vnd.arastra.swi`: MediaType =
        new MediaType(mainType, "vnd.arastra.swi", Compressible, NotBinary)
      lazy val `application/vnd.aristanetworks.swi`: MediaType =
        new MediaType(mainType, "vnd.aristanetworks.swi", Compressible, NotBinary, List("swi"))
      lazy val `application/vnd.artsquare`: MediaType =
        new MediaType(mainType, "vnd.artsquare", Compressible, NotBinary)
      lazy val `application/vnd.astraea-software.iota`: MediaType =
        new MediaType(mainType, "vnd.astraea-software.iota", Compressible, NotBinary, List("iota"))
      lazy val `application/vnd.audiograph`: MediaType =
        new MediaType(mainType, "vnd.audiograph", Compressible, NotBinary, List("aep"))
      lazy val `application/vnd.autopackage`: MediaType =
        new MediaType(mainType, "vnd.autopackage", Compressible, NotBinary)
      lazy val `application/vnd.avalon+json`: MediaType =
        new MediaType(mainType, "vnd.avalon+json", Compressible, NotBinary)
      lazy val `application/vnd.avistar+xml`: MediaType =
        new MediaType(mainType, "vnd.avistar+xml", Compressible, NotBinary)
      lazy val `application/vnd.balsamiq.bmml+xml`: MediaType =
        new MediaType(mainType, "vnd.balsamiq.bmml+xml", Compressible, NotBinary)
      lazy val `application/vnd.balsamiq.bmpr`: MediaType =
        new MediaType(mainType, "vnd.balsamiq.bmpr", Compressible, NotBinary)
      lazy val `application/vnd.bbf.usp.msg`: MediaType =
        new MediaType(mainType, "vnd.bbf.usp.msg", Compressible, NotBinary)
      lazy val `application/vnd.bbf.usp.msg+json`: MediaType =
        new MediaType(mainType, "vnd.bbf.usp.msg+json", Compressible, NotBinary)
      lazy val `application/vnd.bekitzur-stech+json`: MediaType =
        new MediaType(mainType, "vnd.bekitzur-stech+json", Compressible, NotBinary)
      lazy val `application/vnd.bint.med-content`: MediaType =
        new MediaType(mainType, "vnd.bint.med-content", Compressible, NotBinary)
      lazy val `application/vnd.biopax.rdf+xml`: MediaType =
        new MediaType(mainType, "vnd.biopax.rdf+xml", Compressible, NotBinary)
      lazy val `application/vnd.blink-idb-value-wrapper`: MediaType =
        new MediaType(mainType, "vnd.blink-idb-value-wrapper", Compressible, NotBinary)
      lazy val `application/vnd.blueice.multipass`: MediaType =
        new MediaType(mainType, "vnd.blueice.multipass", Compressible, NotBinary, List("mpm"))
      lazy val `application/vnd.bluetooth.ep.oob`: MediaType =
        new MediaType(mainType, "vnd.bluetooth.ep.oob", Compressible, NotBinary)
      lazy val `application/vnd.bluetooth.le.oob`: MediaType =
        new MediaType(mainType, "vnd.bluetooth.le.oob", Compressible, NotBinary)
      lazy val `application/vnd.bmi`: MediaType =
        new MediaType(mainType, "vnd.bmi", Compressible, NotBinary, List("bmi"))
      lazy val `application/vnd.businessobjects`: MediaType =
        new MediaType(mainType, "vnd.businessobjects", Compressible, NotBinary, List("rep"))
      lazy val `application/vnd.cab-jscript`: MediaType =
        new MediaType(mainType, "vnd.cab-jscript", Compressible, NotBinary)
      lazy val `application/vnd.canon-cpdl`: MediaType =
        new MediaType(mainType, "vnd.canon-cpdl", Compressible, NotBinary)
      lazy val `application/vnd.canon-lips`: MediaType =
        new MediaType(mainType, "vnd.canon-lips", Compressible, NotBinary)
      lazy val `application/vnd.capasystems-pg+json`: MediaType =
        new MediaType(mainType, "vnd.capasystems-pg+json", Compressible, NotBinary)
      lazy val `application/vnd.cendio.thinlinc.clientconf`: MediaType =
        new MediaType(mainType, "vnd.cendio.thinlinc.clientconf", Compressible, NotBinary)
      lazy val `application/vnd.century-systems.tcp_stream`: MediaType =
        new MediaType(mainType, "vnd.century-systems.tcp_stream", Compressible, NotBinary)
      lazy val `application/vnd.chemdraw+xml`: MediaType =
        new MediaType(mainType, "vnd.chemdraw+xml", Compressible, NotBinary, List("cdxml"))
      lazy val `application/vnd.chess-pgn`: MediaType =
        new MediaType(mainType, "vnd.chess-pgn", Compressible, NotBinary)
      lazy val `application/vnd.chipnuts.karaoke-mmd`: MediaType =
        new MediaType(mainType, "vnd.chipnuts.karaoke-mmd", Compressible, NotBinary, List("mmd"))
      lazy val `application/vnd.cinderella`: MediaType =
        new MediaType(mainType, "vnd.cinderella", Compressible, NotBinary, List("cdy"))
      lazy val `application/vnd.cirpack.isdn-ext`: MediaType =
        new MediaType(mainType, "vnd.cirpack.isdn-ext", Compressible, NotBinary)
      lazy val `application/vnd.citationstyles.style+xml`: MediaType =
        new MediaType(mainType, "vnd.citationstyles.style+xml", Compressible, NotBinary)
      lazy val `application/vnd.claymore`: MediaType =
        new MediaType(mainType, "vnd.claymore", Compressible, NotBinary, List("cla"))
      lazy val `application/vnd.cloanto.rp9`: MediaType =
        new MediaType(mainType, "vnd.cloanto.rp9", Compressible, NotBinary, List("rp9"))
      lazy val `application/vnd.clonk.c4group`: MediaType = new MediaType(
        mainType,
        "vnd.clonk.c4group",
        Compressible,
        NotBinary,
        List("c4g", "c4d", "c4f", "c4p", "c4u"))
      lazy val `application/vnd.cluetrust.cartomobile-config`: MediaType = new MediaType(
        mainType,
        "vnd.cluetrust.cartomobile-config",
        Compressible,
        NotBinary,
        List("c11amc"))
      lazy val `application/vnd.cluetrust.cartomobile-config-pkg`: MediaType = new MediaType(
        mainType,
        "vnd.cluetrust.cartomobile-config-pkg",
        Compressible,
        NotBinary,
        List("c11amz"))
      lazy val `application/vnd.coffeescript`: MediaType =
        new MediaType(mainType, "vnd.coffeescript", Compressible, NotBinary)
      lazy val `application/vnd.collabio.xodocuments.document`: MediaType =
        new MediaType(mainType, "vnd.collabio.xodocuments.document", Compressible, NotBinary)
      lazy val `application/vnd.collabio.xodocuments.document-template`: MediaType = new MediaType(
        mainType,
        "vnd.collabio.xodocuments.document-template",
        Compressible,
        NotBinary)
      lazy val `application/vnd.collabio.xodocuments.presentation`: MediaType =
        new MediaType(mainType, "vnd.collabio.xodocuments.presentation", Compressible, NotBinary)
      lazy val `application/vnd.collabio.xodocuments.presentation-template`: MediaType =
        new MediaType(
          mainType,
          "vnd.collabio.xodocuments.presentation-template",
          Compressible,
          NotBinary)
      lazy val `application/vnd.collabio.xodocuments.spreadsheet`: MediaType =
        new MediaType(mainType, "vnd.collabio.xodocuments.spreadsheet", Compressible, NotBinary)
      lazy val `application/vnd.collabio.xodocuments.spreadsheet-template`: MediaType =
        new MediaType(
          mainType,
          "vnd.collabio.xodocuments.spreadsheet-template",
          Compressible,
          NotBinary)
      lazy val `application/vnd.collection+json`: MediaType =
        new MediaType(mainType, "vnd.collection+json", Compressible, NotBinary)
      lazy val `application/vnd.collection.doc+json`: MediaType =
        new MediaType(mainType, "vnd.collection.doc+json", Compressible, NotBinary)
      lazy val `application/vnd.collection.next+json`: MediaType =
        new MediaType(mainType, "vnd.collection.next+json", Compressible, NotBinary)
      lazy val `application/vnd.comicbook+zip`: MediaType =
        new MediaType(mainType, "vnd.comicbook+zip", Compressible, NotBinary)
      lazy val `application/vnd.comicbook-rar`: MediaType =
        new MediaType(mainType, "vnd.comicbook-rar", Compressible, NotBinary)
      lazy val `application/vnd.commerce-battelle`: MediaType =
        new MediaType(mainType, "vnd.commerce-battelle", Compressible, NotBinary)
      lazy val `application/vnd.commonspace`: MediaType =
        new MediaType(mainType, "vnd.commonspace", Compressible, NotBinary, List("csp"))
      lazy val `application/vnd.contact.cmsg`: MediaType =
        new MediaType(mainType, "vnd.contact.cmsg", Compressible, NotBinary, List("cdbcmsg"))
      lazy val `application/vnd.coreos.ignition+json`: MediaType =
        new MediaType(mainType, "vnd.coreos.ignition+json", Compressible, NotBinary)
      lazy val `application/vnd.cosmocaller`: MediaType =
        new MediaType(mainType, "vnd.cosmocaller", Compressible, NotBinary, List("cmc"))
      lazy val `application/vnd.crick.clicker`: MediaType =
        new MediaType(mainType, "vnd.crick.clicker", Compressible, NotBinary, List("clkx"))
      lazy val `application/vnd.crick.clicker.keyboard`: MediaType =
        new MediaType(mainType, "vnd.crick.clicker.keyboard", Compressible, NotBinary, List("clkk"))
      lazy val `application/vnd.crick.clicker.palette`: MediaType =
        new MediaType(mainType, "vnd.crick.clicker.palette", Compressible, NotBinary, List("clkp"))
      lazy val `application/vnd.crick.clicker.template`: MediaType =
        new MediaType(mainType, "vnd.crick.clicker.template", Compressible, NotBinary, List("clkt"))
      lazy val `application/vnd.crick.clicker.wordbank`: MediaType =
        new MediaType(mainType, "vnd.crick.clicker.wordbank", Compressible, NotBinary, List("clkw"))
      lazy val all: List[MediaType] = List(
        `application/1d-interleaved-parityfec`,
        `application/3gpdash-qoe-report+xml`,
        `application/3gpp-ims+xml`,
        `application/a2l`,
        `application/activemessage`,
        `application/activity+json`,
        `application/alto-costmap+json`,
        `application/alto-costmapfilter+json`,
        `application/alto-directory+json`,
        `application/alto-endpointcost+json`,
        `application/alto-endpointcostparams+json`,
        `application/alto-endpointprop+json`,
        `application/alto-endpointpropparams+json`,
        `application/alto-error+json`,
        `application/alto-networkmap+json`,
        `application/alto-networkmapfilter+json`,
        `application/aml`,
        `application/andrew-inset`,
        `application/applefile`,
        `application/applixware`,
        `application/atf`,
        `application/atfx`,
        `application/atom+xml`,
        `application/atomcat+xml`,
        `application/atomdeleted+xml`,
        `application/atomicmail`,
        `application/atomsvc+xml`,
        `application/atxml`,
        `application/auth-policy+xml`,
        `application/bacnet-xdd+zip`,
        `application/batch-smtp`,
        `application/bdoc`,
        `application/beep+xml`,
        `application/calendar+json`,
        `application/calendar+xml`,
        `application/call-completion`,
        `application/cals-1840`,
        `application/cbor`,
        `application/cccex`,
        `application/ccmp+xml`,
        `application/ccxml+xml`,
        `application/cdfx+xml`,
        `application/cdmi-capability`,
        `application/cdmi-container`,
        `application/cdmi-domain`,
        `application/cdmi-object`,
        `application/cdmi-queue`,
        `application/cdni`,
        `application/cea`,
        `application/cea-2018+xml`,
        `application/cellml+xml`,
        `application/cfw`,
        `application/clue_info+xml`,
        `application/cms`,
        `application/cnrp+xml`,
        `application/coap-group+json`,
        `application/coap-payload`,
        `application/commonground`,
        `application/conference-info+xml`,
        `application/cose`,
        `application/cose-key`,
        `application/cose-key-set`,
        `application/cpl+xml`,
        `application/csrattrs`,
        `application/csta+xml`,
        `application/cstadata+xml`,
        `application/csvm+json`,
        `application/cu-seeme`,
        `application/cwt`,
        `application/cybercash`,
        `application/dart`,
        `application/dash+xml`,
        `application/dashdelta`,
        `application/davmount+xml`,
        `application/dca-rft`,
        `application/dcd`,
        `application/dec-dx`,
        `application/dialog-info+xml`,
        `application/dicom`,
        `application/dicom+json`,
        `application/dicom+xml`,
        `application/dii`,
        `application/dit`,
        `application/dns`,
        `application/docbook+xml`,
        `application/dskpp+xml`,
        `application/dssc+der`,
        `application/dssc+xml`,
        `application/dvcs`,
        `application/ecmascript`,
        `application/edi-consent`,
        `application/edi-x12`,
        `application/edifact`,
        `application/efi`,
        `application/emergencycalldata.comment+xml`,
        `application/emergencycalldata.control+xml`,
        `application/emergencycalldata.deviceinfo+xml`,
        `application/emergencycalldata.ecall.msd`,
        `application/emergencycalldata.providerinfo+xml`,
        `application/emergencycalldata.serviceinfo+xml`,
        `application/emergencycalldata.subscriberinfo+xml`,
        `application/emergencycalldata.veds+xml`,
        `application/emma+xml`,
        `application/emotionml+xml`,
        `application/encaprtp`,
        `application/epp+xml`,
        `application/epub+zip`,
        `application/eshop`,
        `application/exi`,
        `application/fastinfoset`,
        `application/fastsoap`,
        `application/fdt+xml`,
        `application/fhir+json`,
        `application/fhir+xml`,
        `application/fido.trusted-apps+json`,
        `application/fits`,
        `application/font-sfnt`,
        `application/font-tdpfr`,
        `application/font-woff`,
        `application/framework-attributes+xml`,
        `application/geo+json`,
        `application/geo+json-seq`,
        `application/geoxacml+xml`,
        `application/gml+xml`,
        `application/gpx+xml`,
        `application/gxf`,
        `application/gzip`,
        `application/h224`,
        `application/held+xml`,
        `application/hjson`,
        `application/http`,
        `application/hyperstudio`,
        `application/ibe-key-request+xml`,
        `application/ibe-pkg-reply+xml`,
        `application/ibe-pp-data`,
        `application/iges`,
        `application/im-iscomposing+xml`,
        `application/index`,
        `application/index.cmd`,
        `application/index.obj`,
        `application/index.response`,
        `application/index.vnd`,
        `application/inkml+xml`,
        `application/iotp`,
        `application/ipfix`,
        `application/ipp`,
        `application/isup`,
        `application/its+xml`,
        `application/java-archive`,
        `application/java-serialized-object`,
        `application/java-vm`,
        `application/javascript`,
        `application/jf2feed+json`,
        `application/jose`,
        `application/jose+json`,
        `application/jrd+json`,
        `application/json`,
        `application/json-patch+json`,
        `application/json-seq`,
        `application/json5`,
        `application/jsonml+json`,
        `application/jwk+json`,
        `application/jwk-set+json`,
        `application/jwt`,
        `application/kpml-request+xml`,
        `application/kpml-response+xml`,
        `application/ld+json`,
        `application/lgr+xml`,
        `application/link-format`,
        `application/load-control+xml`,
        `application/lost+xml`,
        `application/lostsync+xml`,
        `application/lxf`,
        `application/mac-binhex40`,
        `application/mac-compactpro`,
        `application/macwriteii`,
        `application/mads+xml`,
        `application/manifest+json`,
        `application/marc`,
        `application/marcxml+xml`,
        `application/mathematica`,
        `application/mathml+xml`,
        `application/mathml-content+xml`,
        `application/mathml-presentation+xml`,
        `application/mbms-associated-procedure-description+xml`,
        `application/mbms-deregister+xml`,
        `application/mbms-envelope+xml`,
        `application/mbms-msk+xml`,
        `application/mbms-msk-response+xml`,
        `application/mbms-protection-description+xml`,
        `application/mbms-reception-report+xml`,
        `application/mbms-register+xml`,
        `application/mbms-register-response+xml`,
        `application/mbms-schedule+xml`,
        `application/mbms-user-service-description+xml`,
        `application/mbox`,
        `application/media-policy-dataset+xml`,
        `application/media_control+xml`,
        `application/mediaservercontrol+xml`,
        `application/merge-patch+json`,
        `application/metalink+xml`,
        `application/metalink4+xml`,
        `application/mets+xml`,
        `application/mf4`,
        `application/mikey`,
        `application/mmt-usd+xml`,
        `application/mods+xml`,
        `application/moss-keys`,
        `application/moss-signature`,
        `application/mosskey-data`,
        `application/mosskey-request`,
        `application/mp21`,
        `application/mp4`,
        `application/mpeg4-generic`,
        `application/mpeg4-iod`,
        `application/mpeg4-iod-xmt`,
        `application/mrb-consumer+xml`,
        `application/mrb-publish+xml`,
        `application/msc-ivr+xml`,
        `application/msc-mixer+xml`,
        `application/msword`,
        `application/mud+json`,
        `application/mxf`,
        `application/n-quads`,
        `application/n-triples`,
        `application/nasdata`,
        `application/news-checkgroups`,
        `application/news-groupinfo`,
        `application/news-transmission`,
        `application/nlsml+xml`,
        `application/node`,
        `application/nss`,
        `application/ocsp-request`,
        `application/ocsp-response`,
        `application/octet-stream`,
        `application/oda`,
        `application/odx`,
        `application/oebps-package+xml`,
        `application/ogg`,
        `application/omdoc+xml`,
        `application/onenote`,
        `application/oxps`,
        `application/p2p-overlay+xml`,
        `application/parityfec`,
        `application/passport`,
        `application/patch-ops-error+xml`,
        `application/pdf`,
        `application/pdx`,
        `application/pgp-encrypted`,
        `application/pgp-keys`,
        `application/pgp-signature`,
        `application/pics-rules`,
        `application/pidf+xml`,
        `application/pidf-diff+xml`,
        `application/pkcs10`,
        `application/pkcs12`,
        `application/pkcs7-mime`,
        `application/pkcs7-signature`,
        `application/pkcs8`,
        `application/pkcs8-encrypted`,
        `application/pkix-attr-cert`,
        `application/pkix-cert`,
        `application/pkix-crl`,
        `application/pkix-pkipath`,
        `application/pkixcmp`,
        `application/pls+xml`,
        `application/poc-settings+xml`,
        `application/postscript`,
        `application/ppsp-tracker+json`,
        `application/problem+json`,
        `application/problem+xml`,
        `application/provenance+xml`,
        `application/prs.alvestrand.titrax-sheet`,
        `application/prs.cww`,
        `application/prs.hpub+zip`,
        `application/prs.nprend`,
        `application/prs.plucker`,
        `application/prs.rdf-xml-crypt`,
        `application/prs.xsf+xml`,
        `application/pskc+xml`,
        `application/qsig`,
        `application/raml+yaml`,
        `application/raptorfec`,
        `application/rdap+json`,
        `application/rdf+xml`,
        `application/reginfo+xml`,
        `application/relax-ng-compact-syntax`,
        `application/remote-printing`,
        `application/reputon+json`,
        `application/resource-lists+xml`,
        `application/resource-lists-diff+xml`,
        `application/rfc+xml`,
        `application/riscos`,
        `application/rlmi+xml`,
        `application/rls-services+xml`,
        `application/route-apd+xml`,
        `application/route-s-tsid+xml`,
        `application/route-usd+xml`,
        `application/rpki-ghostbusters`,
        `application/rpki-manifest`,
        `application/rpki-publication`,
        `application/rpki-roa`,
        `application/rpki-updown`,
        `application/rsd+xml`,
        `application/rss+xml`,
        `application/rtf`,
        `application/rtploopback`,
        `application/rtx`,
        `application/samlassertion+xml`,
        `application/samlmetadata+xml`,
        `application/sbml+xml`,
        `application/scaip+xml`,
        `application/scim+json`,
        `application/scvp-cv-request`,
        `application/scvp-cv-response`,
        `application/scvp-vp-request`,
        `application/scvp-vp-response`,
        `application/sdp`,
        `application/sep+xml`,
        `application/sep-exi`,
        `application/session-info`,
        `application/set-payment`,
        `application/set-payment-initiation`,
        `application/set-registration`,
        `application/set-registration-initiation`,
        `application/sgml`,
        `application/sgml-open-catalog`,
        `application/shf+xml`,
        `application/sieve`,
        `application/simple-filter+xml`,
        `application/simple-message-summary`,
        `application/simplesymbolcontainer`,
        `application/slate`,
        `application/smil`,
        `application/smil+xml`,
        `application/smpte336m`,
        `application/soap+fastinfoset`,
        `application/soap+xml`,
        `application/sparql-query`,
        `application/sparql-results+xml`,
        `application/spirits-event+xml`,
        `application/sql`,
        `application/srgs`,
        `application/srgs+xml`,
        `application/sru+xml`,
        `application/ssdl+xml`,
        `application/ssml+xml`,
        `application/tamp-apex-update`,
        `application/tamp-apex-update-confirm`,
        `application/tamp-community-update`,
        `application/tamp-community-update-confirm`,
        `application/tamp-error`,
        `application/tamp-sequence-adjust`,
        `application/tamp-sequence-adjust-confirm`,
        `application/tamp-status-query`,
        `application/tamp-status-response`,
        `application/tamp-update`,
        `application/tamp-update-confirm`,
        `application/tar`,
        `application/tei+xml`,
        `application/thraud+xml`,
        `application/timestamp-query`,
        `application/timestamp-reply`,
        `application/timestamped-data`,
        `application/tnauthlist`,
        `application/trig`,
        `application/ttml+xml`,
        `application/tve-trigger`,
        `application/ulpfec`,
        `application/urc-grpsheet+xml`,
        `application/urc-ressheet+xml`,
        `application/urc-targetdesc+xml`,
        `application/urc-uisocketdesc+xml`,
        `application/vcard+json`,
        `application/vcard+xml`,
        `application/vemmi`,
        `application/vividence.scriptfile`,
        `application/vnd.1000minds.decision-model+xml`,
        `application/vnd.3gpp-prose+xml`,
        `application/vnd.3gpp-prose-pc3ch+xml`,
        `application/vnd.3gpp-v2x-local-service-information`,
        `application/vnd.3gpp.access-transfer-events+xml`,
        `application/vnd.3gpp.bsf+xml`,
        `application/vnd.3gpp.gmop+xml`,
        `application/vnd.3gpp.mcptt-affiliation-command+xml`,
        `application/vnd.3gpp.mcptt-floor-request+xml`,
        `application/vnd.3gpp.mcptt-info+xml`,
        `application/vnd.3gpp.mcptt-location-info+xml`,
        `application/vnd.3gpp.mcptt-mbms-usage-info+xml`,
        `application/vnd.3gpp.mcptt-signed+xml`,
        `application/vnd.3gpp.mid-call+xml`,
        `application/vnd.3gpp.pic-bw-large`,
        `application/vnd.3gpp.pic-bw-small`,
        `application/vnd.3gpp.pic-bw-var`,
        `application/vnd.3gpp.sms`,
        `application/vnd.3gpp.sms+xml`,
        `application/vnd.3gpp.srvcc-ext+xml`,
        `application/vnd.3gpp.srvcc-info+xml`,
        `application/vnd.3gpp.state-and-event-info+xml`,
        `application/vnd.3gpp.ussd+xml`,
        `application/vnd.3gpp2.bcmcsinfo+xml`,
        `application/vnd.3gpp2.sms`,
        `application/vnd.3gpp2.tcap`,
        `application/vnd.3lightssoftware.imagescal`,
        `application/vnd.3m.post-it-notes`,
        `application/vnd.accpac.simply.aso`,
        `application/vnd.accpac.simply.imp`,
        `application/vnd.acucobol`,
        `application/vnd.acucorp`,
        `application/vnd.adobe.air-application-installer-package+zip`,
        `application/vnd.adobe.flash.movie`,
        `application/vnd.adobe.formscentral.fcdt`,
        `application/vnd.adobe.fxp`,
        `application/vnd.adobe.partial-upload`,
        `application/vnd.adobe.xdp+xml`,
        `application/vnd.adobe.xfdf`,
        `application/vnd.aether.imp`,
        `application/vnd.ah-barcode`,
        `application/vnd.ahead.space`,
        `application/vnd.airzip.filesecure.azf`,
        `application/vnd.airzip.filesecure.azs`,
        `application/vnd.amadeus+json`,
        `application/vnd.amazon.ebook`,
        `application/vnd.amazon.mobi8-ebook`,
        `application/vnd.americandynamics.acc`,
        `application/vnd.amiga.ami`,
        `application/vnd.amundsen.maze+xml`,
        `application/vnd.android.package-archive`,
        `application/vnd.anki`,
        `application/vnd.anser-web-certificate-issue-initiation`,
        `application/vnd.anser-web-funds-transfer-initiation`,
        `application/vnd.antix.game-component`,
        `application/vnd.apache.thrift.binary`,
        `application/vnd.apache.thrift.compact`,
        `application/vnd.apache.thrift.json`,
        `application/vnd.api+json`,
        `application/vnd.apothekende.reservation+json`,
        `application/vnd.apple.installer+xml`,
        `application/vnd.apple.mpegurl`,
        `application/vnd.apple.pkpass`,
        `application/vnd.arastra.swi`,
        `application/vnd.aristanetworks.swi`,
        `application/vnd.artsquare`,
        `application/vnd.astraea-software.iota`,
        `application/vnd.audiograph`,
        `application/vnd.autopackage`,
        `application/vnd.avalon+json`,
        `application/vnd.avistar+xml`,
        `application/vnd.balsamiq.bmml+xml`,
        `application/vnd.balsamiq.bmpr`,
        `application/vnd.bbf.usp.msg`,
        `application/vnd.bbf.usp.msg+json`,
        `application/vnd.bekitzur-stech+json`,
        `application/vnd.bint.med-content`,
        `application/vnd.biopax.rdf+xml`,
        `application/vnd.blink-idb-value-wrapper`,
        `application/vnd.blueice.multipass`,
        `application/vnd.bluetooth.ep.oob`,
        `application/vnd.bluetooth.le.oob`,
        `application/vnd.bmi`,
        `application/vnd.businessobjects`,
        `application/vnd.cab-jscript`,
        `application/vnd.canon-cpdl`,
        `application/vnd.canon-lips`,
        `application/vnd.capasystems-pg+json`,
        `application/vnd.cendio.thinlinc.clientconf`,
        `application/vnd.century-systems.tcp_stream`,
        `application/vnd.chemdraw+xml`,
        `application/vnd.chess-pgn`,
        `application/vnd.chipnuts.karaoke-mmd`,
        `application/vnd.cinderella`,
        `application/vnd.cirpack.isdn-ext`,
        `application/vnd.citationstyles.style+xml`,
        `application/vnd.claymore`,
        `application/vnd.cloanto.rp9`,
        `application/vnd.clonk.c4group`,
        `application/vnd.cluetrust.cartomobile-config`,
        `application/vnd.cluetrust.cartomobile-config-pkg`,
        `application/vnd.coffeescript`,
        `application/vnd.collabio.xodocuments.document`,
        `application/vnd.collabio.xodocuments.document-template`,
        `application/vnd.collabio.xodocuments.presentation`,
        `application/vnd.collabio.xodocuments.presentation-template`,
        `application/vnd.collabio.xodocuments.spreadsheet`,
        `application/vnd.collabio.xodocuments.spreadsheet-template`,
        `application/vnd.collection+json`,
        `application/vnd.collection.doc+json`,
        `application/vnd.collection.next+json`,
        `application/vnd.comicbook+zip`,
        `application/vnd.comicbook-rar`,
        `application/vnd.commerce-battelle`,
        `application/vnd.commonspace`,
        `application/vnd.contact.cmsg`,
        `application/vnd.coreos.ignition+json`,
        `application/vnd.cosmocaller`,
        `application/vnd.crick.clicker`,
        `application/vnd.crick.clicker.keyboard`,
        `application/vnd.crick.clicker.palette`,
        `application/vnd.crick.clicker.template`,
        `application/vnd.crick.clicker.wordbank`
      )
    }
    object application_1 {
      val mainType: String = "application"
      lazy val `application/vnd.criticaltools.wbs+xml`: MediaType =
        new MediaType(mainType, "vnd.criticaltools.wbs+xml", Compressible, NotBinary, List("wbs"))
      lazy val `application/vnd.ctc-posml`: MediaType =
        new MediaType(mainType, "vnd.ctc-posml", Compressible, NotBinary, List("pml"))
      lazy val `application/vnd.ctct.ws+xml`: MediaType =
        new MediaType(mainType, "vnd.ctct.ws+xml", Compressible, NotBinary)
      lazy val `application/vnd.cups-pdf`: MediaType =
        new MediaType(mainType, "vnd.cups-pdf", Compressible, NotBinary)
      lazy val `application/vnd.cups-postscript`: MediaType =
        new MediaType(mainType, "vnd.cups-postscript", Compressible, NotBinary)
      lazy val `application/vnd.cups-ppd`: MediaType =
        new MediaType(mainType, "vnd.cups-ppd", Compressible, NotBinary, List("ppd"))
      lazy val `application/vnd.cups-raster`: MediaType =
        new MediaType(mainType, "vnd.cups-raster", Compressible, NotBinary)
      lazy val `application/vnd.cups-raw`: MediaType =
        new MediaType(mainType, "vnd.cups-raw", Compressible, NotBinary)
      lazy val `application/vnd.curl`: MediaType =
        new MediaType(mainType, "vnd.curl", Compressible, NotBinary)
      lazy val `application/vnd.curl.car`: MediaType =
        new MediaType(mainType, "vnd.curl.car", Compressible, NotBinary, List("car"))
      lazy val `application/vnd.curl.pcurl`: MediaType =
        new MediaType(mainType, "vnd.curl.pcurl", Compressible, NotBinary, List("pcurl"))
      lazy val `application/vnd.cyan.dean.root+xml`: MediaType =
        new MediaType(mainType, "vnd.cyan.dean.root+xml", Compressible, NotBinary)
      lazy val `application/vnd.cybank`: MediaType =
        new MediaType(mainType, "vnd.cybank", Compressible, NotBinary)
      lazy val `application/vnd.d2l.coursepackage1p0+zip`: MediaType =
        new MediaType(mainType, "vnd.d2l.coursepackage1p0+zip", Compressible, NotBinary)
      lazy val `application/vnd.dart`: MediaType =
        new MediaType(mainType, "vnd.dart", Compressible, NotBinary, List("dart"))
      lazy val `application/vnd.data-vision.rdz`: MediaType =
        new MediaType(mainType, "vnd.data-vision.rdz", Compressible, NotBinary, List("rdz"))
      lazy val `application/vnd.datapackage+json`: MediaType =
        new MediaType(mainType, "vnd.datapackage+json", Compressible, NotBinary)
      lazy val `application/vnd.dataresource+json`: MediaType =
        new MediaType(mainType, "vnd.dataresource+json", Compressible, NotBinary)
      lazy val `application/vnd.debian.binary-package`: MediaType =
        new MediaType(mainType, "vnd.debian.binary-package", Compressible, NotBinary)
      lazy val `application/vnd.dece.data`: MediaType = new MediaType(
        mainType,
        "vnd.dece.data",
        Compressible,
        NotBinary,
        List("uvf", "uvvf", "uvd", "uvvd"))
      lazy val `application/vnd.dece.ttml+xml`: MediaType =
        new MediaType(mainType, "vnd.dece.ttml+xml", Compressible, NotBinary, List("uvt", "uvvt"))
      lazy val `application/vnd.dece.unspecified`: MediaType = new MediaType(
        mainType,
        "vnd.dece.unspecified",
        Compressible,
        NotBinary,
        List("uvx", "uvvx"))
      lazy val `application/vnd.dece.zip`: MediaType =
        new MediaType(mainType, "vnd.dece.zip", Compressible, NotBinary, List("uvz", "uvvz"))
      lazy val `application/vnd.denovo.fcselayout-link`: MediaType = new MediaType(
        mainType,
        "vnd.denovo.fcselayout-link",
        Compressible,
        NotBinary,
        List("fe_launch"))
      lazy val `application/vnd.desmume-movie`: MediaType =
        new MediaType(mainType, "vnd.desmume-movie", Compressible, NotBinary)
      lazy val `application/vnd.desmume.movie`: MediaType =
        new MediaType(mainType, "vnd.desmume.movie", Compressible, NotBinary)
      lazy val `application/vnd.dir-bi.plate-dl-nosuffix`: MediaType =
        new MediaType(mainType, "vnd.dir-bi.plate-dl-nosuffix", Compressible, NotBinary)
      lazy val `application/vnd.dm.delegation+xml`: MediaType =
        new MediaType(mainType, "vnd.dm.delegation+xml", Compressible, NotBinary)
      lazy val `application/vnd.dna`: MediaType =
        new MediaType(mainType, "vnd.dna", Compressible, NotBinary, List("dna"))
      lazy val `application/vnd.document+json`: MediaType =
        new MediaType(mainType, "vnd.document+json", Compressible, NotBinary)
      lazy val `application/vnd.dolby.mlp`: MediaType =
        new MediaType(mainType, "vnd.dolby.mlp", Compressible, NotBinary, List("mlp"))
      lazy val `application/vnd.dolby.mobile.1`: MediaType =
        new MediaType(mainType, "vnd.dolby.mobile.1", Compressible, NotBinary)
      lazy val `application/vnd.dolby.mobile.2`: MediaType =
        new MediaType(mainType, "vnd.dolby.mobile.2", Compressible, NotBinary)
      lazy val `application/vnd.doremir.scorecloud-binary-document`: MediaType =
        new MediaType(mainType, "vnd.doremir.scorecloud-binary-document", Compressible, NotBinary)
      lazy val `application/vnd.dpgraph`: MediaType =
        new MediaType(mainType, "vnd.dpgraph", Compressible, NotBinary, List("dpg"))
      lazy val `application/vnd.dreamfactory`: MediaType =
        new MediaType(mainType, "vnd.dreamfactory", Compressible, NotBinary, List("dfac"))
      lazy val `application/vnd.drive+json`: MediaType =
        new MediaType(mainType, "vnd.drive+json", Compressible, NotBinary)
      lazy val `application/vnd.ds-keypoint`: MediaType =
        new MediaType(mainType, "vnd.ds-keypoint", Compressible, NotBinary, List("kpxx"))
      lazy val `application/vnd.dtg.local`: MediaType =
        new MediaType(mainType, "vnd.dtg.local", Compressible, NotBinary)
      lazy val `application/vnd.dtg.local.flash`: MediaType =
        new MediaType(mainType, "vnd.dtg.local.flash", Compressible, NotBinary)
      lazy val `application/vnd.dtg.local.html`: MediaType =
        new MediaType(mainType, "vnd.dtg.local.html", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ait`: MediaType =
        new MediaType(mainType, "vnd.dvb.ait", Compressible, NotBinary, List("ait"))
      lazy val `application/vnd.dvb.dvbj`: MediaType =
        new MediaType(mainType, "vnd.dvb.dvbj", Compressible, NotBinary)
      lazy val `application/vnd.dvb.esgcontainer`: MediaType =
        new MediaType(mainType, "vnd.dvb.esgcontainer", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ipdcdftnotifaccess`: MediaType =
        new MediaType(mainType, "vnd.dvb.ipdcdftnotifaccess", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ipdcesgaccess`: MediaType =
        new MediaType(mainType, "vnd.dvb.ipdcesgaccess", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ipdcesgaccess2`: MediaType =
        new MediaType(mainType, "vnd.dvb.ipdcesgaccess2", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ipdcesgpdd`: MediaType =
        new MediaType(mainType, "vnd.dvb.ipdcesgpdd", Compressible, NotBinary)
      lazy val `application/vnd.dvb.ipdcroaming`: MediaType =
        new MediaType(mainType, "vnd.dvb.ipdcroaming", Compressible, NotBinary)
      lazy val `application/vnd.dvb.iptv.alfec-base`: MediaType =
        new MediaType(mainType, "vnd.dvb.iptv.alfec-base", Compressible, NotBinary)
      lazy val `application/vnd.dvb.iptv.alfec-enhancement`: MediaType =
        new MediaType(mainType, "vnd.dvb.iptv.alfec-enhancement", Compressible, NotBinary)
      lazy val `application/vnd.dvb.notif-aggregate-root+xml`: MediaType =
        new MediaType(mainType, "vnd.dvb.notif-aggregate-root+xml", Compressible, NotBinary)
      lazy val `application/vnd.dvb.notif-container+xml`: MediaType =
        new MediaType(mainType, "vnd.dvb.notif-container+xml", Compressible, NotBinary)
      lazy val `application/vnd.dvb.notif-generic+xml`: MediaType =
        new MediaType(mainType, "vnd.dvb.notif-generic+xml", Compressible, NotBinary)
      lazy val `application/vnd.dvb.notif-ia-msglist+xml`: MediaType =
        new MediaType(mainType, "vnd.dvb.notif-ia-msglist+xml", Compressible, NotBinary)
      lazy val `application/vnd.dvb.notif-ia-registration-request+xml`: MediaType = new MediaType(
        mainType,
        "vnd.dvb.notif-ia-registration-request+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.dvb.notif-ia-registration-response+xml`: MediaType = new MediaType(
        mainType,
        "vnd.dvb.notif-ia-registration-response+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.dvb.notif-init+xml`: MediaType =
        new MediaType(mainType, "vnd.dvb.notif-init+xml", Compressible, NotBinary)
      lazy val `application/vnd.dvb.pfr`: MediaType =
        new MediaType(mainType, "vnd.dvb.pfr", Compressible, NotBinary)
      lazy val `application/vnd.dvb.service`: MediaType =
        new MediaType(mainType, "vnd.dvb.service", Compressible, NotBinary, List("svc"))
      lazy val `application/vnd.dxr`: MediaType =
        new MediaType(mainType, "vnd.dxr", Compressible, NotBinary)
      lazy val `application/vnd.dynageo`: MediaType =
        new MediaType(mainType, "vnd.dynageo", Compressible, NotBinary, List("geo"))
      lazy val `application/vnd.dzr`: MediaType =
        new MediaType(mainType, "vnd.dzr", Compressible, NotBinary)
      lazy val `application/vnd.easykaraoke.cdgdownload`: MediaType =
        new MediaType(mainType, "vnd.easykaraoke.cdgdownload", Compressible, NotBinary)
      lazy val `application/vnd.ecdis-update`: MediaType =
        new MediaType(mainType, "vnd.ecdis-update", Compressible, NotBinary)
      lazy val `application/vnd.ecip.rlp`: MediaType =
        new MediaType(mainType, "vnd.ecip.rlp", Compressible, NotBinary)
      lazy val `application/vnd.ecowin.chart`: MediaType =
        new MediaType(mainType, "vnd.ecowin.chart", Compressible, NotBinary, List("mag"))
      lazy val `application/vnd.ecowin.filerequest`: MediaType =
        new MediaType(mainType, "vnd.ecowin.filerequest", Compressible, NotBinary)
      lazy val `application/vnd.ecowin.fileupdate`: MediaType =
        new MediaType(mainType, "vnd.ecowin.fileupdate", Compressible, NotBinary)
      lazy val `application/vnd.ecowin.series`: MediaType =
        new MediaType(mainType, "vnd.ecowin.series", Compressible, NotBinary)
      lazy val `application/vnd.ecowin.seriesrequest`: MediaType =
        new MediaType(mainType, "vnd.ecowin.seriesrequest", Compressible, NotBinary)
      lazy val `application/vnd.ecowin.seriesupdate`: MediaType =
        new MediaType(mainType, "vnd.ecowin.seriesupdate", Compressible, NotBinary)
      lazy val `application/vnd.efi.img`: MediaType =
        new MediaType(mainType, "vnd.efi.img", Compressible, NotBinary)
      lazy val `application/vnd.efi.iso`: MediaType =
        new MediaType(mainType, "vnd.efi.iso", Compressible, NotBinary)
      lazy val `application/vnd.emclient.accessrequest+xml`: MediaType =
        new MediaType(mainType, "vnd.emclient.accessrequest+xml", Compressible, NotBinary)
      lazy val `application/vnd.enliven`: MediaType =
        new MediaType(mainType, "vnd.enliven", Compressible, NotBinary, List("nml"))
      lazy val `application/vnd.enphase.envoy`: MediaType =
        new MediaType(mainType, "vnd.enphase.envoy", Compressible, NotBinary)
      lazy val `application/vnd.eprints.data+xml`: MediaType =
        new MediaType(mainType, "vnd.eprints.data+xml", Compressible, NotBinary)
      lazy val `application/vnd.epson.esf`: MediaType =
        new MediaType(mainType, "vnd.epson.esf", Compressible, NotBinary, List("esf"))
      lazy val `application/vnd.epson.msf`: MediaType =
        new MediaType(mainType, "vnd.epson.msf", Compressible, NotBinary, List("msf"))
      lazy val `application/vnd.epson.quickanime`: MediaType =
        new MediaType(mainType, "vnd.epson.quickanime", Compressible, NotBinary, List("qam"))
      lazy val `application/vnd.epson.salt`: MediaType =
        new MediaType(mainType, "vnd.epson.salt", Compressible, NotBinary, List("slt"))
      lazy val `application/vnd.epson.ssf`: MediaType =
        new MediaType(mainType, "vnd.epson.ssf", Compressible, NotBinary, List("ssf"))
      lazy val `application/vnd.ericsson.quickcall`: MediaType =
        new MediaType(mainType, "vnd.ericsson.quickcall", Compressible, NotBinary)
      lazy val `application/vnd.espass-espass+zip`: MediaType =
        new MediaType(mainType, "vnd.espass-espass+zip", Compressible, NotBinary)
      lazy val `application/vnd.eszigno3+xml`: MediaType =
        new MediaType(mainType, "vnd.eszigno3+xml", Compressible, NotBinary, List("es3", "et3"))
      lazy val `application/vnd.etsi.aoc+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.aoc+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.asic-e+zip`: MediaType =
        new MediaType(mainType, "vnd.etsi.asic-e+zip", Compressible, NotBinary)
      lazy val `application/vnd.etsi.asic-s+zip`: MediaType =
        new MediaType(mainType, "vnd.etsi.asic-s+zip", Compressible, NotBinary)
      lazy val `application/vnd.etsi.cug+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.cug+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvcommand+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvcommand+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvdiscovery+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvdiscovery+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvprofile+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvprofile+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvsad-bc+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvsad-bc+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvsad-cod+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvsad-cod+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvsad-npvr+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvsad-npvr+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvservice+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvservice+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvsync+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvsync+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.iptvueprofile+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.iptvueprofile+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.mcid+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.mcid+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.mheg5`: MediaType =
        new MediaType(mainType, "vnd.etsi.mheg5", Compressible, NotBinary)
      lazy val `application/vnd.etsi.overload-control-policy-dataset+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.etsi.overload-control-policy-dataset+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.etsi.pstn+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.pstn+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.sci+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.sci+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.simservs+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.simservs+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.timestamp-token`: MediaType =
        new MediaType(mainType, "vnd.etsi.timestamp-token", Compressible, NotBinary)
      lazy val `application/vnd.etsi.tsl+xml`: MediaType =
        new MediaType(mainType, "vnd.etsi.tsl+xml", Compressible, NotBinary)
      lazy val `application/vnd.etsi.tsl.der`: MediaType =
        new MediaType(mainType, "vnd.etsi.tsl.der", Compressible, NotBinary)
      lazy val `application/vnd.eudora.data`: MediaType =
        new MediaType(mainType, "vnd.eudora.data", Compressible, NotBinary)
      lazy val `application/vnd.evolv.ecig.profile`: MediaType =
        new MediaType(mainType, "vnd.evolv.ecig.profile", Compressible, NotBinary)
      lazy val `application/vnd.evolv.ecig.settings`: MediaType =
        new MediaType(mainType, "vnd.evolv.ecig.settings", Compressible, NotBinary)
      lazy val `application/vnd.evolv.ecig.theme`: MediaType =
        new MediaType(mainType, "vnd.evolv.ecig.theme", Compressible, NotBinary)
      lazy val `application/vnd.ezpix-album`: MediaType =
        new MediaType(mainType, "vnd.ezpix-album", Compressible, NotBinary, List("ez2"))
      lazy val `application/vnd.ezpix-package`: MediaType =
        new MediaType(mainType, "vnd.ezpix-package", Compressible, NotBinary, List("ez3"))
      lazy val `application/vnd.f-secure.mobile`: MediaType =
        new MediaType(mainType, "vnd.f-secure.mobile", Compressible, NotBinary)
      lazy val `application/vnd.fastcopy-disk-image`: MediaType =
        new MediaType(mainType, "vnd.fastcopy-disk-image", Compressible, NotBinary)
      lazy val `application/vnd.fdf`: MediaType =
        new MediaType(mainType, "vnd.fdf", Compressible, NotBinary, List("fdf"))
      lazy val `application/vnd.fdsn.mseed`: MediaType =
        new MediaType(mainType, "vnd.fdsn.mseed", Compressible, NotBinary, List("mseed"))
      lazy val `application/vnd.fdsn.seed`: MediaType =
        new MediaType(mainType, "vnd.fdsn.seed", Compressible, NotBinary, List("seed", "dataless"))
      lazy val `application/vnd.ffsns`: MediaType =
        new MediaType(mainType, "vnd.ffsns", Compressible, NotBinary)
      lazy val `application/vnd.filmit.zfc`: MediaType =
        new MediaType(mainType, "vnd.filmit.zfc", Compressible, NotBinary)
      lazy val `application/vnd.fints`: MediaType =
        new MediaType(mainType, "vnd.fints", Compressible, NotBinary)
      lazy val `application/vnd.firemonkeys.cloudcell`: MediaType =
        new MediaType(mainType, "vnd.firemonkeys.cloudcell", Compressible, NotBinary)
      lazy val `application/vnd.flographit`: MediaType =
        new MediaType(mainType, "vnd.flographit", Compressible, NotBinary, List("gph"))
      lazy val `application/vnd.fluxtime.clip`: MediaType =
        new MediaType(mainType, "vnd.fluxtime.clip", Compressible, NotBinary, List("ftc"))
      lazy val `application/vnd.font-fontforge-sfd`: MediaType =
        new MediaType(mainType, "vnd.font-fontforge-sfd", Compressible, NotBinary)
      lazy val `application/vnd.framemaker`: MediaType = new MediaType(
        mainType,
        "vnd.framemaker",
        Compressible,
        NotBinary,
        List("fm", "frame", "maker", "book"))
      lazy val `application/vnd.frogans.fnc`: MediaType =
        new MediaType(mainType, "vnd.frogans.fnc", Compressible, NotBinary, List("fnc"))
      lazy val `application/vnd.frogans.ltf`: MediaType =
        new MediaType(mainType, "vnd.frogans.ltf", Compressible, NotBinary, List("ltf"))
      lazy val `application/vnd.fsc.weblaunch`: MediaType =
        new MediaType(mainType, "vnd.fsc.weblaunch", Compressible, NotBinary, List("fsc"))
      lazy val `application/vnd.fujitsu.oasys`: MediaType =
        new MediaType(mainType, "vnd.fujitsu.oasys", Compressible, NotBinary, List("oas"))
      lazy val `application/vnd.fujitsu.oasys2`: MediaType =
        new MediaType(mainType, "vnd.fujitsu.oasys2", Compressible, NotBinary, List("oa2"))
      lazy val `application/vnd.fujitsu.oasys3`: MediaType =
        new MediaType(mainType, "vnd.fujitsu.oasys3", Compressible, NotBinary, List("oa3"))
      lazy val `application/vnd.fujitsu.oasysgp`: MediaType =
        new MediaType(mainType, "vnd.fujitsu.oasysgp", Compressible, NotBinary, List("fg5"))
      lazy val `application/vnd.fujitsu.oasysprs`: MediaType =
        new MediaType(mainType, "vnd.fujitsu.oasysprs", Compressible, NotBinary, List("bh2"))
      lazy val `application/vnd.fujixerox.art-ex`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.art-ex", Compressible, NotBinary)
      lazy val `application/vnd.fujixerox.art4`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.art4", Compressible, NotBinary)
      lazy val `application/vnd.fujixerox.ddd`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.ddd", Compressible, NotBinary, List("ddd"))
      lazy val `application/vnd.fujixerox.docuworks`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.docuworks", Compressible, NotBinary, List("xdw"))
      lazy val `application/vnd.fujixerox.docuworks.binder`: MediaType = new MediaType(
        mainType,
        "vnd.fujixerox.docuworks.binder",
        Compressible,
        NotBinary,
        List("xbd"))
      lazy val `application/vnd.fujixerox.docuworks.container`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.docuworks.container", Compressible, NotBinary)
      lazy val `application/vnd.fujixerox.hbpl`: MediaType =
        new MediaType(mainType, "vnd.fujixerox.hbpl", Compressible, NotBinary)
      lazy val `application/vnd.fut-misnet`: MediaType =
        new MediaType(mainType, "vnd.fut-misnet", Compressible, NotBinary)
      lazy val `application/vnd.fuzzysheet`: MediaType =
        new MediaType(mainType, "vnd.fuzzysheet", Compressible, NotBinary, List("fzs"))
      lazy val `application/vnd.genomatix.tuxedo`: MediaType =
        new MediaType(mainType, "vnd.genomatix.tuxedo", Compressible, NotBinary, List("txd"))
      lazy val `application/vnd.geo+json`: MediaType =
        new MediaType(mainType, "vnd.geo+json", Compressible, NotBinary)
      lazy val `application/vnd.geocube+xml`: MediaType =
        new MediaType(mainType, "vnd.geocube+xml", Compressible, NotBinary)
      lazy val `application/vnd.geogebra.file`: MediaType =
        new MediaType(mainType, "vnd.geogebra.file", Compressible, NotBinary, List("ggb"))
      lazy val `application/vnd.geogebra.tool`: MediaType =
        new MediaType(mainType, "vnd.geogebra.tool", Compressible, NotBinary, List("ggt"))
      lazy val `application/vnd.geometry-explorer`: MediaType = new MediaType(
        mainType,
        "vnd.geometry-explorer",
        Compressible,
        NotBinary,
        List("gex", "gre"))
      lazy val `application/vnd.geonext`: MediaType =
        new MediaType(mainType, "vnd.geonext", Compressible, NotBinary, List("gxt"))
      lazy val `application/vnd.geoplan`: MediaType =
        new MediaType(mainType, "vnd.geoplan", Compressible, NotBinary, List("g2w"))
      lazy val `application/vnd.geospace`: MediaType =
        new MediaType(mainType, "vnd.geospace", Compressible, NotBinary, List("g3w"))
      lazy val `application/vnd.gerber`: MediaType =
        new MediaType(mainType, "vnd.gerber", Compressible, NotBinary)
      lazy val `application/vnd.globalplatform.card-content-mgt`: MediaType =
        new MediaType(mainType, "vnd.globalplatform.card-content-mgt", Compressible, NotBinary)
      lazy val `application/vnd.globalplatform.card-content-mgt-response`: MediaType =
        new MediaType(
          mainType,
          "vnd.globalplatform.card-content-mgt-response",
          Compressible,
          NotBinary)
      lazy val `application/vnd.gmx`: MediaType =
        new MediaType(mainType, "vnd.gmx", Compressible, NotBinary, List("gmx"))
      lazy val `application/vnd.google-apps.document`: MediaType =
        new MediaType(mainType, "vnd.google-apps.document", Uncompressible, NotBinary, List("gdoc"))
      lazy val `application/vnd.google-apps.presentation`: MediaType = new MediaType(
        mainType,
        "vnd.google-apps.presentation",
        Uncompressible,
        NotBinary,
        List("gslides"))
      lazy val `application/vnd.google-apps.spreadsheet`: MediaType = new MediaType(
        mainType,
        "vnd.google-apps.spreadsheet",
        Uncompressible,
        NotBinary,
        List("gsheet"))
      lazy val `application/vnd.google-earth.kml+xml`: MediaType =
        new MediaType(mainType, "vnd.google-earth.kml+xml", Compressible, NotBinary, List("kml"))
      lazy val `application/vnd.google-earth.kmz`: MediaType =
        new MediaType(mainType, "vnd.google-earth.kmz", Uncompressible, Binary, List("kmz"))
      lazy val `application/vnd.gov.sk.e-form+xml`: MediaType =
        new MediaType(mainType, "vnd.gov.sk.e-form+xml", Compressible, NotBinary)
      lazy val `application/vnd.gov.sk.e-form+zip`: MediaType =
        new MediaType(mainType, "vnd.gov.sk.e-form+zip", Compressible, NotBinary)
      lazy val `application/vnd.gov.sk.xmldatacontainer+xml`: MediaType =
        new MediaType(mainType, "vnd.gov.sk.xmldatacontainer+xml", Compressible, NotBinary)
      lazy val `application/vnd.grafeq`: MediaType =
        new MediaType(mainType, "vnd.grafeq", Compressible, NotBinary, List("gqf", "gqs"))
      lazy val `application/vnd.gridmp`: MediaType =
        new MediaType(mainType, "vnd.gridmp", Compressible, NotBinary)
      lazy val `application/vnd.groove-account`: MediaType =
        new MediaType(mainType, "vnd.groove-account", Compressible, NotBinary, List("gac"))
      lazy val `application/vnd.groove-help`: MediaType =
        new MediaType(mainType, "vnd.groove-help", Compressible, NotBinary, List("ghf"))
      lazy val `application/vnd.groove-identity-message`: MediaType =
        new MediaType(mainType, "vnd.groove-identity-message", Compressible, NotBinary, List("gim"))
      lazy val `application/vnd.groove-injector`: MediaType =
        new MediaType(mainType, "vnd.groove-injector", Compressible, NotBinary, List("grv"))
      lazy val `application/vnd.groove-tool-message`: MediaType =
        new MediaType(mainType, "vnd.groove-tool-message", Compressible, NotBinary, List("gtm"))
      lazy val `application/vnd.groove-tool-template`: MediaType =
        new MediaType(mainType, "vnd.groove-tool-template", Compressible, NotBinary, List("tpl"))
      lazy val `application/vnd.groove-vcard`: MediaType =
        new MediaType(mainType, "vnd.groove-vcard", Compressible, NotBinary, List("vcg"))
      lazy val `application/vnd.hal+json`: MediaType =
        new MediaType(mainType, "vnd.hal+json", Compressible, NotBinary)
      lazy val `application/vnd.hal+xml`: MediaType =
        new MediaType(mainType, "vnd.hal+xml", Compressible, NotBinary, List("hal"))
      lazy val `application/vnd.handheld-entertainment+xml`: MediaType = new MediaType(
        mainType,
        "vnd.handheld-entertainment+xml",
        Compressible,
        NotBinary,
        List("zmm"))
      lazy val `application/vnd.hbci`: MediaType =
        new MediaType(mainType, "vnd.hbci", Compressible, NotBinary, List("hbci"))
      lazy val `application/vnd.hc+json`: MediaType =
        new MediaType(mainType, "vnd.hc+json", Compressible, NotBinary)
      lazy val `application/vnd.hcl-bireports`: MediaType =
        new MediaType(mainType, "vnd.hcl-bireports", Compressible, NotBinary)
      lazy val `application/vnd.hdt`: MediaType =
        new MediaType(mainType, "vnd.hdt", Compressible, NotBinary)
      lazy val `application/vnd.heroku+json`: MediaType =
        new MediaType(mainType, "vnd.heroku+json", Compressible, NotBinary)
      lazy val `application/vnd.hhe.lesson-player`: MediaType =
        new MediaType(mainType, "vnd.hhe.lesson-player", Compressible, NotBinary, List("les"))
      lazy val `application/vnd.hp-hpgl`: MediaType =
        new MediaType(mainType, "vnd.hp-hpgl", Compressible, NotBinary, List("hpgl"))
      lazy val `application/vnd.hp-hpid`: MediaType =
        new MediaType(mainType, "vnd.hp-hpid", Compressible, NotBinary, List("hpid"))
      lazy val `application/vnd.hp-hps`: MediaType =
        new MediaType(mainType, "vnd.hp-hps", Compressible, NotBinary, List("hps"))
      lazy val `application/vnd.hp-jlyt`: MediaType =
        new MediaType(mainType, "vnd.hp-jlyt", Compressible, NotBinary, List("jlt"))
      lazy val `application/vnd.hp-pcl`: MediaType =
        new MediaType(mainType, "vnd.hp-pcl", Compressible, NotBinary, List("pcl"))
      lazy val `application/vnd.hp-pclxl`: MediaType =
        new MediaType(mainType, "vnd.hp-pclxl", Compressible, NotBinary, List("pclxl"))
      lazy val `application/vnd.httphone`: MediaType =
        new MediaType(mainType, "vnd.httphone", Compressible, NotBinary)
      lazy val `application/vnd.hydrostatix.sof-data`: MediaType = new MediaType(
        mainType,
        "vnd.hydrostatix.sof-data",
        Compressible,
        NotBinary,
        List("sfd-hdstx"))
      lazy val `application/vnd.hyper+json`: MediaType =
        new MediaType(mainType, "vnd.hyper+json", Compressible, NotBinary)
      lazy val `application/vnd.hyper-item+json`: MediaType =
        new MediaType(mainType, "vnd.hyper-item+json", Compressible, NotBinary)
      lazy val `application/vnd.hyperdrive+json`: MediaType =
        new MediaType(mainType, "vnd.hyperdrive+json", Compressible, NotBinary)
      lazy val `application/vnd.hzn-3d-crossword`: MediaType =
        new MediaType(mainType, "vnd.hzn-3d-crossword", Compressible, NotBinary)
      lazy val `application/vnd.ibm.afplinedata`: MediaType =
        new MediaType(mainType, "vnd.ibm.afplinedata", Compressible, NotBinary)
      lazy val `application/vnd.ibm.electronic-media`: MediaType =
        new MediaType(mainType, "vnd.ibm.electronic-media", Compressible, NotBinary)
      lazy val `application/vnd.ibm.minipay`: MediaType =
        new MediaType(mainType, "vnd.ibm.minipay", Compressible, NotBinary, List("mpy"))
      lazy val `application/vnd.ibm.modcap`: MediaType = new MediaType(
        mainType,
        "vnd.ibm.modcap",
        Compressible,
        NotBinary,
        List("afp", "listafp", "list3820"))
      lazy val `application/vnd.ibm.rights-management`: MediaType =
        new MediaType(mainType, "vnd.ibm.rights-management", Compressible, NotBinary, List("irm"))
      lazy val `application/vnd.ibm.secure-container`: MediaType =
        new MediaType(mainType, "vnd.ibm.secure-container", Compressible, NotBinary, List("sc"))
      lazy val `application/vnd.iccprofile`: MediaType =
        new MediaType(mainType, "vnd.iccprofile", Compressible, NotBinary, List("icc", "icm"))
      lazy val `application/vnd.ieee.1905`: MediaType =
        new MediaType(mainType, "vnd.ieee.1905", Compressible, NotBinary)
      lazy val `application/vnd.igloader`: MediaType =
        new MediaType(mainType, "vnd.igloader", Compressible, NotBinary, List("igl"))
      lazy val `application/vnd.imagemeter.folder+zip`: MediaType =
        new MediaType(mainType, "vnd.imagemeter.folder+zip", Compressible, NotBinary)
      lazy val `application/vnd.imagemeter.image+zip`: MediaType =
        new MediaType(mainType, "vnd.imagemeter.image+zip", Compressible, NotBinary)
      lazy val `application/vnd.immervision-ivp`: MediaType =
        new MediaType(mainType, "vnd.immervision-ivp", Compressible, NotBinary, List("ivp"))
      lazy val `application/vnd.immervision-ivu`: MediaType =
        new MediaType(mainType, "vnd.immervision-ivu", Compressible, NotBinary, List("ivu"))
      lazy val `application/vnd.ims.imsccv1p1`: MediaType =
        new MediaType(mainType, "vnd.ims.imsccv1p1", Compressible, NotBinary)
      lazy val `application/vnd.ims.imsccv1p2`: MediaType =
        new MediaType(mainType, "vnd.ims.imsccv1p2", Compressible, NotBinary)
      lazy val `application/vnd.ims.imsccv1p3`: MediaType =
        new MediaType(mainType, "vnd.ims.imsccv1p3", Compressible, NotBinary)
      lazy val `application/vnd.ims.lis.v2.result+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lis.v2.result+json", Compressible, NotBinary)
      lazy val `application/vnd.ims.lti.v2.toolconsumerprofile+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lti.v2.toolconsumerprofile+json", Compressible, NotBinary)
      lazy val `application/vnd.ims.lti.v2.toolproxy+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lti.v2.toolproxy+json", Compressible, NotBinary)
      lazy val `application/vnd.ims.lti.v2.toolproxy.id+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lti.v2.toolproxy.id+json", Compressible, NotBinary)
      lazy val `application/vnd.ims.lti.v2.toolsettings+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lti.v2.toolsettings+json", Compressible, NotBinary)
      lazy val `application/vnd.ims.lti.v2.toolsettings.simple+json`: MediaType =
        new MediaType(mainType, "vnd.ims.lti.v2.toolsettings.simple+json", Compressible, NotBinary)
      lazy val `application/vnd.informedcontrol.rms+xml`: MediaType =
        new MediaType(mainType, "vnd.informedcontrol.rms+xml", Compressible, NotBinary)
      lazy val `application/vnd.informix-visionary`: MediaType =
        new MediaType(mainType, "vnd.informix-visionary", Compressible, NotBinary)
      lazy val `application/vnd.infotech.project`: MediaType =
        new MediaType(mainType, "vnd.infotech.project", Compressible, NotBinary)
      lazy val `application/vnd.infotech.project+xml`: MediaType =
        new MediaType(mainType, "vnd.infotech.project+xml", Compressible, NotBinary)
      lazy val `application/vnd.innopath.wamp.notification`: MediaType =
        new MediaType(mainType, "vnd.innopath.wamp.notification", Compressible, NotBinary)
      lazy val `application/vnd.insors.igm`: MediaType =
        new MediaType(mainType, "vnd.insors.igm", Compressible, NotBinary, List("igm"))
      lazy val `application/vnd.intercon.formnet`: MediaType =
        new MediaType(mainType, "vnd.intercon.formnet", Compressible, NotBinary, List("xpw", "xpx"))
      lazy val `application/vnd.intergeo`: MediaType =
        new MediaType(mainType, "vnd.intergeo", Compressible, NotBinary, List("i2g"))
      lazy val `application/vnd.intertrust.digibox`: MediaType =
        new MediaType(mainType, "vnd.intertrust.digibox", Compressible, NotBinary)
      lazy val `application/vnd.intertrust.nncp`: MediaType =
        new MediaType(mainType, "vnd.intertrust.nncp", Compressible, NotBinary)
      lazy val `application/vnd.intu.qbo`: MediaType =
        new MediaType(mainType, "vnd.intu.qbo", Compressible, NotBinary, List("qbo"))
      lazy val `application/vnd.intu.qfx`: MediaType =
        new MediaType(mainType, "vnd.intu.qfx", Compressible, NotBinary, List("qfx"))
      lazy val `application/vnd.iptc.g2.catalogitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.catalogitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.conceptitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.conceptitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.knowledgeitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.knowledgeitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.newsitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.newsitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.newsmessage+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.newsmessage+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.packageitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.packageitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.iptc.g2.planningitem+xml`: MediaType =
        new MediaType(mainType, "vnd.iptc.g2.planningitem+xml", Compressible, NotBinary)
      lazy val `application/vnd.ipunplugged.rcprofile`: MediaType = new MediaType(
        mainType,
        "vnd.ipunplugged.rcprofile",
        Compressible,
        NotBinary,
        List("rcprofile"))
      lazy val `application/vnd.irepository.package+xml`: MediaType =
        new MediaType(mainType, "vnd.irepository.package+xml", Compressible, NotBinary, List("irp"))
      lazy val `application/vnd.is-xpr`: MediaType =
        new MediaType(mainType, "vnd.is-xpr", Compressible, NotBinary, List("xpr"))
      lazy val `application/vnd.isac.fcs`: MediaType =
        new MediaType(mainType, "vnd.isac.fcs", Compressible, NotBinary, List("fcs"))
      lazy val `application/vnd.jam`: MediaType =
        new MediaType(mainType, "vnd.jam", Compressible, NotBinary, List("jam"))
      lazy val `application/vnd.japannet-directory-service`: MediaType =
        new MediaType(mainType, "vnd.japannet-directory-service", Compressible, NotBinary)
      lazy val `application/vnd.japannet-jpnstore-wakeup`: MediaType =
        new MediaType(mainType, "vnd.japannet-jpnstore-wakeup", Compressible, NotBinary)
      lazy val `application/vnd.japannet-payment-wakeup`: MediaType =
        new MediaType(mainType, "vnd.japannet-payment-wakeup", Compressible, NotBinary)
      lazy val `application/vnd.japannet-registration`: MediaType =
        new MediaType(mainType, "vnd.japannet-registration", Compressible, NotBinary)
      lazy val `application/vnd.japannet-registration-wakeup`: MediaType =
        new MediaType(mainType, "vnd.japannet-registration-wakeup", Compressible, NotBinary)
      lazy val `application/vnd.japannet-setstore-wakeup`: MediaType =
        new MediaType(mainType, "vnd.japannet-setstore-wakeup", Compressible, NotBinary)
      lazy val `application/vnd.japannet-verification`: MediaType =
        new MediaType(mainType, "vnd.japannet-verification", Compressible, NotBinary)
      lazy val `application/vnd.japannet-verification-wakeup`: MediaType =
        new MediaType(mainType, "vnd.japannet-verification-wakeup", Compressible, NotBinary)
      lazy val `application/vnd.jcp.javame.midlet-rms`: MediaType =
        new MediaType(mainType, "vnd.jcp.javame.midlet-rms", Compressible, NotBinary, List("rms"))
      lazy val `application/vnd.jisp`: MediaType =
        new MediaType(mainType, "vnd.jisp", Compressible, NotBinary, List("jisp"))
      lazy val `application/vnd.joost.joda-archive`: MediaType =
        new MediaType(mainType, "vnd.joost.joda-archive", Compressible, NotBinary, List("joda"))
      lazy val `application/vnd.jsk.isdn-ngn`: MediaType =
        new MediaType(mainType, "vnd.jsk.isdn-ngn", Compressible, NotBinary)
      lazy val `application/vnd.kahootz`: MediaType =
        new MediaType(mainType, "vnd.kahootz", Compressible, NotBinary, List("ktz", "ktr"))
      lazy val `application/vnd.kde.karbon`: MediaType =
        new MediaType(mainType, "vnd.kde.karbon", Compressible, NotBinary, List("karbon"))
      lazy val `application/vnd.kde.kchart`: MediaType =
        new MediaType(mainType, "vnd.kde.kchart", Compressible, NotBinary, List("chrt"))
      lazy val `application/vnd.kde.kformula`: MediaType =
        new MediaType(mainType, "vnd.kde.kformula", Compressible, NotBinary, List("kfo"))
      lazy val `application/vnd.kde.kivio`: MediaType =
        new MediaType(mainType, "vnd.kde.kivio", Compressible, NotBinary, List("flw"))
      lazy val `application/vnd.kde.kontour`: MediaType =
        new MediaType(mainType, "vnd.kde.kontour", Compressible, NotBinary, List("kon"))
      lazy val `application/vnd.kde.kpresenter`: MediaType =
        new MediaType(mainType, "vnd.kde.kpresenter", Compressible, NotBinary, List("kpr", "kpt"))
      lazy val `application/vnd.kde.kspread`: MediaType =
        new MediaType(mainType, "vnd.kde.kspread", Compressible, NotBinary, List("ksp"))
      lazy val `application/vnd.kde.kword`: MediaType =
        new MediaType(mainType, "vnd.kde.kword", Compressible, NotBinary, List("kwd", "kwt"))
      lazy val `application/vnd.kenameaapp`: MediaType =
        new MediaType(mainType, "vnd.kenameaapp", Compressible, NotBinary, List("htke"))
      lazy val `application/vnd.kidspiration`: MediaType =
        new MediaType(mainType, "vnd.kidspiration", Compressible, NotBinary, List("kia"))
      lazy val `application/vnd.kinar`: MediaType =
        new MediaType(mainType, "vnd.kinar", Compressible, NotBinary, List("kne", "knp"))
      lazy val `application/vnd.koan`: MediaType = new MediaType(
        mainType,
        "vnd.koan",
        Compressible,
        NotBinary,
        List("skp", "skd", "skt", "skm"))
      lazy val `application/vnd.kodak-descriptor`: MediaType =
        new MediaType(mainType, "vnd.kodak-descriptor", Compressible, NotBinary, List("sse"))
      lazy val `application/vnd.las.las+json`: MediaType =
        new MediaType(mainType, "vnd.las.las+json", Compressible, NotBinary)
      lazy val `application/vnd.las.las+xml`: MediaType =
        new MediaType(mainType, "vnd.las.las+xml", Compressible, NotBinary, List("lasxml"))
      lazy val `application/vnd.liberty-request+xml`: MediaType =
        new MediaType(mainType, "vnd.liberty-request+xml", Compressible, NotBinary)
      lazy val `application/vnd.llamagraphics.life-balance.desktop`: MediaType = new MediaType(
        mainType,
        "vnd.llamagraphics.life-balance.desktop",
        Compressible,
        NotBinary,
        List("lbd"))
      lazy val `application/vnd.llamagraphics.life-balance.exchange+xml`: MediaType = new MediaType(
        mainType,
        "vnd.llamagraphics.life-balance.exchange+xml",
        Compressible,
        NotBinary,
        List("lbe"))
      lazy val `application/vnd.lotus-1-2-3`: MediaType =
        new MediaType(mainType, "vnd.lotus-1-2-3", Compressible, NotBinary, List("123"))
      lazy val `application/vnd.lotus-approach`: MediaType =
        new MediaType(mainType, "vnd.lotus-approach", Compressible, NotBinary, List("apr"))
      lazy val `application/vnd.lotus-freelance`: MediaType =
        new MediaType(mainType, "vnd.lotus-freelance", Compressible, NotBinary, List("pre"))
      lazy val `application/vnd.lotus-notes`: MediaType =
        new MediaType(mainType, "vnd.lotus-notes", Compressible, NotBinary, List("nsf"))
      lazy val `application/vnd.lotus-organizer`: MediaType =
        new MediaType(mainType, "vnd.lotus-organizer", Compressible, NotBinary, List("org"))
      lazy val `application/vnd.lotus-screencam`: MediaType =
        new MediaType(mainType, "vnd.lotus-screencam", Compressible, NotBinary, List("scm"))
      lazy val `application/vnd.lotus-wordpro`: MediaType =
        new MediaType(mainType, "vnd.lotus-wordpro", Compressible, NotBinary, List("lwp"))
      lazy val `application/vnd.macports.portpkg`: MediaType =
        new MediaType(mainType, "vnd.macports.portpkg", Compressible, NotBinary, List("portpkg"))
      lazy val `application/vnd.mapbox-vector-tile`: MediaType =
        new MediaType(mainType, "vnd.mapbox-vector-tile", Compressible, NotBinary)
      lazy val `application/vnd.marlin.drm.actiontoken+xml`: MediaType =
        new MediaType(mainType, "vnd.marlin.drm.actiontoken+xml", Compressible, NotBinary)
      lazy val `application/vnd.marlin.drm.conftoken+xml`: MediaType =
        new MediaType(mainType, "vnd.marlin.drm.conftoken+xml", Compressible, NotBinary)
      lazy val `application/vnd.marlin.drm.license+xml`: MediaType =
        new MediaType(mainType, "vnd.marlin.drm.license+xml", Compressible, NotBinary)
      lazy val `application/vnd.marlin.drm.mdcf`: MediaType =
        new MediaType(mainType, "vnd.marlin.drm.mdcf", Compressible, NotBinary)
      lazy val `application/vnd.mason+json`: MediaType =
        new MediaType(mainType, "vnd.mason+json", Compressible, NotBinary)
      lazy val `application/vnd.maxmind.maxmind-db`: MediaType =
        new MediaType(mainType, "vnd.maxmind.maxmind-db", Compressible, NotBinary)
      lazy val `application/vnd.mcd`: MediaType =
        new MediaType(mainType, "vnd.mcd", Compressible, NotBinary, List("mcd"))
      lazy val `application/vnd.medcalcdata`: MediaType =
        new MediaType(mainType, "vnd.medcalcdata", Compressible, NotBinary, List("mc1"))
      lazy val `application/vnd.mediastation.cdkey`: MediaType =
        new MediaType(mainType, "vnd.mediastation.cdkey", Compressible, NotBinary, List("cdkey"))
      lazy val `application/vnd.meridian-slingshot`: MediaType =
        new MediaType(mainType, "vnd.meridian-slingshot", Compressible, NotBinary)
      lazy val `application/vnd.mfer`: MediaType =
        new MediaType(mainType, "vnd.mfer", Compressible, NotBinary, List("mwf"))
      lazy val `application/vnd.mfmp`: MediaType =
        new MediaType(mainType, "vnd.mfmp", Compressible, NotBinary, List("mfm"))
      lazy val `application/vnd.micro+json`: MediaType =
        new MediaType(mainType, "vnd.micro+json", Compressible, NotBinary)
      lazy val `application/vnd.micrografx.flo`: MediaType =
        new MediaType(mainType, "vnd.micrografx.flo", Compressible, NotBinary, List("flo"))
      lazy val `application/vnd.micrografx.igx`: MediaType =
        new MediaType(mainType, "vnd.micrografx.igx", Compressible, NotBinary, List("igx"))
      lazy val `application/vnd.microsoft.portable-executable`: MediaType =
        new MediaType(mainType, "vnd.microsoft.portable-executable", Compressible, NotBinary)
      lazy val `application/vnd.microsoft.windows.thumbnail-cache`: MediaType =
        new MediaType(mainType, "vnd.microsoft.windows.thumbnail-cache", Compressible, NotBinary)
      lazy val `application/vnd.miele+json`: MediaType =
        new MediaType(mainType, "vnd.miele+json", Compressible, NotBinary)
      lazy val `application/vnd.mif`: MediaType =
        new MediaType(mainType, "vnd.mif", Compressible, NotBinary, List("mif"))
      lazy val `application/vnd.minisoft-hp3000-save`: MediaType =
        new MediaType(mainType, "vnd.minisoft-hp3000-save", Compressible, NotBinary)
      lazy val `application/vnd.mitsubishi.misty-guard.trustweb`: MediaType =
        new MediaType(mainType, "vnd.mitsubishi.misty-guard.trustweb", Compressible, NotBinary)
      lazy val `application/vnd.mobius.daf`: MediaType =
        new MediaType(mainType, "vnd.mobius.daf", Compressible, NotBinary, List("daf"))
      lazy val `application/vnd.mobius.dis`: MediaType =
        new MediaType(mainType, "vnd.mobius.dis", Compressible, NotBinary, List("dis"))
      lazy val `application/vnd.mobius.mbk`: MediaType =
        new MediaType(mainType, "vnd.mobius.mbk", Compressible, NotBinary, List("mbk"))
      lazy val `application/vnd.mobius.mqy`: MediaType =
        new MediaType(mainType, "vnd.mobius.mqy", Compressible, NotBinary, List("mqy"))
      lazy val `application/vnd.mobius.msl`: MediaType =
        new MediaType(mainType, "vnd.mobius.msl", Compressible, NotBinary, List("msl"))
      lazy val `application/vnd.mobius.plc`: MediaType =
        new MediaType(mainType, "vnd.mobius.plc", Compressible, NotBinary, List("plc"))
      lazy val `application/vnd.mobius.txf`: MediaType =
        new MediaType(mainType, "vnd.mobius.txf", Compressible, NotBinary, List("txf"))
      lazy val `application/vnd.mophun.application`: MediaType =
        new MediaType(mainType, "vnd.mophun.application", Compressible, NotBinary, List("mpn"))
      lazy val `application/vnd.mophun.certificate`: MediaType =
        new MediaType(mainType, "vnd.mophun.certificate", Compressible, NotBinary, List("mpc"))
      lazy val `application/vnd.motorola.flexsuite`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.adsi`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.adsi", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.fis`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.fis", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.gotap`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.gotap", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.kmr`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.kmr", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.ttc`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.ttc", Compressible, NotBinary)
      lazy val `application/vnd.motorola.flexsuite.wem`: MediaType =
        new MediaType(mainType, "vnd.motorola.flexsuite.wem", Compressible, NotBinary)
      lazy val `application/vnd.motorola.iprm`: MediaType =
        new MediaType(mainType, "vnd.motorola.iprm", Compressible, NotBinary)
      lazy val `application/vnd.mozilla.xul+xml`: MediaType =
        new MediaType(mainType, "vnd.mozilla.xul+xml", Compressible, NotBinary, List("xul"))
      lazy val `application/vnd.ms-3mfdocument`: MediaType =
        new MediaType(mainType, "vnd.ms-3mfdocument", Compressible, NotBinary)
      lazy val `application/vnd.ms-artgalry`: MediaType =
        new MediaType(mainType, "vnd.ms-artgalry", Compressible, NotBinary, List("cil"))
      lazy val `application/vnd.ms-asf`: MediaType =
        new MediaType(mainType, "vnd.ms-asf", Compressible, NotBinary)
      lazy val `application/vnd.ms-cab-compressed`: MediaType =
        new MediaType(mainType, "vnd.ms-cab-compressed", Compressible, NotBinary, List("cab"))
      lazy val `application/vnd.ms-color.iccprofile`: MediaType =
        new MediaType(mainType, "vnd.ms-color.iccprofile", Compressible, NotBinary)
      lazy val `application/vnd.ms-excel`: MediaType = new MediaType(
        mainType,
        "vnd.ms-excel",
        Uncompressible,
        NotBinary,
        List("xls", "xlm", "xla", "xlc", "xlt", "xlw"))
      lazy val `application/vnd.ms-excel.addin.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-excel.addin.macroenabled.12",
        Compressible,
        NotBinary,
        List("xlam"))
      lazy val `application/vnd.ms-excel.sheet.binary.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-excel.sheet.binary.macroenabled.12",
        Compressible,
        NotBinary,
        List("xlsb"))
      lazy val `application/vnd.ms-excel.sheet.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-excel.sheet.macroenabled.12",
        Compressible,
        NotBinary,
        List("xlsm"))
      lazy val `application/vnd.ms-excel.template.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-excel.template.macroenabled.12",
        Compressible,
        NotBinary,
        List("xltm"))
      lazy val `application/vnd.ms-fontobject`: MediaType =
        new MediaType(mainType, "vnd.ms-fontobject", Compressible, Binary, List("eot"))
      lazy val `application/vnd.ms-htmlhelp`: MediaType =
        new MediaType(mainType, "vnd.ms-htmlhelp", Compressible, NotBinary, List("chm"))
      lazy val `application/vnd.ms-ims`: MediaType =
        new MediaType(mainType, "vnd.ms-ims", Compressible, NotBinary, List("ims"))
      lazy val `application/vnd.ms-lrm`: MediaType =
        new MediaType(mainType, "vnd.ms-lrm", Compressible, NotBinary, List("lrm"))
      lazy val `application/vnd.ms-office.activex+xml`: MediaType =
        new MediaType(mainType, "vnd.ms-office.activex+xml", Compressible, NotBinary)
      lazy val `application/vnd.ms-officetheme`: MediaType =
        new MediaType(mainType, "vnd.ms-officetheme", Compressible, NotBinary, List("thmx"))
      lazy val `application/vnd.ms-opentype`: MediaType =
        new MediaType(mainType, "vnd.ms-opentype", Compressible, NotBinary)
      lazy val `application/vnd.ms-outlook`: MediaType =
        new MediaType(mainType, "vnd.ms-outlook", Uncompressible, NotBinary, List("msg"))
      lazy val `application/vnd.ms-package.obfuscated-opentype`: MediaType =
        new MediaType(mainType, "vnd.ms-package.obfuscated-opentype", Compressible, NotBinary)
      lazy val `application/vnd.ms-pki.seccat`: MediaType =
        new MediaType(mainType, "vnd.ms-pki.seccat", Compressible, NotBinary, List("cat"))
      lazy val `application/vnd.ms-pki.stl`: MediaType =
        new MediaType(mainType, "vnd.ms-pki.stl", Compressible, NotBinary, List("stl"))
      lazy val `application/vnd.ms-playready.initiator+xml`: MediaType =
        new MediaType(mainType, "vnd.ms-playready.initiator+xml", Compressible, NotBinary)
      lazy val `application/vnd.ms-powerpoint`: MediaType = new MediaType(
        mainType,
        "vnd.ms-powerpoint",
        Uncompressible,
        NotBinary,
        List("ppt", "pps", "pot"))
      lazy val `application/vnd.ms-powerpoint.addin.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-powerpoint.addin.macroenabled.12",
        Compressible,
        NotBinary,
        List("ppam"))
      lazy val `application/vnd.ms-powerpoint.presentation.macroenabled.12`: MediaType =
        new MediaType(
          mainType,
          "vnd.ms-powerpoint.presentation.macroenabled.12",
          Compressible,
          NotBinary,
          List("pptm"))
      lazy val `application/vnd.ms-powerpoint.slide.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-powerpoint.slide.macroenabled.12",
        Compressible,
        NotBinary,
        List("sldm"))
      lazy val `application/vnd.ms-powerpoint.slideshow.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-powerpoint.slideshow.macroenabled.12",
        Compressible,
        NotBinary,
        List("ppsm"))
      lazy val `application/vnd.ms-powerpoint.template.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-powerpoint.template.macroenabled.12",
        Compressible,
        NotBinary,
        List("potm"))
      lazy val `application/vnd.ms-printdevicecapabilities+xml`: MediaType =
        new MediaType(mainType, "vnd.ms-printdevicecapabilities+xml", Compressible, NotBinary)
      lazy val `application/vnd.ms-printing.printticket+xml`: MediaType =
        new MediaType(mainType, "vnd.ms-printing.printticket+xml", Compressible, NotBinary)
      lazy val `application/vnd.ms-printschematicket+xml`: MediaType =
        new MediaType(mainType, "vnd.ms-printschematicket+xml", Compressible, NotBinary)
      lazy val `application/vnd.ms-project`: MediaType =
        new MediaType(mainType, "vnd.ms-project", Compressible, NotBinary, List("mpp", "mpt"))
      lazy val `application/vnd.ms-tnef`: MediaType =
        new MediaType(mainType, "vnd.ms-tnef", Compressible, NotBinary)
      lazy val `application/vnd.ms-windows.devicepairing`: MediaType =
        new MediaType(mainType, "vnd.ms-windows.devicepairing", Compressible, NotBinary)
      lazy val `application/vnd.ms-windows.nwprinting.oob`: MediaType =
        new MediaType(mainType, "vnd.ms-windows.nwprinting.oob", Compressible, NotBinary)
      lazy val `application/vnd.ms-windows.printerpairing`: MediaType =
        new MediaType(mainType, "vnd.ms-windows.printerpairing", Compressible, NotBinary)
      lazy val `application/vnd.ms-windows.wsd.oob`: MediaType =
        new MediaType(mainType, "vnd.ms-windows.wsd.oob", Compressible, NotBinary)
      lazy val `application/vnd.ms-wmdrm.lic-chlg-req`: MediaType =
        new MediaType(mainType, "vnd.ms-wmdrm.lic-chlg-req", Compressible, NotBinary)
      lazy val `application/vnd.ms-wmdrm.lic-resp`: MediaType =
        new MediaType(mainType, "vnd.ms-wmdrm.lic-resp", Compressible, NotBinary)
      lazy val `application/vnd.ms-wmdrm.meter-chlg-req`: MediaType =
        new MediaType(mainType, "vnd.ms-wmdrm.meter-chlg-req", Compressible, NotBinary)
      lazy val `application/vnd.ms-wmdrm.meter-resp`: MediaType =
        new MediaType(mainType, "vnd.ms-wmdrm.meter-resp", Compressible, NotBinary)
      lazy val `application/vnd.ms-word.document.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-word.document.macroenabled.12",
        Compressible,
        NotBinary,
        List("docm"))
      lazy val `application/vnd.ms-word.template.macroenabled.12`: MediaType = new MediaType(
        mainType,
        "vnd.ms-word.template.macroenabled.12",
        Compressible,
        NotBinary,
        List("dotm"))
      lazy val `application/vnd.ms-works`: MediaType = new MediaType(
        mainType,
        "vnd.ms-works",
        Compressible,
        NotBinary,
        List("wps", "wks", "wcm", "wdb"))
      lazy val `application/vnd.ms-wpl`: MediaType =
        new MediaType(mainType, "vnd.ms-wpl", Compressible, NotBinary, List("wpl"))
      lazy val `application/vnd.ms-xpsdocument`: MediaType =
        new MediaType(mainType, "vnd.ms-xpsdocument", Uncompressible, NotBinary, List("xps"))
      lazy val `application/vnd.msa-disk-image`: MediaType =
        new MediaType(mainType, "vnd.msa-disk-image", Compressible, NotBinary)
      lazy val `application/vnd.mseq`: MediaType =
        new MediaType(mainType, "vnd.mseq", Compressible, NotBinary, List("mseq"))
      lazy val `application/vnd.msign`: MediaType =
        new MediaType(mainType, "vnd.msign", Compressible, NotBinary)
      lazy val `application/vnd.multiad.creator`: MediaType =
        new MediaType(mainType, "vnd.multiad.creator", Compressible, NotBinary)
      lazy val `application/vnd.multiad.creator.cif`: MediaType =
        new MediaType(mainType, "vnd.multiad.creator.cif", Compressible, NotBinary)
      lazy val `application/vnd.music-niff`: MediaType =
        new MediaType(mainType, "vnd.music-niff", Compressible, NotBinary)
      lazy val `application/vnd.musician`: MediaType =
        new MediaType(mainType, "vnd.musician", Compressible, NotBinary, List("mus"))
      lazy val `application/vnd.muvee.style`: MediaType =
        new MediaType(mainType, "vnd.muvee.style", Compressible, NotBinary, List("msty"))
      lazy val `application/vnd.mynfc`: MediaType =
        new MediaType(mainType, "vnd.mynfc", Compressible, NotBinary, List("taglet"))
      lazy val `application/vnd.ncd.control`: MediaType =
        new MediaType(mainType, "vnd.ncd.control", Compressible, NotBinary)
      lazy val `application/vnd.ncd.reference`: MediaType =
        new MediaType(mainType, "vnd.ncd.reference", Compressible, NotBinary)
      lazy val `application/vnd.nearst.inv+json`: MediaType =
        new MediaType(mainType, "vnd.nearst.inv+json", Compressible, NotBinary)
      lazy val `application/vnd.nervana`: MediaType =
        new MediaType(mainType, "vnd.nervana", Compressible, NotBinary)
      lazy val `application/vnd.netfpx`: MediaType =
        new MediaType(mainType, "vnd.netfpx", Compressible, NotBinary)
      lazy val `application/vnd.neurolanguage.nlu`: MediaType =
        new MediaType(mainType, "vnd.neurolanguage.nlu", Compressible, NotBinary, List("nlu"))
      lazy val `application/vnd.nintendo.nitro.rom`: MediaType =
        new MediaType(mainType, "vnd.nintendo.nitro.rom", Compressible, NotBinary)
      lazy val `application/vnd.nintendo.snes.rom`: MediaType =
        new MediaType(mainType, "vnd.nintendo.snes.rom", Compressible, NotBinary)
      lazy val `application/vnd.nitf`: MediaType =
        new MediaType(mainType, "vnd.nitf", Compressible, NotBinary, List("ntf", "nitf"))
      lazy val `application/vnd.noblenet-directory`: MediaType =
        new MediaType(mainType, "vnd.noblenet-directory", Compressible, NotBinary, List("nnd"))
      lazy val `application/vnd.noblenet-sealer`: MediaType =
        new MediaType(mainType, "vnd.noblenet-sealer", Compressible, NotBinary, List("nns"))
      lazy val `application/vnd.noblenet-web`: MediaType =
        new MediaType(mainType, "vnd.noblenet-web", Compressible, NotBinary, List("nnw"))
      lazy val `application/vnd.nokia.catalogs`: MediaType =
        new MediaType(mainType, "vnd.nokia.catalogs", Compressible, NotBinary)
      lazy val `application/vnd.nokia.conml+wbxml`: MediaType =
        new MediaType(mainType, "vnd.nokia.conml+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.conml+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.conml+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.iptv.config+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.iptv.config+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.isds-radio-presets`: MediaType =
        new MediaType(mainType, "vnd.nokia.isds-radio-presets", Compressible, NotBinary)
      lazy val `application/vnd.nokia.landmark+wbxml`: MediaType =
        new MediaType(mainType, "vnd.nokia.landmark+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.landmark+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.landmark+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.landmarkcollection+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.landmarkcollection+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.n-gage.ac+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.n-gage.ac+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.n-gage.data`: MediaType =
        new MediaType(mainType, "vnd.nokia.n-gage.data", Compressible, NotBinary, List("ngdat"))
      lazy val `application/vnd.nokia.n-gage.symbian.install`: MediaType = new MediaType(
        mainType,
        "vnd.nokia.n-gage.symbian.install",
        Compressible,
        NotBinary,
        List("n-gage"))
      lazy val `application/vnd.nokia.ncd`: MediaType =
        new MediaType(mainType, "vnd.nokia.ncd", Compressible, NotBinary)
      lazy val `application/vnd.nokia.pcd+wbxml`: MediaType =
        new MediaType(mainType, "vnd.nokia.pcd+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.pcd+xml`: MediaType =
        new MediaType(mainType, "vnd.nokia.pcd+xml", Compressible, NotBinary)
      lazy val `application/vnd.nokia.radio-preset`: MediaType =
        new MediaType(mainType, "vnd.nokia.radio-preset", Compressible, NotBinary, List("rpst"))
      lazy val `application/vnd.nokia.radio-presets`: MediaType =
        new MediaType(mainType, "vnd.nokia.radio-presets", Compressible, NotBinary, List("rpss"))
      lazy val `application/vnd.novadigm.edm`: MediaType =
        new MediaType(mainType, "vnd.novadigm.edm", Compressible, NotBinary, List("edm"))
      lazy val `application/vnd.novadigm.edx`: MediaType =
        new MediaType(mainType, "vnd.novadigm.edx", Compressible, NotBinary, List("edx"))
      lazy val `application/vnd.novadigm.ext`: MediaType =
        new MediaType(mainType, "vnd.novadigm.ext", Compressible, NotBinary, List("ext"))
      lazy val `application/vnd.ntt-local.content-share`: MediaType =
        new MediaType(mainType, "vnd.ntt-local.content-share", Compressible, NotBinary)
      lazy val `application/vnd.ntt-local.file-transfer`: MediaType =
        new MediaType(mainType, "vnd.ntt-local.file-transfer", Compressible, NotBinary)
      lazy val `application/vnd.ntt-local.ogw_remote-access`: MediaType =
        new MediaType(mainType, "vnd.ntt-local.ogw_remote-access", Compressible, NotBinary)
      lazy val `application/vnd.ntt-local.sip-ta_remote`: MediaType =
        new MediaType(mainType, "vnd.ntt-local.sip-ta_remote", Compressible, NotBinary)
      lazy val `application/vnd.ntt-local.sip-ta_tcp_stream`: MediaType =
        new MediaType(mainType, "vnd.ntt-local.sip-ta_tcp_stream", Compressible, NotBinary)
      lazy val `application/vnd.oasis.opendocument.chart`: MediaType =
        new MediaType(mainType, "vnd.oasis.opendocument.chart", Compressible, Binary, List("odc"))
      lazy val `application/vnd.oasis.opendocument.chart-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.chart-template",
        Compressible,
        NotBinary,
        List("otc"))
      lazy val `application/vnd.oasis.opendocument.database`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.database",
        Compressible,
        Binary,
        List("odb"))
      lazy val `application/vnd.oasis.opendocument.formula`: MediaType =
        new MediaType(mainType, "vnd.oasis.opendocument.formula", Compressible, Binary, List("odf"))
      lazy val `application/vnd.oasis.opendocument.formula-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.formula-template",
        Compressible,
        NotBinary,
        List("odft"))
      lazy val `application/vnd.oasis.opendocument.graphics`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.graphics",
        Uncompressible,
        Binary,
        List("odg"))
      lazy val `application/vnd.oasis.opendocument.graphics-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.graphics-template",
        Compressible,
        NotBinary,
        List("otg"))
      lazy val `application/vnd.oasis.opendocument.image`: MediaType =
        new MediaType(mainType, "vnd.oasis.opendocument.image", Compressible, Binary, List("odi"))
      lazy val `application/vnd.oasis.opendocument.image-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.image-template",
        Compressible,
        NotBinary,
        List("oti"))
      lazy val `application/vnd.oasis.opendocument.presentation`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.presentation",
        Uncompressible,
        Binary,
        List("odp"))
      lazy val `application/vnd.oasis.opendocument.presentation-template`: MediaType =
        new MediaType(
          mainType,
          "vnd.oasis.opendocument.presentation-template",
          Compressible,
          NotBinary,
          List("otp"))
      lazy val `application/vnd.oasis.opendocument.spreadsheet`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.spreadsheet",
        Uncompressible,
        Binary,
        List("ods"))
      lazy val `application/vnd.oasis.opendocument.spreadsheet-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.spreadsheet-template",
        Compressible,
        NotBinary,
        List("ots"))
      lazy val `application/vnd.oasis.opendocument.text`: MediaType =
        new MediaType(mainType, "vnd.oasis.opendocument.text", Uncompressible, Binary, List("odt"))
      lazy val `application/vnd.oasis.opendocument.text-master`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.text-master",
        Compressible,
        Binary,
        List("odm"))
      lazy val `application/vnd.oasis.opendocument.text-template`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.text-template",
        Compressible,
        NotBinary,
        List("ott"))
      lazy val `application/vnd.oasis.opendocument.text-web`: MediaType = new MediaType(
        mainType,
        "vnd.oasis.opendocument.text-web",
        Compressible,
        Binary,
        List("oth"))
      lazy val `application/vnd.obn`: MediaType =
        new MediaType(mainType, "vnd.obn", Compressible, NotBinary)
      lazy val `application/vnd.ocf+cbor`: MediaType =
        new MediaType(mainType, "vnd.ocf+cbor", Compressible, NotBinary)
      lazy val `application/vnd.oftn.l10n+json`: MediaType =
        new MediaType(mainType, "vnd.oftn.l10n+json", Compressible, NotBinary)
      lazy val `application/vnd.oipf.contentaccessdownload+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.contentaccessdownload+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.contentaccessstreaming+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.contentaccessstreaming+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.cspg-hexbinary`: MediaType =
        new MediaType(mainType, "vnd.oipf.cspg-hexbinary", Compressible, NotBinary)
      lazy val `application/vnd.oipf.dae.svg+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.dae.svg+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.dae.xhtml+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.dae.xhtml+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.mippvcontrolmessage+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.mippvcontrolmessage+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.pae.gem`: MediaType =
        new MediaType(mainType, "vnd.oipf.pae.gem", Compressible, NotBinary)
      lazy val `application/vnd.oipf.spdiscovery+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.spdiscovery+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.spdlist+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.spdlist+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.ueprofile+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.ueprofile+xml", Compressible, NotBinary)
      lazy val `application/vnd.oipf.userprofile+xml`: MediaType =
        new MediaType(mainType, "vnd.oipf.userprofile+xml", Compressible, NotBinary)
      lazy val `application/vnd.olpc-sugar`: MediaType =
        new MediaType(mainType, "vnd.olpc-sugar", Compressible, NotBinary, List("xo"))
      lazy val `application/vnd.oma-scws-config`: MediaType =
        new MediaType(mainType, "vnd.oma-scws-config", Compressible, NotBinary)
      lazy val `application/vnd.oma-scws-http-request`: MediaType =
        new MediaType(mainType, "vnd.oma-scws-http-request", Compressible, NotBinary)
      lazy val `application/vnd.oma-scws-http-response`: MediaType =
        new MediaType(mainType, "vnd.oma-scws-http-response", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.associated-procedure-parameter+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.oma.bcast.associated-procedure-parameter+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.oma.bcast.drm-trigger+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.drm-trigger+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.imd+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.imd+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.ltkm`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.ltkm", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.notification+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.notification+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.provisioningtrigger`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.provisioningtrigger", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.sgboot`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.sgboot", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.sgdd+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.sgdd+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.sgdu`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.sgdu", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.simple-symbol-container`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.simple-symbol-container", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.smartcard-trigger+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.smartcard-trigger+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.sprov+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.sprov+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.bcast.stkm`: MediaType =
        new MediaType(mainType, "vnd.oma.bcast.stkm", Compressible, NotBinary)
      lazy val `application/vnd.oma.cab-address-book+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.cab-address-book+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.cab-feature-handler+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.cab-feature-handler+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.cab-pcc+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.cab-pcc+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.cab-subs-invite+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.cab-subs-invite+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.cab-user-prefs+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.cab-user-prefs+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.dcd`: MediaType =
        new MediaType(mainType, "vnd.oma.dcd", Compressible, NotBinary)
      lazy val `application/vnd.oma.dcdc`: MediaType =
        new MediaType(mainType, "vnd.oma.dcdc", Compressible, NotBinary)
      lazy val `application/vnd.oma.dd2+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.dd2+xml", Compressible, NotBinary, List("dd2"))
      lazy val `application/vnd.oma.drm.risd+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.drm.risd+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.group-usage-list+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.group-usage-list+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.lwm2m+json`: MediaType =
        new MediaType(mainType, "vnd.oma.lwm2m+json", Compressible, NotBinary)
      lazy val `application/vnd.oma.lwm2m+tlv`: MediaType =
        new MediaType(mainType, "vnd.oma.lwm2m+tlv", Compressible, NotBinary)
      lazy val `application/vnd.oma.pal+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.pal+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.poc.detailed-progress-report+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.poc.detailed-progress-report+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.poc.final-report+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.poc.final-report+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.poc.groups+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.poc.groups+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.poc.invocation-descriptor+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.poc.invocation-descriptor+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.poc.optimized-progress-report+xml`: MediaType = new MediaType(
        mainType,
        "vnd.oma.poc.optimized-progress-report+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.oma.push`: MediaType =
        new MediaType(mainType, "vnd.oma.push", Compressible, NotBinary)
      lazy val `application/vnd.oma.scidm.messages+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.scidm.messages+xml", Compressible, NotBinary)
      lazy val `application/vnd.oma.xcap-directory+xml`: MediaType =
        new MediaType(mainType, "vnd.oma.xcap-directory+xml", Compressible, NotBinary)
      lazy val `application/vnd.omads-email+xml`: MediaType =
        new MediaType(mainType, "vnd.omads-email+xml", Compressible, NotBinary)
      lazy val `application/vnd.omads-file+xml`: MediaType =
        new MediaType(mainType, "vnd.omads-file+xml", Compressible, NotBinary)
      lazy val `application/vnd.omads-folder+xml`: MediaType =
        new MediaType(mainType, "vnd.omads-folder+xml", Compressible, NotBinary)
      lazy val `application/vnd.omaloc-supl-init`: MediaType =
        new MediaType(mainType, "vnd.omaloc-supl-init", Compressible, NotBinary)
      lazy val `application/vnd.onepager`: MediaType =
        new MediaType(mainType, "vnd.onepager", Compressible, NotBinary)
      lazy val `application/vnd.onepagertamp`: MediaType =
        new MediaType(mainType, "vnd.onepagertamp", Compressible, NotBinary)
      lazy val `application/vnd.onepagertamx`: MediaType =
        new MediaType(mainType, "vnd.onepagertamx", Compressible, NotBinary)
      lazy val `application/vnd.onepagertat`: MediaType =
        new MediaType(mainType, "vnd.onepagertat", Compressible, NotBinary)
      lazy val `application/vnd.onepagertatp`: MediaType =
        new MediaType(mainType, "vnd.onepagertatp", Compressible, NotBinary)
      lazy val `application/vnd.onepagertatx`: MediaType =
        new MediaType(mainType, "vnd.onepagertatx", Compressible, NotBinary)
      lazy val `application/vnd.openblox.game+xml`: MediaType =
        new MediaType(mainType, "vnd.openblox.game+xml", Compressible, NotBinary)
      lazy val `application/vnd.openblox.game-binary`: MediaType =
        new MediaType(mainType, "vnd.openblox.game-binary", Compressible, NotBinary)
      lazy val `application/vnd.openeye.oeb`: MediaType =
        new MediaType(mainType, "vnd.openeye.oeb", Compressible, NotBinary)
      lazy val `application/vnd.openofficeorg.extension`: MediaType =
        new MediaType(mainType, "vnd.openofficeorg.extension", Compressible, NotBinary, List("oxt"))
      lazy val `application/vnd.openstreetmap.data+xml`: MediaType =
        new MediaType(mainType, "vnd.openstreetmap.data+xml", Compressible, NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.custom-properties+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.custom-properties+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.customxmlproperties+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.customxmlproperties+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawing+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.drawing+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.chart+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.drawingml.chart+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.chartshapes+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.drawingml.chartshapes+xml",
        Compressible,
        NotBinary)
      lazy val all: List[MediaType] = List(
        `application/vnd.criticaltools.wbs+xml`,
        `application/vnd.ctc-posml`,
        `application/vnd.ctct.ws+xml`,
        `application/vnd.cups-pdf`,
        `application/vnd.cups-postscript`,
        `application/vnd.cups-ppd`,
        `application/vnd.cups-raster`,
        `application/vnd.cups-raw`,
        `application/vnd.curl`,
        `application/vnd.curl.car`,
        `application/vnd.curl.pcurl`,
        `application/vnd.cyan.dean.root+xml`,
        `application/vnd.cybank`,
        `application/vnd.d2l.coursepackage1p0+zip`,
        `application/vnd.dart`,
        `application/vnd.data-vision.rdz`,
        `application/vnd.datapackage+json`,
        `application/vnd.dataresource+json`,
        `application/vnd.debian.binary-package`,
        `application/vnd.dece.data`,
        `application/vnd.dece.ttml+xml`,
        `application/vnd.dece.unspecified`,
        `application/vnd.dece.zip`,
        `application/vnd.denovo.fcselayout-link`,
        `application/vnd.desmume-movie`,
        `application/vnd.desmume.movie`,
        `application/vnd.dir-bi.plate-dl-nosuffix`,
        `application/vnd.dm.delegation+xml`,
        `application/vnd.dna`,
        `application/vnd.document+json`,
        `application/vnd.dolby.mlp`,
        `application/vnd.dolby.mobile.1`,
        `application/vnd.dolby.mobile.2`,
        `application/vnd.doremir.scorecloud-binary-document`,
        `application/vnd.dpgraph`,
        `application/vnd.dreamfactory`,
        `application/vnd.drive+json`,
        `application/vnd.ds-keypoint`,
        `application/vnd.dtg.local`,
        `application/vnd.dtg.local.flash`,
        `application/vnd.dtg.local.html`,
        `application/vnd.dvb.ait`,
        `application/vnd.dvb.dvbj`,
        `application/vnd.dvb.esgcontainer`,
        `application/vnd.dvb.ipdcdftnotifaccess`,
        `application/vnd.dvb.ipdcesgaccess`,
        `application/vnd.dvb.ipdcesgaccess2`,
        `application/vnd.dvb.ipdcesgpdd`,
        `application/vnd.dvb.ipdcroaming`,
        `application/vnd.dvb.iptv.alfec-base`,
        `application/vnd.dvb.iptv.alfec-enhancement`,
        `application/vnd.dvb.notif-aggregate-root+xml`,
        `application/vnd.dvb.notif-container+xml`,
        `application/vnd.dvb.notif-generic+xml`,
        `application/vnd.dvb.notif-ia-msglist+xml`,
        `application/vnd.dvb.notif-ia-registration-request+xml`,
        `application/vnd.dvb.notif-ia-registration-response+xml`,
        `application/vnd.dvb.notif-init+xml`,
        `application/vnd.dvb.pfr`,
        `application/vnd.dvb.service`,
        `application/vnd.dxr`,
        `application/vnd.dynageo`,
        `application/vnd.dzr`,
        `application/vnd.easykaraoke.cdgdownload`,
        `application/vnd.ecdis-update`,
        `application/vnd.ecip.rlp`,
        `application/vnd.ecowin.chart`,
        `application/vnd.ecowin.filerequest`,
        `application/vnd.ecowin.fileupdate`,
        `application/vnd.ecowin.series`,
        `application/vnd.ecowin.seriesrequest`,
        `application/vnd.ecowin.seriesupdate`,
        `application/vnd.efi.img`,
        `application/vnd.efi.iso`,
        `application/vnd.emclient.accessrequest+xml`,
        `application/vnd.enliven`,
        `application/vnd.enphase.envoy`,
        `application/vnd.eprints.data+xml`,
        `application/vnd.epson.esf`,
        `application/vnd.epson.msf`,
        `application/vnd.epson.quickanime`,
        `application/vnd.epson.salt`,
        `application/vnd.epson.ssf`,
        `application/vnd.ericsson.quickcall`,
        `application/vnd.espass-espass+zip`,
        `application/vnd.eszigno3+xml`,
        `application/vnd.etsi.aoc+xml`,
        `application/vnd.etsi.asic-e+zip`,
        `application/vnd.etsi.asic-s+zip`,
        `application/vnd.etsi.cug+xml`,
        `application/vnd.etsi.iptvcommand+xml`,
        `application/vnd.etsi.iptvdiscovery+xml`,
        `application/vnd.etsi.iptvprofile+xml`,
        `application/vnd.etsi.iptvsad-bc+xml`,
        `application/vnd.etsi.iptvsad-cod+xml`,
        `application/vnd.etsi.iptvsad-npvr+xml`,
        `application/vnd.etsi.iptvservice+xml`,
        `application/vnd.etsi.iptvsync+xml`,
        `application/vnd.etsi.iptvueprofile+xml`,
        `application/vnd.etsi.mcid+xml`,
        `application/vnd.etsi.mheg5`,
        `application/vnd.etsi.overload-control-policy-dataset+xml`,
        `application/vnd.etsi.pstn+xml`,
        `application/vnd.etsi.sci+xml`,
        `application/vnd.etsi.simservs+xml`,
        `application/vnd.etsi.timestamp-token`,
        `application/vnd.etsi.tsl+xml`,
        `application/vnd.etsi.tsl.der`,
        `application/vnd.eudora.data`,
        `application/vnd.evolv.ecig.profile`,
        `application/vnd.evolv.ecig.settings`,
        `application/vnd.evolv.ecig.theme`,
        `application/vnd.ezpix-album`,
        `application/vnd.ezpix-package`,
        `application/vnd.f-secure.mobile`,
        `application/vnd.fastcopy-disk-image`,
        `application/vnd.fdf`,
        `application/vnd.fdsn.mseed`,
        `application/vnd.fdsn.seed`,
        `application/vnd.ffsns`,
        `application/vnd.filmit.zfc`,
        `application/vnd.fints`,
        `application/vnd.firemonkeys.cloudcell`,
        `application/vnd.flographit`,
        `application/vnd.fluxtime.clip`,
        `application/vnd.font-fontforge-sfd`,
        `application/vnd.framemaker`,
        `application/vnd.frogans.fnc`,
        `application/vnd.frogans.ltf`,
        `application/vnd.fsc.weblaunch`,
        `application/vnd.fujitsu.oasys`,
        `application/vnd.fujitsu.oasys2`,
        `application/vnd.fujitsu.oasys3`,
        `application/vnd.fujitsu.oasysgp`,
        `application/vnd.fujitsu.oasysprs`,
        `application/vnd.fujixerox.art-ex`,
        `application/vnd.fujixerox.art4`,
        `application/vnd.fujixerox.ddd`,
        `application/vnd.fujixerox.docuworks`,
        `application/vnd.fujixerox.docuworks.binder`,
        `application/vnd.fujixerox.docuworks.container`,
        `application/vnd.fujixerox.hbpl`,
        `application/vnd.fut-misnet`,
        `application/vnd.fuzzysheet`,
        `application/vnd.genomatix.tuxedo`,
        `application/vnd.geo+json`,
        `application/vnd.geocube+xml`,
        `application/vnd.geogebra.file`,
        `application/vnd.geogebra.tool`,
        `application/vnd.geometry-explorer`,
        `application/vnd.geonext`,
        `application/vnd.geoplan`,
        `application/vnd.geospace`,
        `application/vnd.gerber`,
        `application/vnd.globalplatform.card-content-mgt`,
        `application/vnd.globalplatform.card-content-mgt-response`,
        `application/vnd.gmx`,
        `application/vnd.google-apps.document`,
        `application/vnd.google-apps.presentation`,
        `application/vnd.google-apps.spreadsheet`,
        `application/vnd.google-earth.kml+xml`,
        `application/vnd.google-earth.kmz`,
        `application/vnd.gov.sk.e-form+xml`,
        `application/vnd.gov.sk.e-form+zip`,
        `application/vnd.gov.sk.xmldatacontainer+xml`,
        `application/vnd.grafeq`,
        `application/vnd.gridmp`,
        `application/vnd.groove-account`,
        `application/vnd.groove-help`,
        `application/vnd.groove-identity-message`,
        `application/vnd.groove-injector`,
        `application/vnd.groove-tool-message`,
        `application/vnd.groove-tool-template`,
        `application/vnd.groove-vcard`,
        `application/vnd.hal+json`,
        `application/vnd.hal+xml`,
        `application/vnd.handheld-entertainment+xml`,
        `application/vnd.hbci`,
        `application/vnd.hc+json`,
        `application/vnd.hcl-bireports`,
        `application/vnd.hdt`,
        `application/vnd.heroku+json`,
        `application/vnd.hhe.lesson-player`,
        `application/vnd.hp-hpgl`,
        `application/vnd.hp-hpid`,
        `application/vnd.hp-hps`,
        `application/vnd.hp-jlyt`,
        `application/vnd.hp-pcl`,
        `application/vnd.hp-pclxl`,
        `application/vnd.httphone`,
        `application/vnd.hydrostatix.sof-data`,
        `application/vnd.hyper+json`,
        `application/vnd.hyper-item+json`,
        `application/vnd.hyperdrive+json`,
        `application/vnd.hzn-3d-crossword`,
        `application/vnd.ibm.afplinedata`,
        `application/vnd.ibm.electronic-media`,
        `application/vnd.ibm.minipay`,
        `application/vnd.ibm.modcap`,
        `application/vnd.ibm.rights-management`,
        `application/vnd.ibm.secure-container`,
        `application/vnd.iccprofile`,
        `application/vnd.ieee.1905`,
        `application/vnd.igloader`,
        `application/vnd.imagemeter.folder+zip`,
        `application/vnd.imagemeter.image+zip`,
        `application/vnd.immervision-ivp`,
        `application/vnd.immervision-ivu`,
        `application/vnd.ims.imsccv1p1`,
        `application/vnd.ims.imsccv1p2`,
        `application/vnd.ims.imsccv1p3`,
        `application/vnd.ims.lis.v2.result+json`,
        `application/vnd.ims.lti.v2.toolconsumerprofile+json`,
        `application/vnd.ims.lti.v2.toolproxy+json`,
        `application/vnd.ims.lti.v2.toolproxy.id+json`,
        `application/vnd.ims.lti.v2.toolsettings+json`,
        `application/vnd.ims.lti.v2.toolsettings.simple+json`,
        `application/vnd.informedcontrol.rms+xml`,
        `application/vnd.informix-visionary`,
        `application/vnd.infotech.project`,
        `application/vnd.infotech.project+xml`,
        `application/vnd.innopath.wamp.notification`,
        `application/vnd.insors.igm`,
        `application/vnd.intercon.formnet`,
        `application/vnd.intergeo`,
        `application/vnd.intertrust.digibox`,
        `application/vnd.intertrust.nncp`,
        `application/vnd.intu.qbo`,
        `application/vnd.intu.qfx`,
        `application/vnd.iptc.g2.catalogitem+xml`,
        `application/vnd.iptc.g2.conceptitem+xml`,
        `application/vnd.iptc.g2.knowledgeitem+xml`,
        `application/vnd.iptc.g2.newsitem+xml`,
        `application/vnd.iptc.g2.newsmessage+xml`,
        `application/vnd.iptc.g2.packageitem+xml`,
        `application/vnd.iptc.g2.planningitem+xml`,
        `application/vnd.ipunplugged.rcprofile`,
        `application/vnd.irepository.package+xml`,
        `application/vnd.is-xpr`,
        `application/vnd.isac.fcs`,
        `application/vnd.jam`,
        `application/vnd.japannet-directory-service`,
        `application/vnd.japannet-jpnstore-wakeup`,
        `application/vnd.japannet-payment-wakeup`,
        `application/vnd.japannet-registration`,
        `application/vnd.japannet-registration-wakeup`,
        `application/vnd.japannet-setstore-wakeup`,
        `application/vnd.japannet-verification`,
        `application/vnd.japannet-verification-wakeup`,
        `application/vnd.jcp.javame.midlet-rms`,
        `application/vnd.jisp`,
        `application/vnd.joost.joda-archive`,
        `application/vnd.jsk.isdn-ngn`,
        `application/vnd.kahootz`,
        `application/vnd.kde.karbon`,
        `application/vnd.kde.kchart`,
        `application/vnd.kde.kformula`,
        `application/vnd.kde.kivio`,
        `application/vnd.kde.kontour`,
        `application/vnd.kde.kpresenter`,
        `application/vnd.kde.kspread`,
        `application/vnd.kde.kword`,
        `application/vnd.kenameaapp`,
        `application/vnd.kidspiration`,
        `application/vnd.kinar`,
        `application/vnd.koan`,
        `application/vnd.kodak-descriptor`,
        `application/vnd.las.las+json`,
        `application/vnd.las.las+xml`,
        `application/vnd.liberty-request+xml`,
        `application/vnd.llamagraphics.life-balance.desktop`,
        `application/vnd.llamagraphics.life-balance.exchange+xml`,
        `application/vnd.lotus-1-2-3`,
        `application/vnd.lotus-approach`,
        `application/vnd.lotus-freelance`,
        `application/vnd.lotus-notes`,
        `application/vnd.lotus-organizer`,
        `application/vnd.lotus-screencam`,
        `application/vnd.lotus-wordpro`,
        `application/vnd.macports.portpkg`,
        `application/vnd.mapbox-vector-tile`,
        `application/vnd.marlin.drm.actiontoken+xml`,
        `application/vnd.marlin.drm.conftoken+xml`,
        `application/vnd.marlin.drm.license+xml`,
        `application/vnd.marlin.drm.mdcf`,
        `application/vnd.mason+json`,
        `application/vnd.maxmind.maxmind-db`,
        `application/vnd.mcd`,
        `application/vnd.medcalcdata`,
        `application/vnd.mediastation.cdkey`,
        `application/vnd.meridian-slingshot`,
        `application/vnd.mfer`,
        `application/vnd.mfmp`,
        `application/vnd.micro+json`,
        `application/vnd.micrografx.flo`,
        `application/vnd.micrografx.igx`,
        `application/vnd.microsoft.portable-executable`,
        `application/vnd.microsoft.windows.thumbnail-cache`,
        `application/vnd.miele+json`,
        `application/vnd.mif`,
        `application/vnd.minisoft-hp3000-save`,
        `application/vnd.mitsubishi.misty-guard.trustweb`,
        `application/vnd.mobius.daf`,
        `application/vnd.mobius.dis`,
        `application/vnd.mobius.mbk`,
        `application/vnd.mobius.mqy`,
        `application/vnd.mobius.msl`,
        `application/vnd.mobius.plc`,
        `application/vnd.mobius.txf`,
        `application/vnd.mophun.application`,
        `application/vnd.mophun.certificate`,
        `application/vnd.motorola.flexsuite`,
        `application/vnd.motorola.flexsuite.adsi`,
        `application/vnd.motorola.flexsuite.fis`,
        `application/vnd.motorola.flexsuite.gotap`,
        `application/vnd.motorola.flexsuite.kmr`,
        `application/vnd.motorola.flexsuite.ttc`,
        `application/vnd.motorola.flexsuite.wem`,
        `application/vnd.motorola.iprm`,
        `application/vnd.mozilla.xul+xml`,
        `application/vnd.ms-3mfdocument`,
        `application/vnd.ms-artgalry`,
        `application/vnd.ms-asf`,
        `application/vnd.ms-cab-compressed`,
        `application/vnd.ms-color.iccprofile`,
        `application/vnd.ms-excel`,
        `application/vnd.ms-excel.addin.macroenabled.12`,
        `application/vnd.ms-excel.sheet.binary.macroenabled.12`,
        `application/vnd.ms-excel.sheet.macroenabled.12`,
        `application/vnd.ms-excel.template.macroenabled.12`,
        `application/vnd.ms-fontobject`,
        `application/vnd.ms-htmlhelp`,
        `application/vnd.ms-ims`,
        `application/vnd.ms-lrm`,
        `application/vnd.ms-office.activex+xml`,
        `application/vnd.ms-officetheme`,
        `application/vnd.ms-opentype`,
        `application/vnd.ms-outlook`,
        `application/vnd.ms-package.obfuscated-opentype`,
        `application/vnd.ms-pki.seccat`,
        `application/vnd.ms-pki.stl`,
        `application/vnd.ms-playready.initiator+xml`,
        `application/vnd.ms-powerpoint`,
        `application/vnd.ms-powerpoint.addin.macroenabled.12`,
        `application/vnd.ms-powerpoint.presentation.macroenabled.12`,
        `application/vnd.ms-powerpoint.slide.macroenabled.12`,
        `application/vnd.ms-powerpoint.slideshow.macroenabled.12`,
        `application/vnd.ms-powerpoint.template.macroenabled.12`,
        `application/vnd.ms-printdevicecapabilities+xml`,
        `application/vnd.ms-printing.printticket+xml`,
        `application/vnd.ms-printschematicket+xml`,
        `application/vnd.ms-project`,
        `application/vnd.ms-tnef`,
        `application/vnd.ms-windows.devicepairing`,
        `application/vnd.ms-windows.nwprinting.oob`,
        `application/vnd.ms-windows.printerpairing`,
        `application/vnd.ms-windows.wsd.oob`,
        `application/vnd.ms-wmdrm.lic-chlg-req`,
        `application/vnd.ms-wmdrm.lic-resp`,
        `application/vnd.ms-wmdrm.meter-chlg-req`,
        `application/vnd.ms-wmdrm.meter-resp`,
        `application/vnd.ms-word.document.macroenabled.12`,
        `application/vnd.ms-word.template.macroenabled.12`,
        `application/vnd.ms-works`,
        `application/vnd.ms-wpl`,
        `application/vnd.ms-xpsdocument`,
        `application/vnd.msa-disk-image`,
        `application/vnd.mseq`,
        `application/vnd.msign`,
        `application/vnd.multiad.creator`,
        `application/vnd.multiad.creator.cif`,
        `application/vnd.music-niff`,
        `application/vnd.musician`,
        `application/vnd.muvee.style`,
        `application/vnd.mynfc`,
        `application/vnd.ncd.control`,
        `application/vnd.ncd.reference`,
        `application/vnd.nearst.inv+json`,
        `application/vnd.nervana`,
        `application/vnd.netfpx`,
        `application/vnd.neurolanguage.nlu`,
        `application/vnd.nintendo.nitro.rom`,
        `application/vnd.nintendo.snes.rom`,
        `application/vnd.nitf`,
        `application/vnd.noblenet-directory`,
        `application/vnd.noblenet-sealer`,
        `application/vnd.noblenet-web`,
        `application/vnd.nokia.catalogs`,
        `application/vnd.nokia.conml+wbxml`,
        `application/vnd.nokia.conml+xml`,
        `application/vnd.nokia.iptv.config+xml`,
        `application/vnd.nokia.isds-radio-presets`,
        `application/vnd.nokia.landmark+wbxml`,
        `application/vnd.nokia.landmark+xml`,
        `application/vnd.nokia.landmarkcollection+xml`,
        `application/vnd.nokia.n-gage.ac+xml`,
        `application/vnd.nokia.n-gage.data`,
        `application/vnd.nokia.n-gage.symbian.install`,
        `application/vnd.nokia.ncd`,
        `application/vnd.nokia.pcd+wbxml`,
        `application/vnd.nokia.pcd+xml`,
        `application/vnd.nokia.radio-preset`,
        `application/vnd.nokia.radio-presets`,
        `application/vnd.novadigm.edm`,
        `application/vnd.novadigm.edx`,
        `application/vnd.novadigm.ext`,
        `application/vnd.ntt-local.content-share`,
        `application/vnd.ntt-local.file-transfer`,
        `application/vnd.ntt-local.ogw_remote-access`,
        `application/vnd.ntt-local.sip-ta_remote`,
        `application/vnd.ntt-local.sip-ta_tcp_stream`,
        `application/vnd.oasis.opendocument.chart`,
        `application/vnd.oasis.opendocument.chart-template`,
        `application/vnd.oasis.opendocument.database`,
        `application/vnd.oasis.opendocument.formula`,
        `application/vnd.oasis.opendocument.formula-template`,
        `application/vnd.oasis.opendocument.graphics`,
        `application/vnd.oasis.opendocument.graphics-template`,
        `application/vnd.oasis.opendocument.image`,
        `application/vnd.oasis.opendocument.image-template`,
        `application/vnd.oasis.opendocument.presentation`,
        `application/vnd.oasis.opendocument.presentation-template`,
        `application/vnd.oasis.opendocument.spreadsheet`,
        `application/vnd.oasis.opendocument.spreadsheet-template`,
        `application/vnd.oasis.opendocument.text`,
        `application/vnd.oasis.opendocument.text-master`,
        `application/vnd.oasis.opendocument.text-template`,
        `application/vnd.oasis.opendocument.text-web`,
        `application/vnd.obn`,
        `application/vnd.ocf+cbor`,
        `application/vnd.oftn.l10n+json`,
        `application/vnd.oipf.contentaccessdownload+xml`,
        `application/vnd.oipf.contentaccessstreaming+xml`,
        `application/vnd.oipf.cspg-hexbinary`,
        `application/vnd.oipf.dae.svg+xml`,
        `application/vnd.oipf.dae.xhtml+xml`,
        `application/vnd.oipf.mippvcontrolmessage+xml`,
        `application/vnd.oipf.pae.gem`,
        `application/vnd.oipf.spdiscovery+xml`,
        `application/vnd.oipf.spdlist+xml`,
        `application/vnd.oipf.ueprofile+xml`,
        `application/vnd.oipf.userprofile+xml`,
        `application/vnd.olpc-sugar`,
        `application/vnd.oma-scws-config`,
        `application/vnd.oma-scws-http-request`,
        `application/vnd.oma-scws-http-response`,
        `application/vnd.oma.bcast.associated-procedure-parameter+xml`,
        `application/vnd.oma.bcast.drm-trigger+xml`,
        `application/vnd.oma.bcast.imd+xml`,
        `application/vnd.oma.bcast.ltkm`,
        `application/vnd.oma.bcast.notification+xml`,
        `application/vnd.oma.bcast.provisioningtrigger`,
        `application/vnd.oma.bcast.sgboot`,
        `application/vnd.oma.bcast.sgdd+xml`,
        `application/vnd.oma.bcast.sgdu`,
        `application/vnd.oma.bcast.simple-symbol-container`,
        `application/vnd.oma.bcast.smartcard-trigger+xml`,
        `application/vnd.oma.bcast.sprov+xml`,
        `application/vnd.oma.bcast.stkm`,
        `application/vnd.oma.cab-address-book+xml`,
        `application/vnd.oma.cab-feature-handler+xml`,
        `application/vnd.oma.cab-pcc+xml`,
        `application/vnd.oma.cab-subs-invite+xml`,
        `application/vnd.oma.cab-user-prefs+xml`,
        `application/vnd.oma.dcd`,
        `application/vnd.oma.dcdc`,
        `application/vnd.oma.dd2+xml`,
        `application/vnd.oma.drm.risd+xml`,
        `application/vnd.oma.group-usage-list+xml`,
        `application/vnd.oma.lwm2m+json`,
        `application/vnd.oma.lwm2m+tlv`,
        `application/vnd.oma.pal+xml`,
        `application/vnd.oma.poc.detailed-progress-report+xml`,
        `application/vnd.oma.poc.final-report+xml`,
        `application/vnd.oma.poc.groups+xml`,
        `application/vnd.oma.poc.invocation-descriptor+xml`,
        `application/vnd.oma.poc.optimized-progress-report+xml`,
        `application/vnd.oma.push`,
        `application/vnd.oma.scidm.messages+xml`,
        `application/vnd.oma.xcap-directory+xml`,
        `application/vnd.omads-email+xml`,
        `application/vnd.omads-file+xml`,
        `application/vnd.omads-folder+xml`,
        `application/vnd.omaloc-supl-init`,
        `application/vnd.onepager`,
        `application/vnd.onepagertamp`,
        `application/vnd.onepagertamx`,
        `application/vnd.onepagertat`,
        `application/vnd.onepagertatp`,
        `application/vnd.onepagertatx`,
        `application/vnd.openblox.game+xml`,
        `application/vnd.openblox.game-binary`,
        `application/vnd.openeye.oeb`,
        `application/vnd.openofficeorg.extension`,
        `application/vnd.openstreetmap.data+xml`,
        `application/vnd.openxmlformats-officedocument.custom-properties+xml`,
        `application/vnd.openxmlformats-officedocument.customxmlproperties+xml`,
        `application/vnd.openxmlformats-officedocument.drawing+xml`,
        `application/vnd.openxmlformats-officedocument.drawingml.chart+xml`,
        `application/vnd.openxmlformats-officedocument.drawingml.chartshapes+xml`
      )
    }
    object application_2 {
      val mainType: String = "application"
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.diagramdata+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.drawingml.diagramdata+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.extended-properties+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.extended-properties+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.commentauthors+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.commentauthors+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.comments+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.comments+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.notesmaster+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.notesmaster+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.notesslide+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.notesslide+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.presentation`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.presentation",
        Uncompressible,
        Binary,
        List("pptx"))
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.presprops+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.presprops+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slide`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.presentationml.slide",
          Compressible,
          Binary,
          List("sldx"))
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slide+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.presentationml.slide+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slidelayout+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.slidelayout+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slidemaster+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.slidemaster+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slideshow`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.presentationml.slideshow",
          Compressible,
          Binary,
          List("ppsx"))
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.tablestyles+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.tablestyles+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.tags+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.presentationml.tags+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.template`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.presentationml.template",
          Compressible,
          Binary,
          List("potx"))
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.template.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.template.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.presentationml.viewprops+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.presentationml.viewprops+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.comments+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.connections+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.connections+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          Uncompressible,
          Binary,
          List("xlsx"))
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.spreadsheetml.table+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.template`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.spreadsheetml.template",
          Compressible,
          Binary,
          List("xltx"))
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.theme+xml`: MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.theme+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.themeoverride+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.themeoverride+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.vmldrawing`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-officedocument.vmldrawing",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.document",
        Uncompressible,
        Binary,
        List("docx"))
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.footer+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.settings+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.styles+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.template`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.template",
        Compressible,
        Binary,
        List("dotx"))
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-package.core-properties+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-package.core-properties+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml`
        : MediaType = new MediaType(
        mainType,
        "vnd.openxmlformats-package.digital-signature-xmlsignature+xml",
        Compressible,
        NotBinary)
      lazy val `application/vnd.openxmlformats-package.relationships+xml`: MediaType =
        new MediaType(
          mainType,
          "vnd.openxmlformats-package.relationships+xml",
          Compressible,
          NotBinary)
      lazy val `application/vnd.oracle.resource+json`: MediaType =
        new MediaType(mainType, "vnd.oracle.resource+json", Compressible, NotBinary)
      lazy val `application/vnd.orange.indata`: MediaType =
        new MediaType(mainType, "vnd.orange.indata", Compressible, NotBinary)
      lazy val `application/vnd.osa.netdeploy`: MediaType =
        new MediaType(mainType, "vnd.osa.netdeploy", Compressible, NotBinary)
      lazy val `application/vnd.osgeo.mapguide.package`: MediaType =
        new MediaType(mainType, "vnd.osgeo.mapguide.package", Compressible, NotBinary, List("mgp"))
      lazy val `application/vnd.osgi.bundle`: MediaType =
        new MediaType(mainType, "vnd.osgi.bundle", Compressible, NotBinary)
      lazy val `application/vnd.osgi.dp`: MediaType =
        new MediaType(mainType, "vnd.osgi.dp", Compressible, NotBinary, List("dp"))
      lazy val `application/vnd.osgi.subsystem`: MediaType =
        new MediaType(mainType, "vnd.osgi.subsystem", Compressible, NotBinary, List("esa"))
      lazy val `application/vnd.otps.ct-kip+xml`: MediaType =
        new MediaType(mainType, "vnd.otps.ct-kip+xml", Compressible, NotBinary)
      lazy val `application/vnd.oxli.countgraph`: MediaType =
        new MediaType(mainType, "vnd.oxli.countgraph", Compressible, NotBinary)
      lazy val `application/vnd.pagerduty+json`: MediaType =
        new MediaType(mainType, "vnd.pagerduty+json", Compressible, NotBinary)
      lazy val `application/vnd.palm`: MediaType =
        new MediaType(mainType, "vnd.palm", Compressible, NotBinary, List("pdb", "pqa", "oprc"))
      lazy val `application/vnd.panoply`: MediaType =
        new MediaType(mainType, "vnd.panoply", Compressible, NotBinary)
      lazy val `application/vnd.paos+xml`: MediaType =
        new MediaType(mainType, "vnd.paos+xml", Compressible, NotBinary)
      lazy val `application/vnd.paos.xml`: MediaType =
        new MediaType(mainType, "vnd.paos.xml", Compressible, NotBinary)
      lazy val `application/vnd.patentdive`: MediaType =
        new MediaType(mainType, "vnd.patentdive", Compressible, NotBinary)
      lazy val `application/vnd.pawaafile`: MediaType =
        new MediaType(mainType, "vnd.pawaafile", Compressible, NotBinary, List("paw"))
      lazy val `application/vnd.pcos`: MediaType =
        new MediaType(mainType, "vnd.pcos", Compressible, NotBinary)
      lazy val `application/vnd.pg.format`: MediaType =
        new MediaType(mainType, "vnd.pg.format", Compressible, NotBinary, List("str"))
      lazy val `application/vnd.pg.osasli`: MediaType =
        new MediaType(mainType, "vnd.pg.osasli", Compressible, NotBinary, List("ei6"))
      lazy val `application/vnd.piaccess.application-licence`: MediaType =
        new MediaType(mainType, "vnd.piaccess.application-licence", Compressible, NotBinary)
      lazy val `application/vnd.picsel`: MediaType =
        new MediaType(mainType, "vnd.picsel", Compressible, NotBinary, List("efif"))
      lazy val `application/vnd.pmi.widget`: MediaType =
        new MediaType(mainType, "vnd.pmi.widget", Compressible, NotBinary, List("wg"))
      lazy val `application/vnd.poc.group-advertisement+xml`: MediaType =
        new MediaType(mainType, "vnd.poc.group-advertisement+xml", Compressible, NotBinary)
      lazy val `application/vnd.pocketlearn`: MediaType =
        new MediaType(mainType, "vnd.pocketlearn", Compressible, NotBinary, List("plf"))
      lazy val `application/vnd.powerbuilder6`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder6", Compressible, NotBinary, List("pbd"))
      lazy val `application/vnd.powerbuilder6-s`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder6-s", Compressible, NotBinary)
      lazy val `application/vnd.powerbuilder7`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder7", Compressible, NotBinary)
      lazy val `application/vnd.powerbuilder7-s`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder7-s", Compressible, NotBinary)
      lazy val `application/vnd.powerbuilder75`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder75", Compressible, NotBinary)
      lazy val `application/vnd.powerbuilder75-s`: MediaType =
        new MediaType(mainType, "vnd.powerbuilder75-s", Compressible, NotBinary)
      lazy val `application/vnd.preminet`: MediaType =
        new MediaType(mainType, "vnd.preminet", Compressible, NotBinary)
      lazy val `application/vnd.previewsystems.box`: MediaType =
        new MediaType(mainType, "vnd.previewsystems.box", Compressible, NotBinary, List("box"))
      lazy val `application/vnd.proteus.magazine`: MediaType =
        new MediaType(mainType, "vnd.proteus.magazine", Compressible, NotBinary, List("mgz"))
      lazy val `application/vnd.publishare-delta-tree`: MediaType =
        new MediaType(mainType, "vnd.publishare-delta-tree", Compressible, NotBinary, List("qps"))
      lazy val `application/vnd.pvi.ptid1`: MediaType =
        new MediaType(mainType, "vnd.pvi.ptid1", Compressible, NotBinary, List("ptid"))
      lazy val `application/vnd.pwg-multiplexed`: MediaType =
        new MediaType(mainType, "vnd.pwg-multiplexed", Compressible, NotBinary)
      lazy val `application/vnd.pwg-xhtml-print+xml`: MediaType =
        new MediaType(mainType, "vnd.pwg-xhtml-print+xml", Compressible, NotBinary)
      lazy val `application/vnd.qualcomm.brew-app-res`: MediaType =
        new MediaType(mainType, "vnd.qualcomm.brew-app-res", Compressible, NotBinary)
      lazy val `application/vnd.quarantainenet`: MediaType =
        new MediaType(mainType, "vnd.quarantainenet", Compressible, NotBinary)
      lazy val `application/vnd.quark.quarkxpress`: MediaType = new MediaType(
        mainType,
        "vnd.quark.quarkxpress",
        Compressible,
        NotBinary,
        List("qxd", "qxt", "qwd", "qwt", "qxl", "qxb"))
      lazy val `application/vnd.quobject-quoxdocument`: MediaType =
        new MediaType(mainType, "vnd.quobject-quoxdocument", Compressible, NotBinary)
      lazy val `application/vnd.radisys.moml+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.moml+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-audit+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-audit+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-audit-conf+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-audit-conf+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-audit-conn+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-audit-conn+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-audit-dialog+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-audit-dialog+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-audit-stream+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-audit-stream+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-conf+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-conf+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-base+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-base+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-fax-detect+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-fax-detect+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-fax-sendrecv+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-fax-sendrecv+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-group+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-group+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-speech+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-speech+xml", Compressible, NotBinary)
      lazy val `application/vnd.radisys.msml-dialog-transform+xml`: MediaType =
        new MediaType(mainType, "vnd.radisys.msml-dialog-transform+xml", Compressible, NotBinary)
      lazy val `application/vnd.rainstor.data`: MediaType =
        new MediaType(mainType, "vnd.rainstor.data", Compressible, NotBinary)
      lazy val `application/vnd.rapid`: MediaType =
        new MediaType(mainType, "vnd.rapid", Compressible, NotBinary)
      lazy val `application/vnd.rar`: MediaType =
        new MediaType(mainType, "vnd.rar", Compressible, NotBinary)
      lazy val `application/vnd.realvnc.bed`: MediaType =
        new MediaType(mainType, "vnd.realvnc.bed", Compressible, NotBinary, List("bed"))
      lazy val `application/vnd.recordare.musicxml`: MediaType =
        new MediaType(mainType, "vnd.recordare.musicxml", Compressible, NotBinary, List("mxl"))
      lazy val `application/vnd.recordare.musicxml+xml`: MediaType = new MediaType(
        mainType,
        "vnd.recordare.musicxml+xml",
        Compressible,
        NotBinary,
        List("musicxml"))
      lazy val `application/vnd.renlearn.rlprint`: MediaType =
        new MediaType(mainType, "vnd.renlearn.rlprint", Compressible, NotBinary)
      lazy val `application/vnd.restful+json`: MediaType =
        new MediaType(mainType, "vnd.restful+json", Compressible, NotBinary)
      lazy val `application/vnd.rig.cryptonote`: MediaType =
        new MediaType(mainType, "vnd.rig.cryptonote", Compressible, NotBinary, List("cryptonote"))
      lazy val `application/vnd.rim.cod`: MediaType =
        new MediaType(mainType, "vnd.rim.cod", Compressible, NotBinary, List("cod"))
      lazy val `application/vnd.rn-realmedia`: MediaType =
        new MediaType(mainType, "vnd.rn-realmedia", Compressible, NotBinary, List("rm"))
      lazy val `application/vnd.rn-realmedia-vbr`: MediaType =
        new MediaType(mainType, "vnd.rn-realmedia-vbr", Compressible, NotBinary, List("rmvb"))
      lazy val `application/vnd.route66.link66+xml`: MediaType =
        new MediaType(mainType, "vnd.route66.link66+xml", Compressible, NotBinary, List("link66"))
      lazy val `application/vnd.rs-274x`: MediaType =
        new MediaType(mainType, "vnd.rs-274x", Compressible, NotBinary)
      lazy val `application/vnd.ruckus.download`: MediaType =
        new MediaType(mainType, "vnd.ruckus.download", Compressible, NotBinary)
      lazy val `application/vnd.s3sms`: MediaType =
        new MediaType(mainType, "vnd.s3sms", Compressible, NotBinary)
      lazy val `application/vnd.sailingtracker.track`: MediaType =
        new MediaType(mainType, "vnd.sailingtracker.track", Compressible, NotBinary, List("st"))
      lazy val `application/vnd.sbm.cid`: MediaType =
        new MediaType(mainType, "vnd.sbm.cid", Compressible, NotBinary)
      lazy val `application/vnd.sbm.mid2`: MediaType =
        new MediaType(mainType, "vnd.sbm.mid2", Compressible, NotBinary)
      lazy val `application/vnd.scribus`: MediaType =
        new MediaType(mainType, "vnd.scribus", Compressible, NotBinary)
      lazy val `application/vnd.sealed.3df`: MediaType =
        new MediaType(mainType, "vnd.sealed.3df", Compressible, NotBinary)
      lazy val `application/vnd.sealed.csf`: MediaType =
        new MediaType(mainType, "vnd.sealed.csf", Compressible, NotBinary)
      lazy val `application/vnd.sealed.doc`: MediaType =
        new MediaType(mainType, "vnd.sealed.doc", Compressible, NotBinary)
      lazy val `application/vnd.sealed.eml`: MediaType =
        new MediaType(mainType, "vnd.sealed.eml", Compressible, NotBinary)
      lazy val `application/vnd.sealed.mht`: MediaType =
        new MediaType(mainType, "vnd.sealed.mht", Compressible, NotBinary)
      lazy val `application/vnd.sealed.net`: MediaType =
        new MediaType(mainType, "vnd.sealed.net", Compressible, NotBinary)
      lazy val `application/vnd.sealed.ppt`: MediaType =
        new MediaType(mainType, "vnd.sealed.ppt", Compressible, NotBinary)
      lazy val `application/vnd.sealed.tiff`: MediaType =
        new MediaType(mainType, "vnd.sealed.tiff", Compressible, NotBinary)
      lazy val `application/vnd.sealed.xls`: MediaType =
        new MediaType(mainType, "vnd.sealed.xls", Compressible, NotBinary)
      lazy val `application/vnd.sealedmedia.softseal.html`: MediaType =
        new MediaType(mainType, "vnd.sealedmedia.softseal.html", Compressible, NotBinary)
      lazy val `application/vnd.sealedmedia.softseal.pdf`: MediaType =
        new MediaType(mainType, "vnd.sealedmedia.softseal.pdf", Compressible, NotBinary)
      lazy val `application/vnd.seemail`: MediaType =
        new MediaType(mainType, "vnd.seemail", Compressible, NotBinary, List("see"))
      lazy val `application/vnd.sema`: MediaType =
        new MediaType(mainType, "vnd.sema", Compressible, NotBinary, List("sema"))
      lazy val `application/vnd.semd`: MediaType =
        new MediaType(mainType, "vnd.semd", Compressible, NotBinary, List("semd"))
      lazy val `application/vnd.semf`: MediaType =
        new MediaType(mainType, "vnd.semf", Compressible, NotBinary, List("semf"))
      lazy val `application/vnd.shana.informed.formdata`: MediaType =
        new MediaType(mainType, "vnd.shana.informed.formdata", Compressible, NotBinary, List("ifm"))
      lazy val `application/vnd.shana.informed.formtemplate`: MediaType = new MediaType(
        mainType,
        "vnd.shana.informed.formtemplate",
        Compressible,
        NotBinary,
        List("itp"))
      lazy val `application/vnd.shana.informed.interchange`: MediaType = new MediaType(
        mainType,
        "vnd.shana.informed.interchange",
        Compressible,
        NotBinary,
        List("iif"))
      lazy val `application/vnd.shana.informed.package`: MediaType =
        new MediaType(mainType, "vnd.shana.informed.package", Compressible, NotBinary, List("ipk"))
      lazy val `application/vnd.sigrok.session`: MediaType =
        new MediaType(mainType, "vnd.sigrok.session", Compressible, NotBinary)
      lazy val `application/vnd.simtech-mindmapper`: MediaType = new MediaType(
        mainType,
        "vnd.simtech-mindmapper",
        Compressible,
        NotBinary,
        List("twd", "twds"))
      lazy val `application/vnd.siren+json`: MediaType =
        new MediaType(mainType, "vnd.siren+json", Compressible, NotBinary)
      lazy val `application/vnd.smaf`: MediaType =
        new MediaType(mainType, "vnd.smaf", Compressible, NotBinary, List("mmf"))
      lazy val `application/vnd.smart.notebook`: MediaType =
        new MediaType(mainType, "vnd.smart.notebook", Compressible, NotBinary)
      lazy val `application/vnd.smart.teacher`: MediaType =
        new MediaType(mainType, "vnd.smart.teacher", Compressible, NotBinary, List("teacher"))
      lazy val `application/vnd.software602.filler.form+xml`: MediaType =
        new MediaType(mainType, "vnd.software602.filler.form+xml", Compressible, NotBinary)
      lazy val `application/vnd.software602.filler.form-xml-zip`: MediaType =
        new MediaType(mainType, "vnd.software602.filler.form-xml-zip", Compressible, NotBinary)
      lazy val `application/vnd.solent.sdkm+xml`: MediaType = new MediaType(
        mainType,
        "vnd.solent.sdkm+xml",
        Compressible,
        NotBinary,
        List("sdkm", "sdkd"))
      lazy val `application/vnd.spotfire.dxp`: MediaType =
        new MediaType(mainType, "vnd.spotfire.dxp", Compressible, NotBinary, List("dxp"))
      lazy val `application/vnd.spotfire.sfs`: MediaType =
        new MediaType(mainType, "vnd.spotfire.sfs", Compressible, NotBinary, List("sfs"))
      lazy val `application/vnd.sqlite3`: MediaType =
        new MediaType(mainType, "vnd.sqlite3", Compressible, NotBinary)
      lazy val `application/vnd.sss-cod`: MediaType =
        new MediaType(mainType, "vnd.sss-cod", Compressible, NotBinary)
      lazy val `application/vnd.sss-dtf`: MediaType =
        new MediaType(mainType, "vnd.sss-dtf", Compressible, NotBinary)
      lazy val `application/vnd.sss-ntf`: MediaType =
        new MediaType(mainType, "vnd.sss-ntf", Compressible, NotBinary)
      lazy val `application/vnd.stardivision.calc`: MediaType =
        new MediaType(mainType, "vnd.stardivision.calc", Compressible, NotBinary, List("sdc"))
      lazy val `application/vnd.stardivision.draw`: MediaType =
        new MediaType(mainType, "vnd.stardivision.draw", Compressible, NotBinary, List("sda"))
      lazy val `application/vnd.stardivision.impress`: MediaType =
        new MediaType(mainType, "vnd.stardivision.impress", Compressible, NotBinary, List("sdd"))
      lazy val `application/vnd.stardivision.math`: MediaType =
        new MediaType(mainType, "vnd.stardivision.math", Compressible, NotBinary, List("smf"))
      lazy val `application/vnd.stardivision.writer`: MediaType = new MediaType(
        mainType,
        "vnd.stardivision.writer",
        Compressible,
        NotBinary,
        List("sdw", "vor"))
      lazy val `application/vnd.stardivision.writer-global`: MediaType = new MediaType(
        mainType,
        "vnd.stardivision.writer-global",
        Compressible,
        NotBinary,
        List("sgl"))
      lazy val `application/vnd.stepmania.package`: MediaType =
        new MediaType(mainType, "vnd.stepmania.package", Compressible, NotBinary, List("smzip"))
      lazy val `application/vnd.stepmania.stepchart`: MediaType =
        new MediaType(mainType, "vnd.stepmania.stepchart", Compressible, NotBinary, List("sm"))
      lazy val `application/vnd.street-stream`: MediaType =
        new MediaType(mainType, "vnd.street-stream", Compressible, NotBinary)
      lazy val `application/vnd.sun.wadl+xml`: MediaType =
        new MediaType(mainType, "vnd.sun.wadl+xml", Compressible, NotBinary, List("wadl"))
      lazy val `application/vnd.sun.xml.calc`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.calc", Compressible, NotBinary, List("sxc"))
      lazy val `application/vnd.sun.xml.calc.template`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.calc.template", Compressible, NotBinary, List("stc"))
      lazy val `application/vnd.sun.xml.draw`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.draw", Compressible, NotBinary, List("sxd"))
      lazy val `application/vnd.sun.xml.draw.template`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.draw.template", Compressible, NotBinary, List("std"))
      lazy val `application/vnd.sun.xml.impress`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.impress", Compressible, NotBinary, List("sxi"))
      lazy val `application/vnd.sun.xml.impress.template`: MediaType = new MediaType(
        mainType,
        "vnd.sun.xml.impress.template",
        Compressible,
        NotBinary,
        List("sti"))
      lazy val `application/vnd.sun.xml.math`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.math", Compressible, NotBinary, List("sxm"))
      lazy val `application/vnd.sun.xml.writer`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.writer", Compressible, NotBinary, List("sxw"))
      lazy val `application/vnd.sun.xml.writer.global`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.writer.global", Compressible, NotBinary, List("sxg"))
      lazy val `application/vnd.sun.xml.writer.template`: MediaType =
        new MediaType(mainType, "vnd.sun.xml.writer.template", Compressible, NotBinary, List("stw"))
      lazy val `application/vnd.sus-calendar`: MediaType =
        new MediaType(mainType, "vnd.sus-calendar", Compressible, NotBinary, List("sus", "susp"))
      lazy val `application/vnd.svd`: MediaType =
        new MediaType(mainType, "vnd.svd", Compressible, NotBinary, List("svd"))
      lazy val `application/vnd.swiftview-ics`: MediaType =
        new MediaType(mainType, "vnd.swiftview-ics", Compressible, NotBinary)
      lazy val `application/vnd.symbian.install`: MediaType =
        new MediaType(mainType, "vnd.symbian.install", Compressible, NotBinary, List("sis", "sisx"))
      lazy val `application/vnd.syncml+xml`: MediaType =
        new MediaType(mainType, "vnd.syncml+xml", Compressible, NotBinary, List("xsm"))
      lazy val `application/vnd.syncml.dm+wbxml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dm+wbxml", Compressible, NotBinary, List("bdm"))
      lazy val `application/vnd.syncml.dm+xml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dm+xml", Compressible, NotBinary, List("xdm"))
      lazy val `application/vnd.syncml.dm.notification`: MediaType =
        new MediaType(mainType, "vnd.syncml.dm.notification", Compressible, NotBinary)
      lazy val `application/vnd.syncml.dmddf+wbxml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dmddf+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.syncml.dmddf+xml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dmddf+xml", Compressible, NotBinary)
      lazy val `application/vnd.syncml.dmtnds+wbxml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dmtnds+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.syncml.dmtnds+xml`: MediaType =
        new MediaType(mainType, "vnd.syncml.dmtnds+xml", Compressible, NotBinary)
      lazy val `application/vnd.syncml.ds.notification`: MediaType =
        new MediaType(mainType, "vnd.syncml.ds.notification", Compressible, NotBinary)
      lazy val `application/vnd.tableschema+json`: MediaType =
        new MediaType(mainType, "vnd.tableschema+json", Compressible, NotBinary)
      lazy val `application/vnd.tao.intent-module-archive`: MediaType = new MediaType(
        mainType,
        "vnd.tao.intent-module-archive",
        Compressible,
        NotBinary,
        List("tao"))
      lazy val `application/vnd.tcpdump.pcap`: MediaType = new MediaType(
        mainType,
        "vnd.tcpdump.pcap",
        Compressible,
        NotBinary,
        List("pcap", "cap", "dmp"))
      lazy val `application/vnd.tmd.mediaflex.api+xml`: MediaType =
        new MediaType(mainType, "vnd.tmd.mediaflex.api+xml", Compressible, NotBinary)
      lazy val `application/vnd.tml`: MediaType =
        new MediaType(mainType, "vnd.tml", Compressible, NotBinary)
      lazy val `application/vnd.tmobile-livetv`: MediaType =
        new MediaType(mainType, "vnd.tmobile-livetv", Compressible, NotBinary, List("tmo"))
      lazy val `application/vnd.tri.onesource`: MediaType =
        new MediaType(mainType, "vnd.tri.onesource", Compressible, NotBinary)
      lazy val `application/vnd.trid.tpt`: MediaType =
        new MediaType(mainType, "vnd.trid.tpt", Compressible, NotBinary, List("tpt"))
      lazy val `application/vnd.triscape.mxs`: MediaType =
        new MediaType(mainType, "vnd.triscape.mxs", Compressible, NotBinary, List("mxs"))
      lazy val `application/vnd.trueapp`: MediaType =
        new MediaType(mainType, "vnd.trueapp", Compressible, NotBinary, List("tra"))
      lazy val `application/vnd.truedoc`: MediaType =
        new MediaType(mainType, "vnd.truedoc", Compressible, NotBinary)
      lazy val `application/vnd.ubisoft.webplayer`: MediaType =
        new MediaType(mainType, "vnd.ubisoft.webplayer", Compressible, NotBinary)
      lazy val `application/vnd.ufdl`: MediaType =
        new MediaType(mainType, "vnd.ufdl", Compressible, NotBinary, List("ufd", "ufdl"))
      lazy val `application/vnd.uiq.theme`: MediaType =
        new MediaType(mainType, "vnd.uiq.theme", Compressible, NotBinary, List("utz"))
      lazy val `application/vnd.umajin`: MediaType =
        new MediaType(mainType, "vnd.umajin", Compressible, NotBinary, List("umj"))
      lazy val `application/vnd.unity`: MediaType =
        new MediaType(mainType, "vnd.unity", Compressible, NotBinary, List("unityweb"))
      lazy val `application/vnd.uoml+xml`: MediaType =
        new MediaType(mainType, "vnd.uoml+xml", Compressible, NotBinary, List("uoml"))
      lazy val `application/vnd.uplanet.alert`: MediaType =
        new MediaType(mainType, "vnd.uplanet.alert", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.alert-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.alert-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.bearer-choice`: MediaType =
        new MediaType(mainType, "vnd.uplanet.bearer-choice", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.bearer-choice-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.bearer-choice-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.cacheop`: MediaType =
        new MediaType(mainType, "vnd.uplanet.cacheop", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.cacheop-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.cacheop-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.channel`: MediaType =
        new MediaType(mainType, "vnd.uplanet.channel", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.channel-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.channel-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.list`: MediaType =
        new MediaType(mainType, "vnd.uplanet.list", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.list-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.list-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.listcmd`: MediaType =
        new MediaType(mainType, "vnd.uplanet.listcmd", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.listcmd-wbxml`: MediaType =
        new MediaType(mainType, "vnd.uplanet.listcmd-wbxml", Compressible, NotBinary)
      lazy val `application/vnd.uplanet.signal`: MediaType =
        new MediaType(mainType, "vnd.uplanet.signal", Compressible, NotBinary)
      lazy val `application/vnd.uri-map`: MediaType =
        new MediaType(mainType, "vnd.uri-map", Compressible, NotBinary)
      lazy val `application/vnd.valve.source.material`: MediaType =
        new MediaType(mainType, "vnd.valve.source.material", Compressible, NotBinary)
      lazy val `application/vnd.vcx`: MediaType =
        new MediaType(mainType, "vnd.vcx", Compressible, NotBinary, List("vcx"))
      lazy val `application/vnd.vd-study`: MediaType =
        new MediaType(mainType, "vnd.vd-study", Compressible, NotBinary)
      lazy val `application/vnd.vectorworks`: MediaType =
        new MediaType(mainType, "vnd.vectorworks", Compressible, NotBinary)
      lazy val `application/vnd.vel+json`: MediaType =
        new MediaType(mainType, "vnd.vel+json", Compressible, NotBinary)
      lazy val `application/vnd.verimatrix.vcas`: MediaType =
        new MediaType(mainType, "vnd.verimatrix.vcas", Compressible, NotBinary)
      lazy val `application/vnd.vidsoft.vidconference`: MediaType =
        new MediaType(mainType, "vnd.vidsoft.vidconference", Compressible, NotBinary)
      lazy val `application/vnd.visio`: MediaType = new MediaType(
        mainType,
        "vnd.visio",
        Compressible,
        NotBinary,
        List("vsd", "vst", "vss", "vsw"))
      lazy val `application/vnd.visionary`: MediaType =
        new MediaType(mainType, "vnd.visionary", Compressible, NotBinary, List("vis"))
      lazy val `application/vnd.vividence.scriptfile`: MediaType =
        new MediaType(mainType, "vnd.vividence.scriptfile", Compressible, NotBinary)
      lazy val `application/vnd.vsf`: MediaType =
        new MediaType(mainType, "vnd.vsf", Compressible, NotBinary, List("vsf"))
      lazy val `application/vnd.wap.sic`: MediaType =
        new MediaType(mainType, "vnd.wap.sic", Compressible, NotBinary)
      lazy val `application/vnd.wap.slc`: MediaType =
        new MediaType(mainType, "vnd.wap.slc", Compressible, NotBinary)
      lazy val `application/vnd.wap.wbxml`: MediaType =
        new MediaType(mainType, "vnd.wap.wbxml", Compressible, NotBinary, List("wbxml"))
      lazy val `application/vnd.wap.wmlc`: MediaType =
        new MediaType(mainType, "vnd.wap.wmlc", Compressible, NotBinary, List("wmlc"))
      lazy val `application/vnd.wap.wmlscriptc`: MediaType =
        new MediaType(mainType, "vnd.wap.wmlscriptc", Compressible, NotBinary, List("wmlsc"))
      lazy val `application/vnd.webturbo`: MediaType =
        new MediaType(mainType, "vnd.webturbo", Compressible, NotBinary, List("wtb"))
      lazy val `application/vnd.wfa.p2p`: MediaType =
        new MediaType(mainType, "vnd.wfa.p2p", Compressible, NotBinary)
      lazy val `application/vnd.wfa.wsc`: MediaType =
        new MediaType(mainType, "vnd.wfa.wsc", Compressible, NotBinary)
      lazy val `application/vnd.windows.devicepairing`: MediaType =
        new MediaType(mainType, "vnd.windows.devicepairing", Compressible, NotBinary)
      lazy val `application/vnd.wmc`: MediaType =
        new MediaType(mainType, "vnd.wmc", Compressible, NotBinary)
      lazy val `application/vnd.wmf.bootstrap`: MediaType =
        new MediaType(mainType, "vnd.wmf.bootstrap", Compressible, NotBinary)
      lazy val `application/vnd.wolfram.mathematica`: MediaType =
        new MediaType(mainType, "vnd.wolfram.mathematica", Compressible, NotBinary)
      lazy val `application/vnd.wolfram.mathematica.package`: MediaType =
        new MediaType(mainType, "vnd.wolfram.mathematica.package", Compressible, NotBinary)
      lazy val `application/vnd.wolfram.player`: MediaType =
        new MediaType(mainType, "vnd.wolfram.player", Compressible, NotBinary, List("nbp"))
      lazy val `application/vnd.wordperfect`: MediaType =
        new MediaType(mainType, "vnd.wordperfect", Compressible, NotBinary, List("wpd"))
      lazy val `application/vnd.wqd`: MediaType =
        new MediaType(mainType, "vnd.wqd", Compressible, NotBinary, List("wqd"))
      lazy val `application/vnd.wrq-hp3000-labelled`: MediaType =
        new MediaType(mainType, "vnd.wrq-hp3000-labelled", Compressible, NotBinary)
      lazy val `application/vnd.wt.stf`: MediaType =
        new MediaType(mainType, "vnd.wt.stf", Compressible, NotBinary, List("stf"))
      lazy val `application/vnd.wv.csp+wbxml`: MediaType =
        new MediaType(mainType, "vnd.wv.csp+wbxml", Compressible, NotBinary)
      lazy val `application/vnd.wv.csp+xml`: MediaType =
        new MediaType(mainType, "vnd.wv.csp+xml", Compressible, NotBinary)
      lazy val `application/vnd.wv.ssp+xml`: MediaType =
        new MediaType(mainType, "vnd.wv.ssp+xml", Compressible, NotBinary)
      lazy val `application/vnd.xacml+json`: MediaType =
        new MediaType(mainType, "vnd.xacml+json", Compressible, NotBinary)
      lazy val `application/vnd.xara`: MediaType =
        new MediaType(mainType, "vnd.xara", Compressible, NotBinary, List("xar"))
      lazy val `application/vnd.xfdl`: MediaType =
        new MediaType(mainType, "vnd.xfdl", Compressible, NotBinary, List("xfdl"))
      lazy val `application/vnd.xfdl.webform`: MediaType =
        new MediaType(mainType, "vnd.xfdl.webform", Compressible, NotBinary)
      lazy val `application/vnd.xmi+xml`: MediaType =
        new MediaType(mainType, "vnd.xmi+xml", Compressible, NotBinary)
      lazy val `application/vnd.xmpie.cpkg`: MediaType =
        new MediaType(mainType, "vnd.xmpie.cpkg", Compressible, NotBinary)
      lazy val `application/vnd.xmpie.dpkg`: MediaType =
        new MediaType(mainType, "vnd.xmpie.dpkg", Compressible, NotBinary)
      lazy val `application/vnd.xmpie.plan`: MediaType =
        new MediaType(mainType, "vnd.xmpie.plan", Compressible, NotBinary)
      lazy val `application/vnd.xmpie.ppkg`: MediaType =
        new MediaType(mainType, "vnd.xmpie.ppkg", Compressible, NotBinary)
      lazy val `application/vnd.xmpie.xlim`: MediaType =
        new MediaType(mainType, "vnd.xmpie.xlim", Compressible, NotBinary)
      lazy val `application/vnd.yamaha.hv-dic`: MediaType =
        new MediaType(mainType, "vnd.yamaha.hv-dic", Compressible, NotBinary, List("hvd"))
      lazy val `application/vnd.yamaha.hv-script`: MediaType =
        new MediaType(mainType, "vnd.yamaha.hv-script", Compressible, NotBinary, List("hvs"))
      lazy val `application/vnd.yamaha.hv-voice`: MediaType =
        new MediaType(mainType, "vnd.yamaha.hv-voice", Compressible, NotBinary, List("hvp"))
      lazy val `application/vnd.yamaha.openscoreformat`: MediaType =
        new MediaType(mainType, "vnd.yamaha.openscoreformat", Compressible, NotBinary, List("osf"))
      lazy val `application/vnd.yamaha.openscoreformat.osfpvg+xml`: MediaType = new MediaType(
        mainType,
        "vnd.yamaha.openscoreformat.osfpvg+xml",
        Compressible,
        NotBinary,
        List("osfpvg"))
      lazy val `application/vnd.yamaha.remote-setup`: MediaType =
        new MediaType(mainType, "vnd.yamaha.remote-setup", Compressible, NotBinary)
      lazy val `application/vnd.yamaha.smaf-audio`: MediaType =
        new MediaType(mainType, "vnd.yamaha.smaf-audio", Compressible, NotBinary, List("saf"))
      lazy val `application/vnd.yamaha.smaf-phrase`: MediaType =
        new MediaType(mainType, "vnd.yamaha.smaf-phrase", Compressible, NotBinary, List("spf"))
      lazy val `application/vnd.yamaha.through-ngn`: MediaType =
        new MediaType(mainType, "vnd.yamaha.through-ngn", Compressible, NotBinary)
      lazy val `application/vnd.yamaha.tunnel-udpencap`: MediaType =
        new MediaType(mainType, "vnd.yamaha.tunnel-udpencap", Compressible, NotBinary)
      lazy val `application/vnd.yaoweme`: MediaType =
        new MediaType(mainType, "vnd.yaoweme", Compressible, NotBinary)
      lazy val `application/vnd.yellowriver-custom-menu`: MediaType =
        new MediaType(mainType, "vnd.yellowriver-custom-menu", Compressible, NotBinary, List("cmp"))
      lazy val `application/vnd.youtube.yt`: MediaType =
        new MediaType(mainType, "vnd.youtube.yt", Compressible, NotBinary)
      lazy val `application/vnd.zul`: MediaType =
        new MediaType(mainType, "vnd.zul", Compressible, NotBinary, List("zir", "zirz"))
      lazy val `application/vnd.zzazz.deck+xml`: MediaType =
        new MediaType(mainType, "vnd.zzazz.deck+xml", Compressible, NotBinary, List("zaz"))
      lazy val `application/voicexml+xml`: MediaType =
        new MediaType(mainType, "voicexml+xml", Compressible, NotBinary, List("vxml"))
      lazy val `application/voucher-cms+json`: MediaType =
        new MediaType(mainType, "voucher-cms+json", Compressible, NotBinary)
      lazy val `application/vq-rtcpxr`: MediaType =
        new MediaType(mainType, "vq-rtcpxr", Compressible, NotBinary)
      lazy val `application/wasm`: MediaType =
        new MediaType(mainType, "wasm", Compressible, NotBinary, List("wasm"))
      lazy val `application/watcherinfo+xml`: MediaType =
        new MediaType(mainType, "watcherinfo+xml", Compressible, NotBinary)
      lazy val `application/webpush-options+json`: MediaType =
        new MediaType(mainType, "webpush-options+json", Compressible, NotBinary)
      lazy val `application/whoispp-query`: MediaType =
        new MediaType(mainType, "whoispp-query", Compressible, NotBinary)
      lazy val `application/whoispp-response`: MediaType =
        new MediaType(mainType, "whoispp-response", Compressible, NotBinary)
      lazy val `application/widget`: MediaType =
        new MediaType(mainType, "widget", Compressible, NotBinary, List("wgt"))
      lazy val `application/winhlp`: MediaType =
        new MediaType(mainType, "winhlp", Compressible, NotBinary, List("hlp"))
      lazy val `application/wita`: MediaType =
        new MediaType(mainType, "wita", Compressible, NotBinary)
      lazy val `application/wordperfect5.1`: MediaType =
        new MediaType(mainType, "wordperfect5.1", Compressible, NotBinary)
      lazy val `application/wsdl+xml`: MediaType =
        new MediaType(mainType, "wsdl+xml", Compressible, NotBinary, List("wsdl"))
      lazy val `application/wspolicy+xml`: MediaType =
        new MediaType(mainType, "wspolicy+xml", Compressible, NotBinary, List("wspolicy"))
      lazy val `application/x-7z-compressed`: MediaType =
        new MediaType(mainType, "x-7z-compressed", Uncompressible, Binary, List("7z"))
      lazy val `application/x-abiword`: MediaType =
        new MediaType(mainType, "x-abiword", Compressible, NotBinary, List("abw"))
      lazy val `application/x-ace-compressed`: MediaType =
        new MediaType(mainType, "x-ace-compressed", Compressible, Binary, List("ace"))
      lazy val `application/x-amf`: MediaType =
        new MediaType(mainType, "x-amf", Compressible, NotBinary)
      lazy val `application/x-apple-diskimage`: MediaType =
        new MediaType(mainType, "x-apple-diskimage", Compressible, Binary, List("dmg"))
      lazy val `application/x-arj`: MediaType =
        new MediaType(mainType, "x-arj", Uncompressible, NotBinary, List("arj"))
      lazy val `application/x-authorware-bin`: MediaType = new MediaType(
        mainType,
        "x-authorware-bin",
        Compressible,
        NotBinary,
        List("aab", "x32", "u32", "vox"))
      lazy val `application/x-authorware-map`: MediaType =
        new MediaType(mainType, "x-authorware-map", Compressible, NotBinary, List("aam"))
      lazy val `application/x-authorware-seg`: MediaType =
        new MediaType(mainType, "x-authorware-seg", Compressible, NotBinary, List("aas"))
      lazy val `application/x-bcpio`: MediaType =
        new MediaType(mainType, "x-bcpio", Compressible, NotBinary, List("bcpio"))
      lazy val `application/x-bdoc`: MediaType =
        new MediaType(mainType, "x-bdoc", Uncompressible, NotBinary, List("bdoc"))
      lazy val `application/x-bittorrent`: MediaType =
        new MediaType(mainType, "x-bittorrent", Compressible, NotBinary, List("torrent"))
      lazy val `application/x-blorb`: MediaType =
        new MediaType(mainType, "x-blorb", Compressible, NotBinary, List("blb", "blorb"))
      lazy val `application/x-bzip`: MediaType =
        new MediaType(mainType, "x-bzip", Uncompressible, Binary, List("bz"))
      lazy val `application/x-bzip2`: MediaType =
        new MediaType(mainType, "x-bzip2", Uncompressible, Binary, List("bz2", "boz"))
      lazy val `application/x-cbr`: MediaType = new MediaType(
        mainType,
        "x-cbr",
        Compressible,
        NotBinary,
        List("cbr", "cba", "cbt", "cbz", "cb7"))
      lazy val `application/x-cdlink`: MediaType =
        new MediaType(mainType, "x-cdlink", Compressible, NotBinary, List("vcd"))
      lazy val `application/x-cfs-compressed`: MediaType =
        new MediaType(mainType, "x-cfs-compressed", Compressible, NotBinary, List("cfs"))
      lazy val `application/x-chat`: MediaType =
        new MediaType(mainType, "x-chat", Compressible, NotBinary, List("chat"))
      lazy val `application/x-chess-pgn`: MediaType =
        new MediaType(mainType, "x-chess-pgn", Compressible, NotBinary, List("pgn"))
      lazy val `application/x-chrome-extension`: MediaType =
        new MediaType(mainType, "x-chrome-extension", Compressible, Binary, List("crx"))
      lazy val `application/x-cocoa`: MediaType =
        new MediaType(mainType, "x-cocoa", Compressible, NotBinary, List("cco"))
      lazy val `application/x-compress`: MediaType =
        new MediaType(mainType, "x-compress", Compressible, Binary)
      lazy val `application/x-conference`: MediaType =
        new MediaType(mainType, "x-conference", Compressible, NotBinary, List("nsc"))
      lazy val `application/x-cpio`: MediaType =
        new MediaType(mainType, "x-cpio", Compressible, NotBinary, List("cpio"))
      lazy val `application/x-csh`: MediaType =
        new MediaType(mainType, "x-csh", Compressible, NotBinary, List("csh"))
      lazy val `application/x-deb`: MediaType =
        new MediaType(mainType, "x-deb", Uncompressible, NotBinary)
      lazy val `application/x-debian-package`: MediaType =
        new MediaType(mainType, "x-debian-package", Compressible, Binary, List("deb", "udeb"))
      lazy val `application/x-dgc-compressed`: MediaType =
        new MediaType(mainType, "x-dgc-compressed", Compressible, NotBinary, List("dgc"))
      lazy val `application/x-director`: MediaType = new MediaType(
        mainType,
        "x-director",
        Compressible,
        NotBinary,
        List("dir", "dcr", "dxr", "cst", "cct", "cxt", "w3d", "fgd", "swa"))
      lazy val `application/x-doom`: MediaType =
        new MediaType(mainType, "x-doom", Compressible, NotBinary, List("wad"))
      lazy val `application/x-dtbncx+xml`: MediaType =
        new MediaType(mainType, "x-dtbncx+xml", Compressible, NotBinary, List("ncx"))
      lazy val `application/x-dtbook+xml`: MediaType =
        new MediaType(mainType, "x-dtbook+xml", Compressible, NotBinary, List("dtb"))
      lazy val `application/x-dtbresource+xml`: MediaType =
        new MediaType(mainType, "x-dtbresource+xml", Compressible, NotBinary, List("res"))
      lazy val `application/x-dvi`: MediaType =
        new MediaType(mainType, "x-dvi", Uncompressible, Binary, List("dvi"))
      lazy val `application/x-envoy`: MediaType =
        new MediaType(mainType, "x-envoy", Compressible, NotBinary, List("evy"))
      lazy val `application/x-eva`: MediaType =
        new MediaType(mainType, "x-eva", Compressible, NotBinary, List("eva"))
      lazy val `application/x-font-bdf`: MediaType =
        new MediaType(mainType, "x-font-bdf", Compressible, NotBinary, List("bdf"))
      lazy val `application/x-font-dos`: MediaType =
        new MediaType(mainType, "x-font-dos", Compressible, NotBinary)
      lazy val `application/x-font-framemaker`: MediaType =
        new MediaType(mainType, "x-font-framemaker", Compressible, NotBinary)
      lazy val `application/x-font-ghostscript`: MediaType =
        new MediaType(mainType, "x-font-ghostscript", Compressible, NotBinary, List("gsf"))
      lazy val `application/x-font-libgrx`: MediaType =
        new MediaType(mainType, "x-font-libgrx", Compressible, NotBinary)
      lazy val `application/x-font-linux-psf`: MediaType =
        new MediaType(mainType, "x-font-linux-psf", Compressible, NotBinary, List("psf"))
      lazy val `application/x-font-pcf`: MediaType =
        new MediaType(mainType, "x-font-pcf", Compressible, NotBinary, List("pcf"))
      lazy val `application/x-font-snf`: MediaType =
        new MediaType(mainType, "x-font-snf", Compressible, NotBinary, List("snf"))
      lazy val `application/x-font-speedo`: MediaType =
        new MediaType(mainType, "x-font-speedo", Compressible, NotBinary)
      lazy val `application/x-font-sunos-news`: MediaType =
        new MediaType(mainType, "x-font-sunos-news", Compressible, NotBinary)
      lazy val `application/x-font-type1`: MediaType = new MediaType(
        mainType,
        "x-font-type1",
        Compressible,
        NotBinary,
        List("pfa", "pfb", "pfm", "afm"))
      lazy val `application/x-font-vfont`: MediaType =
        new MediaType(mainType, "x-font-vfont", Compressible, NotBinary)
      lazy val `application/x-freearc`: MediaType =
        new MediaType(mainType, "x-freearc", Compressible, NotBinary, List("arc"))
      lazy val `application/x-futuresplash`: MediaType =
        new MediaType(mainType, "x-futuresplash", Compressible, NotBinary, List("spl"))
      lazy val `application/x-gca-compressed`: MediaType =
        new MediaType(mainType, "x-gca-compressed", Compressible, NotBinary, List("gca"))
      lazy val `application/x-glulx`: MediaType =
        new MediaType(mainType, "x-glulx", Compressible, NotBinary, List("ulx"))
      lazy val `application/x-gnumeric`: MediaType =
        new MediaType(mainType, "x-gnumeric", Compressible, NotBinary, List("gnumeric"))
      lazy val `application/x-gramps-xml`: MediaType =
        new MediaType(mainType, "x-gramps-xml", Compressible, NotBinary, List("gramps"))
      lazy val `application/x-gtar`: MediaType =
        new MediaType(mainType, "x-gtar", Compressible, Binary, List("gtar"))
      lazy val `application/x-gzip`: MediaType =
        new MediaType(mainType, "x-gzip", Compressible, Binary)
      lazy val `application/x-hdf`: MediaType =
        new MediaType(mainType, "x-hdf", Compressible, NotBinary, List("hdf"))
      lazy val `application/x-httpd-php`: MediaType =
        new MediaType(mainType, "x-httpd-php", Compressible, NotBinary, List("php"))
      lazy val `application/x-install-instructions`: MediaType =
        new MediaType(mainType, "x-install-instructions", Compressible, NotBinary, List("install"))
      lazy val `application/x-iso9660-image`: MediaType =
        new MediaType(mainType, "x-iso9660-image", Compressible, NotBinary, List("iso"))
      lazy val `application/x-java-archive-diff`: MediaType =
        new MediaType(mainType, "x-java-archive-diff", Compressible, NotBinary, List("jardiff"))
      lazy val `application/x-java-jnlp-file`: MediaType =
        new MediaType(mainType, "x-java-jnlp-file", Uncompressible, NotBinary, List("jnlp"))
      lazy val `application/x-javascript`: MediaType =
        new MediaType(mainType, "x-javascript", Compressible, NotBinary)
      lazy val `application/x-latex`: MediaType =
        new MediaType(mainType, "x-latex", Uncompressible, Binary, List("latex"))
      lazy val `application/x-lua-bytecode`: MediaType =
        new MediaType(mainType, "x-lua-bytecode", Compressible, NotBinary, List("luac"))
      lazy val `application/x-lzh-compressed`: MediaType =
        new MediaType(mainType, "x-lzh-compressed", Compressible, NotBinary, List("lzh", "lha"))
      lazy val `application/x-makeself`: MediaType =
        new MediaType(mainType, "x-makeself", Compressible, NotBinary, List("run"))
      lazy val `application/x-mie`: MediaType =
        new MediaType(mainType, "x-mie", Compressible, NotBinary, List("mie"))
      lazy val `application/x-mobipocket-ebook`: MediaType =
        new MediaType(mainType, "x-mobipocket-ebook", Compressible, NotBinary, List("prc", "mobi"))
      lazy val `application/x-mpegurl`: MediaType =
        new MediaType(mainType, "x-mpegurl", Uncompressible, NotBinary)
      lazy val `application/x-ms-application`: MediaType =
        new MediaType(mainType, "x-ms-application", Compressible, NotBinary, List("application"))
      lazy val `application/x-ms-shortcut`: MediaType =
        new MediaType(mainType, "x-ms-shortcut", Compressible, NotBinary, List("lnk"))
      lazy val `application/x-ms-wmd`: MediaType =
        new MediaType(mainType, "x-ms-wmd", Compressible, NotBinary, List("wmd"))
      lazy val `application/x-ms-wmz`: MediaType =
        new MediaType(mainType, "x-ms-wmz", Compressible, NotBinary, List("wmz"))
      lazy val `application/x-ms-xbap`: MediaType =
        new MediaType(mainType, "x-ms-xbap", Compressible, NotBinary, List("xbap"))
      lazy val `application/x-msaccess`: MediaType =
        new MediaType(mainType, "x-msaccess", Compressible, NotBinary, List("mdb"))
      lazy val `application/x-msbinder`: MediaType =
        new MediaType(mainType, "x-msbinder", Compressible, NotBinary, List("obd"))
      lazy val `application/x-mscardfile`: MediaType =
        new MediaType(mainType, "x-mscardfile", Compressible, NotBinary, List("crd"))
      lazy val `application/x-msclip`: MediaType =
        new MediaType(mainType, "x-msclip", Compressible, NotBinary, List("clp"))
      lazy val `application/x-msdos-program`: MediaType =
        new MediaType(mainType, "x-msdos-program", Compressible, NotBinary, List("exe"))
      lazy val `application/x-msdownload`: MediaType = new MediaType(
        mainType,
        "x-msdownload",
        Compressible,
        NotBinary,
        List("exe", "dll", "com", "bat", "msi"))
      lazy val `application/x-msmediaview`: MediaType =
        new MediaType(mainType, "x-msmediaview", Compressible, NotBinary, List("mvb", "m13", "m14"))
      lazy val `application/x-msmetafile`: MediaType = new MediaType(
        mainType,
        "x-msmetafile",
        Compressible,
        NotBinary,
        List("wmf", "wmz", "emf", "emz"))
      lazy val `application/x-msmoney`: MediaType =
        new MediaType(mainType, "x-msmoney", Compressible, NotBinary, List("mny"))
      lazy val `application/x-mspublisher`: MediaType =
        new MediaType(mainType, "x-mspublisher", Compressible, NotBinary, List("pub"))
      lazy val `application/x-msschedule`: MediaType =
        new MediaType(mainType, "x-msschedule", Compressible, NotBinary, List("scd"))
      lazy val `application/x-msterminal`: MediaType =
        new MediaType(mainType, "x-msterminal", Compressible, NotBinary, List("trm"))
      lazy val `application/x-mswrite`: MediaType =
        new MediaType(mainType, "x-mswrite", Compressible, NotBinary, List("wri"))
      lazy val `application/x-netcdf`: MediaType =
        new MediaType(mainType, "x-netcdf", Compressible, NotBinary, List("nc", "cdf"))
      lazy val `application/x-ns-proxy-autoconfig`: MediaType =
        new MediaType(mainType, "x-ns-proxy-autoconfig", Compressible, NotBinary, List("pac"))
      lazy val `application/x-nzb`: MediaType =
        new MediaType(mainType, "x-nzb", Compressible, NotBinary, List("nzb"))
      lazy val `application/x-perl`: MediaType =
        new MediaType(mainType, "x-perl", Compressible, NotBinary, List("pl", "pm"))
      lazy val `application/x-pilot`: MediaType =
        new MediaType(mainType, "x-pilot", Compressible, NotBinary, List("prc", "pdb"))
      lazy val `application/x-pkcs12`: MediaType =
        new MediaType(mainType, "x-pkcs12", Uncompressible, NotBinary, List("p12", "pfx"))
      lazy val `application/x-pkcs7-certificates`: MediaType =
        new MediaType(mainType, "x-pkcs7-certificates", Compressible, NotBinary, List("p7b", "spc"))
      lazy val `application/x-pkcs7-certreqresp`: MediaType =
        new MediaType(mainType, "x-pkcs7-certreqresp", Compressible, NotBinary, List("p7r"))
      lazy val `application/x-rar-compressed`: MediaType =
        new MediaType(mainType, "x-rar-compressed", Uncompressible, Binary, List("rar"))
      lazy val `application/x-redhat-package-manager`: MediaType =
        new MediaType(mainType, "x-redhat-package-manager", Compressible, Binary, List("rpm"))
      lazy val `application/x-research-info-systems`: MediaType =
        new MediaType(mainType, "x-research-info-systems", Compressible, NotBinary, List("ris"))
      lazy val `application/x-sea`: MediaType =
        new MediaType(mainType, "x-sea", Compressible, NotBinary, List("sea"))
      lazy val `application/x-sh`: MediaType =
        new MediaType(mainType, "x-sh", Compressible, NotBinary, List("sh"))
      lazy val `application/x-shar`: MediaType =
        new MediaType(mainType, "x-shar", Compressible, NotBinary, List("shar"))
      lazy val `application/x-shockwave-flash`: MediaType =
        new MediaType(mainType, "x-shockwave-flash", Uncompressible, Binary, List("swf"))
      lazy val `application/x-silverlight-app`: MediaType =
        new MediaType(mainType, "x-silverlight-app", Compressible, NotBinary, List("xap"))
      lazy val `application/x-sql`: MediaType =
        new MediaType(mainType, "x-sql", Compressible, NotBinary, List("sql"))
      lazy val `application/x-stuffit`: MediaType =
        new MediaType(mainType, "x-stuffit", Uncompressible, NotBinary, List("sit"))
      lazy val `application/x-stuffitx`: MediaType =
        new MediaType(mainType, "x-stuffitx", Compressible, NotBinary, List("sitx"))
      lazy val `application/x-subrip`: MediaType =
        new MediaType(mainType, "x-subrip", Compressible, NotBinary, List("srt"))
      lazy val `application/x-sv4cpio`: MediaType =
        new MediaType(mainType, "x-sv4cpio", Compressible, NotBinary, List("sv4cpio"))
      lazy val `application/x-sv4crc`: MediaType =
        new MediaType(mainType, "x-sv4crc", Compressible, NotBinary, List("sv4crc"))
      lazy val `application/x-t3vm-image`: MediaType =
        new MediaType(mainType, "x-t3vm-image", Compressible, NotBinary, List("t3"))
      lazy val `application/x-tads`: MediaType =
        new MediaType(mainType, "x-tads", Compressible, NotBinary, List("gam"))
      lazy val `application/x-tar`: MediaType =
        new MediaType(mainType, "x-tar", Compressible, Binary, List("tar"))
      lazy val `application/x-tcl`: MediaType =
        new MediaType(mainType, "x-tcl", Compressible, NotBinary, List("tcl", "tk"))
      lazy val `application/x-tex`: MediaType =
        new MediaType(mainType, "x-tex", Compressible, Binary, List("tex"))
      lazy val `application/x-tex-tfm`: MediaType =
        new MediaType(mainType, "x-tex-tfm", Compressible, NotBinary, List("tfm"))
      lazy val `application/x-texinfo`: MediaType =
        new MediaType(mainType, "x-texinfo", Compressible, Binary, List("texinfo", "texi"))
      lazy val `application/x-tgif`: MediaType =
        new MediaType(mainType, "x-tgif", Compressible, NotBinary, List("obj"))
      lazy val `application/x-ustar`: MediaType =
        new MediaType(mainType, "x-ustar", Compressible, NotBinary, List("ustar"))
      lazy val `application/x-virtualbox-hdd`: MediaType =
        new MediaType(mainType, "x-virtualbox-hdd", Compressible, NotBinary, List("hdd"))
      lazy val `application/x-virtualbox-ova`: MediaType =
        new MediaType(mainType, "x-virtualbox-ova", Compressible, NotBinary, List("ova"))
      lazy val `application/x-virtualbox-ovf`: MediaType =
        new MediaType(mainType, "x-virtualbox-ovf", Compressible, NotBinary, List("ovf"))
      lazy val `application/x-virtualbox-vbox`: MediaType =
        new MediaType(mainType, "x-virtualbox-vbox", Compressible, NotBinary, List("vbox"))
      lazy val `application/x-virtualbox-vbox-extpack`: MediaType = new MediaType(
        mainType,
        "x-virtualbox-vbox-extpack",
        Uncompressible,
        NotBinary,
        List("vbox-extpack"))
      lazy val `application/x-virtualbox-vdi`: MediaType =
        new MediaType(mainType, "x-virtualbox-vdi", Compressible, NotBinary, List("vdi"))
      lazy val `application/x-virtualbox-vhd`: MediaType =
        new MediaType(mainType, "x-virtualbox-vhd", Compressible, NotBinary, List("vhd"))
      lazy val `application/x-virtualbox-vmdk`: MediaType =
        new MediaType(mainType, "x-virtualbox-vmdk", Compressible, NotBinary, List("vmdk"))
      lazy val `application/x-wais-source`: MediaType =
        new MediaType(mainType, "x-wais-source", Compressible, NotBinary, List("src"))
      lazy val `application/x-web-app-manifest+json`: MediaType =
        new MediaType(mainType, "x-web-app-manifest+json", Compressible, NotBinary, List("webapp"))
      lazy val `application/x-www-form-urlencoded`: MediaType =
        new MediaType(mainType, "x-www-form-urlencoded", Compressible, NotBinary)
      lazy val `application/x-x509-ca-cert`: MediaType =
        new MediaType(mainType, "x-x509-ca-cert", Compressible, Binary, List("der", "crt", "pem"))
      lazy val `application/x-xfig`: MediaType =
        new MediaType(mainType, "x-xfig", Compressible, NotBinary, List("fig"))
      lazy val `application/x-xliff+xml`: MediaType =
        new MediaType(mainType, "x-xliff+xml", Compressible, NotBinary, List("xlf"))
      lazy val `application/x-xpinstall`: MediaType =
        new MediaType(mainType, "x-xpinstall", Uncompressible, Binary, List("xpi"))
      lazy val `application/x-xz`: MediaType =
        new MediaType(mainType, "x-xz", Compressible, NotBinary, List("xz"))
      lazy val `application/x-zmachine`: MediaType = new MediaType(
        mainType,
        "x-zmachine",
        Compressible,
        NotBinary,
        List("z1", "z2", "z3", "z4", "z5", "z6", "z7", "z8"))
      lazy val `application/x400-bp`: MediaType =
        new MediaType(mainType, "x400-bp", Compressible, NotBinary)
      lazy val `application/xacml+xml`: MediaType =
        new MediaType(mainType, "xacml+xml", Compressible, NotBinary)
      lazy val `application/xaml+xml`: MediaType =
        new MediaType(mainType, "xaml+xml", Compressible, NotBinary, List("xaml"))
      lazy val `application/xcap-att+xml`: MediaType =
        new MediaType(mainType, "xcap-att+xml", Compressible, NotBinary)
      lazy val `application/xcap-caps+xml`: MediaType =
        new MediaType(mainType, "xcap-caps+xml", Compressible, NotBinary)
      lazy val `application/xcap-diff+xml`: MediaType =
        new MediaType(mainType, "xcap-diff+xml", Compressible, NotBinary, List("xdf"))
      lazy val `application/xcap-el+xml`: MediaType =
        new MediaType(mainType, "xcap-el+xml", Compressible, NotBinary)
      lazy val `application/xcap-error+xml`: MediaType =
        new MediaType(mainType, "xcap-error+xml", Compressible, NotBinary)
      lazy val `application/xcap-ns+xml`: MediaType =
        new MediaType(mainType, "xcap-ns+xml", Compressible, NotBinary)
      lazy val `application/xcon-conference-info+xml`: MediaType =
        new MediaType(mainType, "xcon-conference-info+xml", Compressible, NotBinary)
      lazy val `application/xcon-conference-info-diff+xml`: MediaType =
        new MediaType(mainType, "xcon-conference-info-diff+xml", Compressible, NotBinary)
      lazy val `application/xenc+xml`: MediaType =
        new MediaType(mainType, "xenc+xml", Compressible, NotBinary, List("xenc"))
      lazy val `application/xhtml+xml`: MediaType =
        new MediaType(mainType, "xhtml+xml", Compressible, NotBinary, List("xhtml", "xht"))
      lazy val `application/xhtml-voice+xml`: MediaType =
        new MediaType(mainType, "xhtml-voice+xml", Compressible, NotBinary)
      lazy val `application/xml`: MediaType =
        new MediaType(mainType, "xml", Compressible, NotBinary, List("xml", "xsl", "xsd", "rng"))
      lazy val `application/xml-dtd`: MediaType =
        new MediaType(mainType, "xml-dtd", Compressible, NotBinary, List("dtd"))
      lazy val `application/xml-external-parsed-entity`: MediaType =
        new MediaType(mainType, "xml-external-parsed-entity", Compressible, NotBinary)
      lazy val `application/xml-patch+xml`: MediaType =
        new MediaType(mainType, "xml-patch+xml", Compressible, NotBinary)
      lazy val `application/xmpp+xml`: MediaType =
        new MediaType(mainType, "xmpp+xml", Compressible, NotBinary)
      lazy val `application/xop+xml`: MediaType =
        new MediaType(mainType, "xop+xml", Compressible, NotBinary, List("xop"))
      lazy val `application/xproc+xml`: MediaType =
        new MediaType(mainType, "xproc+xml", Compressible, NotBinary, List("xpl"))
      lazy val `application/xslt+xml`: MediaType =
        new MediaType(mainType, "xslt+xml", Compressible, NotBinary, List("xslt"))
      lazy val `application/xspf+xml`: MediaType =
        new MediaType(mainType, "xspf+xml", Compressible, NotBinary, List("xspf"))
      lazy val `application/xv+xml`: MediaType = new MediaType(
        mainType,
        "xv+xml",
        Compressible,
        NotBinary,
        List("mxml", "xhvml", "xvml", "xvm"))
      lazy val `application/yang`: MediaType =
        new MediaType(mainType, "yang", Compressible, NotBinary, List("yang"))
      lazy val `application/yang-data+json`: MediaType =
        new MediaType(mainType, "yang-data+json", Compressible, NotBinary)
      lazy val `application/yang-data+xml`: MediaType =
        new MediaType(mainType, "yang-data+xml", Compressible, NotBinary)
      lazy val `application/yang-patch+json`: MediaType =
        new MediaType(mainType, "yang-patch+json", Compressible, NotBinary)
      lazy val `application/yang-patch+xml`: MediaType =
        new MediaType(mainType, "yang-patch+xml", Compressible, NotBinary)
      lazy val `application/yin+xml`: MediaType =
        new MediaType(mainType, "yin+xml", Compressible, NotBinary, List("yin"))
      lazy val `application/zip`: MediaType =
        new MediaType(mainType, "zip", Uncompressible, Binary, List("zip"))
      lazy val `application/zlib`: MediaType =
        new MediaType(mainType, "zlib", Compressible, NotBinary)
      lazy val all: List[MediaType] = List(
        `application/vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml`,
        `application/vnd.openxmlformats-officedocument.drawingml.diagramdata+xml`,
        `application/vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml`,
        `application/vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml`,
        `application/vnd.openxmlformats-officedocument.extended-properties+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.commentauthors+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.comments+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.notesmaster+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.notesslide+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.presentation`,
        `application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.presprops+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.slide`,
        `application/vnd.openxmlformats-officedocument.presentationml.slide+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.slidelayout+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.slidemaster+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.slideshow`,
        `application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.tablestyles+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.tags+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.template`,
        `application/vnd.openxmlformats-officedocument.presentationml.template.main+xml`,
        `application/vnd.openxmlformats-officedocument.presentationml.viewprops+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.connections+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.template`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml`,
        `application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`,
        `application/vnd.openxmlformats-officedocument.theme+xml`,
        `application/vnd.openxmlformats-officedocument.themeoverride+xml`,
        `application/vnd.openxmlformats-officedocument.vmldrawing`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.document`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.template`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`,
        `application/vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml`,
        `application/vnd.openxmlformats-package.core-properties+xml`,
        `application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml`,
        `application/vnd.openxmlformats-package.relationships+xml`,
        `application/vnd.oracle.resource+json`,
        `application/vnd.orange.indata`,
        `application/vnd.osa.netdeploy`,
        `application/vnd.osgeo.mapguide.package`,
        `application/vnd.osgi.bundle`,
        `application/vnd.osgi.dp`,
        `application/vnd.osgi.subsystem`,
        `application/vnd.otps.ct-kip+xml`,
        `application/vnd.oxli.countgraph`,
        `application/vnd.pagerduty+json`,
        `application/vnd.palm`,
        `application/vnd.panoply`,
        `application/vnd.paos+xml`,
        `application/vnd.paos.xml`,
        `application/vnd.patentdive`,
        `application/vnd.pawaafile`,
        `application/vnd.pcos`,
        `application/vnd.pg.format`,
        `application/vnd.pg.osasli`,
        `application/vnd.piaccess.application-licence`,
        `application/vnd.picsel`,
        `application/vnd.pmi.widget`,
        `application/vnd.poc.group-advertisement+xml`,
        `application/vnd.pocketlearn`,
        `application/vnd.powerbuilder6`,
        `application/vnd.powerbuilder6-s`,
        `application/vnd.powerbuilder7`,
        `application/vnd.powerbuilder7-s`,
        `application/vnd.powerbuilder75`,
        `application/vnd.powerbuilder75-s`,
        `application/vnd.preminet`,
        `application/vnd.previewsystems.box`,
        `application/vnd.proteus.magazine`,
        `application/vnd.publishare-delta-tree`,
        `application/vnd.pvi.ptid1`,
        `application/vnd.pwg-multiplexed`,
        `application/vnd.pwg-xhtml-print+xml`,
        `application/vnd.qualcomm.brew-app-res`,
        `application/vnd.quarantainenet`,
        `application/vnd.quark.quarkxpress`,
        `application/vnd.quobject-quoxdocument`,
        `application/vnd.radisys.moml+xml`,
        `application/vnd.radisys.msml+xml`,
        `application/vnd.radisys.msml-audit+xml`,
        `application/vnd.radisys.msml-audit-conf+xml`,
        `application/vnd.radisys.msml-audit-conn+xml`,
        `application/vnd.radisys.msml-audit-dialog+xml`,
        `application/vnd.radisys.msml-audit-stream+xml`,
        `application/vnd.radisys.msml-conf+xml`,
        `application/vnd.radisys.msml-dialog+xml`,
        `application/vnd.radisys.msml-dialog-base+xml`,
        `application/vnd.radisys.msml-dialog-fax-detect+xml`,
        `application/vnd.radisys.msml-dialog-fax-sendrecv+xml`,
        `application/vnd.radisys.msml-dialog-group+xml`,
        `application/vnd.radisys.msml-dialog-speech+xml`,
        `application/vnd.radisys.msml-dialog-transform+xml`,
        `application/vnd.rainstor.data`,
        `application/vnd.rapid`,
        `application/vnd.rar`,
        `application/vnd.realvnc.bed`,
        `application/vnd.recordare.musicxml`,
        `application/vnd.recordare.musicxml+xml`,
        `application/vnd.renlearn.rlprint`,
        `application/vnd.restful+json`,
        `application/vnd.rig.cryptonote`,
        `application/vnd.rim.cod`,
        `application/vnd.rn-realmedia`,
        `application/vnd.rn-realmedia-vbr`,
        `application/vnd.route66.link66+xml`,
        `application/vnd.rs-274x`,
        `application/vnd.ruckus.download`,
        `application/vnd.s3sms`,
        `application/vnd.sailingtracker.track`,
        `application/vnd.sbm.cid`,
        `application/vnd.sbm.mid2`,
        `application/vnd.scribus`,
        `application/vnd.sealed.3df`,
        `application/vnd.sealed.csf`,
        `application/vnd.sealed.doc`,
        `application/vnd.sealed.eml`,
        `application/vnd.sealed.mht`,
        `application/vnd.sealed.net`,
        `application/vnd.sealed.ppt`,
        `application/vnd.sealed.tiff`,
        `application/vnd.sealed.xls`,
        `application/vnd.sealedmedia.softseal.html`,
        `application/vnd.sealedmedia.softseal.pdf`,
        `application/vnd.seemail`,
        `application/vnd.sema`,
        `application/vnd.semd`,
        `application/vnd.semf`,
        `application/vnd.shana.informed.formdata`,
        `application/vnd.shana.informed.formtemplate`,
        `application/vnd.shana.informed.interchange`,
        `application/vnd.shana.informed.package`,
        `application/vnd.sigrok.session`,
        `application/vnd.simtech-mindmapper`,
        `application/vnd.siren+json`,
        `application/vnd.smaf`,
        `application/vnd.smart.notebook`,
        `application/vnd.smart.teacher`,
        `application/vnd.software602.filler.form+xml`,
        `application/vnd.software602.filler.form-xml-zip`,
        `application/vnd.solent.sdkm+xml`,
        `application/vnd.spotfire.dxp`,
        `application/vnd.spotfire.sfs`,
        `application/vnd.sqlite3`,
        `application/vnd.sss-cod`,
        `application/vnd.sss-dtf`,
        `application/vnd.sss-ntf`,
        `application/vnd.stardivision.calc`,
        `application/vnd.stardivision.draw`,
        `application/vnd.stardivision.impress`,
        `application/vnd.stardivision.math`,
        `application/vnd.stardivision.writer`,
        `application/vnd.stardivision.writer-global`,
        `application/vnd.stepmania.package`,
        `application/vnd.stepmania.stepchart`,
        `application/vnd.street-stream`,
        `application/vnd.sun.wadl+xml`,
        `application/vnd.sun.xml.calc`,
        `application/vnd.sun.xml.calc.template`,
        `application/vnd.sun.xml.draw`,
        `application/vnd.sun.xml.draw.template`,
        `application/vnd.sun.xml.impress`,
        `application/vnd.sun.xml.impress.template`,
        `application/vnd.sun.xml.math`,
        `application/vnd.sun.xml.writer`,
        `application/vnd.sun.xml.writer.global`,
        `application/vnd.sun.xml.writer.template`,
        `application/vnd.sus-calendar`,
        `application/vnd.svd`,
        `application/vnd.swiftview-ics`,
        `application/vnd.symbian.install`,
        `application/vnd.syncml+xml`,
        `application/vnd.syncml.dm+wbxml`,
        `application/vnd.syncml.dm+xml`,
        `application/vnd.syncml.dm.notification`,
        `application/vnd.syncml.dmddf+wbxml`,
        `application/vnd.syncml.dmddf+xml`,
        `application/vnd.syncml.dmtnds+wbxml`,
        `application/vnd.syncml.dmtnds+xml`,
        `application/vnd.syncml.ds.notification`,
        `application/vnd.tableschema+json`,
        `application/vnd.tao.intent-module-archive`,
        `application/vnd.tcpdump.pcap`,
        `application/vnd.tmd.mediaflex.api+xml`,
        `application/vnd.tml`,
        `application/vnd.tmobile-livetv`,
        `application/vnd.tri.onesource`,
        `application/vnd.trid.tpt`,
        `application/vnd.triscape.mxs`,
        `application/vnd.trueapp`,
        `application/vnd.truedoc`,
        `application/vnd.ubisoft.webplayer`,
        `application/vnd.ufdl`,
        `application/vnd.uiq.theme`,
        `application/vnd.umajin`,
        `application/vnd.unity`,
        `application/vnd.uoml+xml`,
        `application/vnd.uplanet.alert`,
        `application/vnd.uplanet.alert-wbxml`,
        `application/vnd.uplanet.bearer-choice`,
        `application/vnd.uplanet.bearer-choice-wbxml`,
        `application/vnd.uplanet.cacheop`,
        `application/vnd.uplanet.cacheop-wbxml`,
        `application/vnd.uplanet.channel`,
        `application/vnd.uplanet.channel-wbxml`,
        `application/vnd.uplanet.list`,
        `application/vnd.uplanet.list-wbxml`,
        `application/vnd.uplanet.listcmd`,
        `application/vnd.uplanet.listcmd-wbxml`,
        `application/vnd.uplanet.signal`,
        `application/vnd.uri-map`,
        `application/vnd.valve.source.material`,
        `application/vnd.vcx`,
        `application/vnd.vd-study`,
        `application/vnd.vectorworks`,
        `application/vnd.vel+json`,
        `application/vnd.verimatrix.vcas`,
        `application/vnd.vidsoft.vidconference`,
        `application/vnd.visio`,
        `application/vnd.visionary`,
        `application/vnd.vividence.scriptfile`,
        `application/vnd.vsf`,
        `application/vnd.wap.sic`,
        `application/vnd.wap.slc`,
        `application/vnd.wap.wbxml`,
        `application/vnd.wap.wmlc`,
        `application/vnd.wap.wmlscriptc`,
        `application/vnd.webturbo`,
        `application/vnd.wfa.p2p`,
        `application/vnd.wfa.wsc`,
        `application/vnd.windows.devicepairing`,
        `application/vnd.wmc`,
        `application/vnd.wmf.bootstrap`,
        `application/vnd.wolfram.mathematica`,
        `application/vnd.wolfram.mathematica.package`,
        `application/vnd.wolfram.player`,
        `application/vnd.wordperfect`,
        `application/vnd.wqd`,
        `application/vnd.wrq-hp3000-labelled`,
        `application/vnd.wt.stf`,
        `application/vnd.wv.csp+wbxml`,
        `application/vnd.wv.csp+xml`,
        `application/vnd.wv.ssp+xml`,
        `application/vnd.xacml+json`,
        `application/vnd.xara`,
        `application/vnd.xfdl`,
        `application/vnd.xfdl.webform`,
        `application/vnd.xmi+xml`,
        `application/vnd.xmpie.cpkg`,
        `application/vnd.xmpie.dpkg`,
        `application/vnd.xmpie.plan`,
        `application/vnd.xmpie.ppkg`,
        `application/vnd.xmpie.xlim`,
        `application/vnd.yamaha.hv-dic`,
        `application/vnd.yamaha.hv-script`,
        `application/vnd.yamaha.hv-voice`,
        `application/vnd.yamaha.openscoreformat`,
        `application/vnd.yamaha.openscoreformat.osfpvg+xml`,
        `application/vnd.yamaha.remote-setup`,
        `application/vnd.yamaha.smaf-audio`,
        `application/vnd.yamaha.smaf-phrase`,
        `application/vnd.yamaha.through-ngn`,
        `application/vnd.yamaha.tunnel-udpencap`,
        `application/vnd.yaoweme`,
        `application/vnd.yellowriver-custom-menu`,
        `application/vnd.youtube.yt`,
        `application/vnd.zul`,
        `application/vnd.zzazz.deck+xml`,
        `application/voicexml+xml`,
        `application/voucher-cms+json`,
        `application/vq-rtcpxr`,
        `application/wasm`,
        `application/watcherinfo+xml`,
        `application/webpush-options+json`,
        `application/whoispp-query`,
        `application/whoispp-response`,
        `application/widget`,
        `application/winhlp`,
        `application/wita`,
        `application/wordperfect5.1`,
        `application/wsdl+xml`,
        `application/wspolicy+xml`,
        `application/x-7z-compressed`,
        `application/x-abiword`,
        `application/x-ace-compressed`,
        `application/x-amf`,
        `application/x-apple-diskimage`,
        `application/x-arj`,
        `application/x-authorware-bin`,
        `application/x-authorware-map`,
        `application/x-authorware-seg`,
        `application/x-bcpio`,
        `application/x-bdoc`,
        `application/x-bittorrent`,
        `application/x-blorb`,
        `application/x-bzip`,
        `application/x-bzip2`,
        `application/x-cbr`,
        `application/x-cdlink`,
        `application/x-cfs-compressed`,
        `application/x-chat`,
        `application/x-chess-pgn`,
        `application/x-chrome-extension`,
        `application/x-cocoa`,
        `application/x-compress`,
        `application/x-conference`,
        `application/x-cpio`,
        `application/x-csh`,
        `application/x-deb`,
        `application/x-debian-package`,
        `application/x-dgc-compressed`,
        `application/x-director`,
        `application/x-doom`,
        `application/x-dtbncx+xml`,
        `application/x-dtbook+xml`,
        `application/x-dtbresource+xml`,
        `application/x-dvi`,
        `application/x-envoy`,
        `application/x-eva`,
        `application/x-font-bdf`,
        `application/x-font-dos`,
        `application/x-font-framemaker`,
        `application/x-font-ghostscript`,
        `application/x-font-libgrx`,
        `application/x-font-linux-psf`,
        `application/x-font-pcf`,
        `application/x-font-snf`,
        `application/x-font-speedo`,
        `application/x-font-sunos-news`,
        `application/x-font-type1`,
        `application/x-font-vfont`,
        `application/x-freearc`,
        `application/x-futuresplash`,
        `application/x-gca-compressed`,
        `application/x-glulx`,
        `application/x-gnumeric`,
        `application/x-gramps-xml`,
        `application/x-gtar`,
        `application/x-gzip`,
        `application/x-hdf`,
        `application/x-httpd-php`,
        `application/x-install-instructions`,
        `application/x-iso9660-image`,
        `application/x-java-archive-diff`,
        `application/x-java-jnlp-file`,
        `application/x-javascript`,
        `application/x-latex`,
        `application/x-lua-bytecode`,
        `application/x-lzh-compressed`,
        `application/x-makeself`,
        `application/x-mie`,
        `application/x-mobipocket-ebook`,
        `application/x-mpegurl`,
        `application/x-ms-application`,
        `application/x-ms-shortcut`,
        `application/x-ms-wmd`,
        `application/x-ms-wmz`,
        `application/x-ms-xbap`,
        `application/x-msaccess`,
        `application/x-msbinder`,
        `application/x-mscardfile`,
        `application/x-msclip`,
        `application/x-msdos-program`,
        `application/x-msdownload`,
        `application/x-msmediaview`,
        `application/x-msmetafile`,
        `application/x-msmoney`,
        `application/x-mspublisher`,
        `application/x-msschedule`,
        `application/x-msterminal`,
        `application/x-mswrite`,
        `application/x-netcdf`,
        `application/x-ns-proxy-autoconfig`,
        `application/x-nzb`,
        `application/x-perl`,
        `application/x-pilot`,
        `application/x-pkcs12`,
        `application/x-pkcs7-certificates`,
        `application/x-pkcs7-certreqresp`,
        `application/x-rar-compressed`,
        `application/x-redhat-package-manager`,
        `application/x-research-info-systems`,
        `application/x-sea`,
        `application/x-sh`,
        `application/x-shar`,
        `application/x-shockwave-flash`,
        `application/x-silverlight-app`,
        `application/x-sql`,
        `application/x-stuffit`,
        `application/x-stuffitx`,
        `application/x-subrip`,
        `application/x-sv4cpio`,
        `application/x-sv4crc`,
        `application/x-t3vm-image`,
        `application/x-tads`,
        `application/x-tar`,
        `application/x-tcl`,
        `application/x-tex`,
        `application/x-tex-tfm`,
        `application/x-texinfo`,
        `application/x-tgif`,
        `application/x-ustar`,
        `application/x-virtualbox-hdd`,
        `application/x-virtualbox-ova`,
        `application/x-virtualbox-ovf`,
        `application/x-virtualbox-vbox`,
        `application/x-virtualbox-vbox-extpack`,
        `application/x-virtualbox-vdi`,
        `application/x-virtualbox-vhd`,
        `application/x-virtualbox-vmdk`,
        `application/x-wais-source`,
        `application/x-web-app-manifest+json`,
        `application/x-www-form-urlencoded`,
        `application/x-x509-ca-cert`,
        `application/x-xfig`,
        `application/x-xliff+xml`,
        `application/x-xpinstall`,
        `application/x-xz`,
        `application/x-zmachine`,
        `application/x400-bp`,
        `application/xacml+xml`,
        `application/xaml+xml`,
        `application/xcap-att+xml`,
        `application/xcap-caps+xml`,
        `application/xcap-diff+xml`,
        `application/xcap-el+xml`,
        `application/xcap-error+xml`,
        `application/xcap-ns+xml`,
        `application/xcon-conference-info+xml`,
        `application/xcon-conference-info-diff+xml`,
        `application/xenc+xml`,
        `application/xhtml+xml`,
        `application/xhtml-voice+xml`,
        `application/xml`,
        `application/xml-dtd`,
        `application/xml-external-parsed-entity`,
        `application/xml-patch+xml`,
        `application/xmpp+xml`,
        `application/xop+xml`,
        `application/xproc+xml`,
        `application/xslt+xml`,
        `application/xspf+xml`,
        `application/xv+xml`,
        `application/yang`,
        `application/yang-data+json`,
        `application/yang-data+xml`,
        `application/yang-patch+json`,
        `application/yang-patch+xml`,
        `application/yin+xml`,
        `application/zip`,
        `application/zlib`
      )
    }
  }
  object multipart {
    val mainType: String = "multipart"
    lazy val `multipart/alternative`: MediaType =
      new MediaType(mainType, "alternative", Uncompressible, NotBinary)
    lazy val `multipart/appledouble`: MediaType =
      new MediaType(mainType, "appledouble", Compressible, NotBinary)
    lazy val `multipart/byteranges`: MediaType =
      new MediaType(mainType, "byteranges", Compressible, NotBinary)
    lazy val `multipart/digest`: MediaType =
      new MediaType(mainType, "digest", Compressible, NotBinary)
    lazy val `multipart/encrypted`: MediaType =
      new MediaType(mainType, "encrypted", Uncompressible, NotBinary)
    lazy val `multipart/form-data`: MediaType =
      new MediaType(mainType, "form-data", Uncompressible, NotBinary)
    lazy val `multipart/header-set`: MediaType =
      new MediaType(mainType, "header-set", Compressible, NotBinary)
    lazy val `multipart/mixed`: MediaType =
      new MediaType(mainType, "mixed", Uncompressible, NotBinary)
    lazy val `multipart/multilingual`: MediaType =
      new MediaType(mainType, "multilingual", Compressible, NotBinary)
    lazy val `multipart/parallel`: MediaType =
      new MediaType(mainType, "parallel", Compressible, NotBinary)
    lazy val `multipart/related`: MediaType =
      new MediaType(mainType, "related", Uncompressible, NotBinary)
    lazy val `multipart/report`: MediaType =
      new MediaType(mainType, "report", Compressible, NotBinary)
    lazy val `multipart/signed`: MediaType =
      new MediaType(mainType, "signed", Uncompressible, NotBinary)
    lazy val `multipart/vnd.bint.med-plus`: MediaType =
      new MediaType(mainType, "vnd.bint.med-plus", Compressible, NotBinary)
    lazy val `multipart/voice-message`: MediaType =
      new MediaType(mainType, "voice-message", Compressible, NotBinary)
    lazy val `multipart/x-mixed-replace`: MediaType =
      new MediaType(mainType, "x-mixed-replace", Compressible, NotBinary)
    lazy val all: List[MediaType] = List(
      `multipart/alternative`,
      `multipart/appledouble`,
      `multipart/byteranges`,
      `multipart/digest`,
      `multipart/encrypted`,
      `multipart/form-data`,
      `multipart/header-set`,
      `multipart/mixed`,
      `multipart/multilingual`,
      `multipart/parallel`,
      `multipart/related`,
      `multipart/report`,
      `multipart/signed`,
      `multipart/vnd.bint.med-plus`,
      `multipart/voice-message`,
      `multipart/x-mixed-replace`
    )
  }
  object x_shader {
    val mainType: String = "x-shader"
    lazy val `x-shader/x-fragment`: MediaType =
      new MediaType(mainType, "x-fragment", Compressible, NotBinary)
    lazy val `x-shader/x-vertex`: MediaType =
      new MediaType(mainType, "x-vertex", Compressible, NotBinary)
    lazy val all: List[MediaType] = List(`x-shader/x-fragment`, `x-shader/x-vertex`)
  }
  object video {
    val mainType: String = "video"
    lazy val `video/1d-interleaved-parityfec`: MediaType =
      new MediaType(mainType, "1d-interleaved-parityfec", Compressible, Binary)
    lazy val `video/3gpp`: MediaType =
      new MediaType(mainType, "3gpp", Compressible, Binary, List("3gp", "3gpp"))
    lazy val `video/3gpp-tt`: MediaType = new MediaType(mainType, "3gpp-tt", Compressible, Binary)
    lazy val `video/3gpp2`: MediaType =
      new MediaType(mainType, "3gpp2", Compressible, Binary, List("3g2"))
    lazy val `video/bmpeg`: MediaType = new MediaType(mainType, "bmpeg", Compressible, Binary)
    lazy val `video/bt656`: MediaType = new MediaType(mainType, "bt656", Compressible, Binary)
    lazy val `video/celb`: MediaType = new MediaType(mainType, "celb", Compressible, Binary)
    lazy val `video/dv`: MediaType = new MediaType(mainType, "dv", Compressible, Binary)
    lazy val `video/encaprtp`: MediaType = new MediaType(mainType, "encaprtp", Compressible, Binary)
    lazy val `video/h261`: MediaType =
      new MediaType(mainType, "h261", Compressible, Binary, List("h261"))
    lazy val `video/h263`: MediaType =
      new MediaType(mainType, "h263", Compressible, Binary, List("h263"))
    lazy val `video/h263-1998`: MediaType =
      new MediaType(mainType, "h263-1998", Compressible, Binary)
    lazy val `video/h263-2000`: MediaType =
      new MediaType(mainType, "h263-2000", Compressible, Binary)
    lazy val `video/h264`: MediaType =
      new MediaType(mainType, "h264", Compressible, Binary, List("h264"))
    lazy val `video/h264-rcdo`: MediaType =
      new MediaType(mainType, "h264-rcdo", Compressible, Binary)
    lazy val `video/h264-svc`: MediaType = new MediaType(mainType, "h264-svc", Compressible, Binary)
    lazy val `video/h265`: MediaType = new MediaType(mainType, "h265", Compressible, Binary)
    lazy val `video/iso.segment`: MediaType =
      new MediaType(mainType, "iso.segment", Compressible, Binary)
    lazy val `video/jpeg`: MediaType =
      new MediaType(mainType, "jpeg", Compressible, Binary, List("jpgv"))
    lazy val `video/jpeg2000`: MediaType = new MediaType(mainType, "jpeg2000", Compressible, Binary)
    lazy val `video/jpm`: MediaType =
      new MediaType(mainType, "jpm", Compressible, Binary, List("jpm", "jpgm"))
    lazy val `video/mj2`: MediaType =
      new MediaType(mainType, "mj2", Compressible, Binary, List("mj2", "mjp2"))
    lazy val `video/mp1s`: MediaType = new MediaType(mainType, "mp1s", Compressible, Binary)
    lazy val `video/mp2p`: MediaType = new MediaType(mainType, "mp2p", Compressible, Binary)
    lazy val `video/mp2t`: MediaType =
      new MediaType(mainType, "mp2t", Compressible, Binary, List("ts"))
    lazy val `video/mp4`: MediaType =
      new MediaType(mainType, "mp4", Uncompressible, Binary, List("mp4", "mp4v", "mpg4"))
    lazy val `video/mp4v-es`: MediaType = new MediaType(mainType, "mp4v-es", Compressible, Binary)
    lazy val `video/mpeg`: MediaType = new MediaType(
      mainType,
      "mpeg",
      Uncompressible,
      Binary,
      List("mpeg", "mpg", "mpe", "m1v", "m2v"))
    lazy val `video/mpeg4-generic`: MediaType =
      new MediaType(mainType, "mpeg4-generic", Compressible, Binary)
    lazy val `video/mpv`: MediaType = new MediaType(mainType, "mpv", Compressible, Binary)
    lazy val `video/nv`: MediaType = new MediaType(mainType, "nv", Compressible, Binary)
    lazy val `video/ogg`: MediaType =
      new MediaType(mainType, "ogg", Uncompressible, Binary, List("ogv"))
    lazy val `video/parityfec`: MediaType =
      new MediaType(mainType, "parityfec", Compressible, Binary)
    lazy val `video/pointer`: MediaType = new MediaType(mainType, "pointer", Compressible, Binary)
    lazy val `video/quicktime`: MediaType =
      new MediaType(mainType, "quicktime", Uncompressible, Binary, List("qt", "mov"))
    lazy val `video/raptorfec`: MediaType =
      new MediaType(mainType, "raptorfec", Compressible, Binary)
    lazy val `video/raw`: MediaType = new MediaType(mainType, "raw", Compressible, Binary)
    lazy val `video/rtp-enc-aescm128`: MediaType =
      new MediaType(mainType, "rtp-enc-aescm128", Compressible, Binary)
    lazy val `video/rtploopback`: MediaType =
      new MediaType(mainType, "rtploopback", Compressible, Binary)
    lazy val `video/rtx`: MediaType = new MediaType(mainType, "rtx", Compressible, Binary)
    lazy val `video/smpte291`: MediaType = new MediaType(mainType, "smpte291", Compressible, Binary)
    lazy val `video/smpte292m`: MediaType =
      new MediaType(mainType, "smpte292m", Compressible, Binary)
    lazy val `video/ulpfec`: MediaType = new MediaType(mainType, "ulpfec", Compressible, Binary)
    lazy val `video/vc1`: MediaType = new MediaType(mainType, "vc1", Compressible, Binary)
    lazy val `video/vnd.cctv`: MediaType = new MediaType(mainType, "vnd.cctv", Compressible, Binary)
    lazy val `video/vnd.dece.hd`: MediaType =
      new MediaType(mainType, "vnd.dece.hd", Compressible, Binary, List("uvh", "uvvh"))
    lazy val `video/vnd.dece.mobile`: MediaType =
      new MediaType(mainType, "vnd.dece.mobile", Compressible, Binary, List("uvm", "uvvm"))
    lazy val `video/vnd.dece.mp4`: MediaType =
      new MediaType(mainType, "vnd.dece.mp4", Compressible, Binary)
    lazy val `video/vnd.dece.pd`: MediaType =
      new MediaType(mainType, "vnd.dece.pd", Compressible, Binary, List("uvp", "uvvp"))
    lazy val `video/vnd.dece.sd`: MediaType =
      new MediaType(mainType, "vnd.dece.sd", Compressible, Binary, List("uvs", "uvvs"))
    lazy val `video/vnd.dece.video`: MediaType =
      new MediaType(mainType, "vnd.dece.video", Compressible, Binary, List("uvv", "uvvv"))
    lazy val `video/vnd.directv.mpeg`: MediaType =
      new MediaType(mainType, "vnd.directv.mpeg", Compressible, Binary)
    lazy val `video/vnd.directv.mpeg-tts`: MediaType =
      new MediaType(mainType, "vnd.directv.mpeg-tts", Compressible, Binary)
    lazy val `video/vnd.dlna.mpeg-tts`: MediaType =
      new MediaType(mainType, "vnd.dlna.mpeg-tts", Compressible, Binary)
    lazy val `video/vnd.dvb.file`: MediaType =
      new MediaType(mainType, "vnd.dvb.file", Compressible, Binary, List("dvb"))
    lazy val `video/vnd.fvt`: MediaType =
      new MediaType(mainType, "vnd.fvt", Compressible, Binary, List("fvt"))
    lazy val `video/vnd.hns.video`: MediaType =
      new MediaType(mainType, "vnd.hns.video", Compressible, Binary)
    lazy val `video/vnd.iptvforum.1dparityfec-1010`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.1dparityfec-1010", Compressible, Binary)
    lazy val `video/vnd.iptvforum.1dparityfec-2005`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.1dparityfec-2005", Compressible, Binary)
    lazy val `video/vnd.iptvforum.2dparityfec-1010`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.2dparityfec-1010", Compressible, Binary)
    lazy val `video/vnd.iptvforum.2dparityfec-2005`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.2dparityfec-2005", Compressible, Binary)
    lazy val `video/vnd.iptvforum.ttsavc`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.ttsavc", Compressible, Binary)
    lazy val `video/vnd.iptvforum.ttsmpeg2`: MediaType =
      new MediaType(mainType, "vnd.iptvforum.ttsmpeg2", Compressible, Binary)
    lazy val `video/vnd.motorola.video`: MediaType =
      new MediaType(mainType, "vnd.motorola.video", Compressible, Binary)
    lazy val `video/vnd.motorola.videop`: MediaType =
      new MediaType(mainType, "vnd.motorola.videop", Compressible, Binary)
    lazy val `video/vnd.mpegurl`: MediaType =
      new MediaType(mainType, "vnd.mpegurl", Compressible, Binary, List("mxu", "m4u"))
    lazy val `video/vnd.ms-playready.media.pyv`: MediaType =
      new MediaType(mainType, "vnd.ms-playready.media.pyv", Compressible, Binary, List("pyv"))
    lazy val `video/vnd.nokia.interleaved-multimedia`: MediaType =
      new MediaType(mainType, "vnd.nokia.interleaved-multimedia", Compressible, Binary)
    lazy val `video/vnd.nokia.mp4vr`: MediaType =
      new MediaType(mainType, "vnd.nokia.mp4vr", Compressible, Binary)
    lazy val `video/vnd.nokia.videovoip`: MediaType =
      new MediaType(mainType, "vnd.nokia.videovoip", Compressible, Binary)
    lazy val `video/vnd.objectvideo`: MediaType =
      new MediaType(mainType, "vnd.objectvideo", Compressible, Binary)
    lazy val `video/vnd.radgamettools.bink`: MediaType =
      new MediaType(mainType, "vnd.radgamettools.bink", Compressible, Binary)
    lazy val `video/vnd.radgamettools.smacker`: MediaType =
      new MediaType(mainType, "vnd.radgamettools.smacker", Compressible, Binary)
    lazy val `video/vnd.sealed.mpeg1`: MediaType =
      new MediaType(mainType, "vnd.sealed.mpeg1", Compressible, Binary)
    lazy val `video/vnd.sealed.mpeg4`: MediaType =
      new MediaType(mainType, "vnd.sealed.mpeg4", Compressible, Binary)
    lazy val `video/vnd.sealed.swf`: MediaType =
      new MediaType(mainType, "vnd.sealed.swf", Compressible, Binary)
    lazy val `video/vnd.sealedmedia.softseal.mov`: MediaType =
      new MediaType(mainType, "vnd.sealedmedia.softseal.mov", Compressible, Binary)
    lazy val `video/vnd.uvvu.mp4`: MediaType =
      new MediaType(mainType, "vnd.uvvu.mp4", Compressible, Binary, List("uvu", "uvvu"))
    lazy val `video/vnd.vivo`: MediaType =
      new MediaType(mainType, "vnd.vivo", Compressible, Binary, List("viv"))
    lazy val `video/vp8`: MediaType = new MediaType(mainType, "vp8", Compressible, Binary)
    lazy val `video/webm`: MediaType =
      new MediaType(mainType, "webm", Uncompressible, Binary, List("webm"))
    lazy val `video/x-f4v`: MediaType =
      new MediaType(mainType, "x-f4v", Compressible, Binary, List("f4v"))
    lazy val `video/x-fli`: MediaType =
      new MediaType(mainType, "x-fli", Compressible, Binary, List("fli"))
    lazy val `video/x-flv`: MediaType =
      new MediaType(mainType, "x-flv", Uncompressible, Binary, List("flv"))
    lazy val `video/x-m4v`: MediaType =
      new MediaType(mainType, "x-m4v", Compressible, Binary, List("m4v"))
    lazy val `video/x-matroska`: MediaType =
      new MediaType(mainType, "x-matroska", Uncompressible, Binary, List("mkv", "mk3d", "mks"))
    lazy val `video/x-mng`: MediaType =
      new MediaType(mainType, "x-mng", Compressible, Binary, List("mng"))
    lazy val `video/x-ms-asf`: MediaType =
      new MediaType(mainType, "x-ms-asf", Compressible, Binary, List("asf", "asx"))
    lazy val `video/x-ms-vob`: MediaType =
      new MediaType(mainType, "x-ms-vob", Compressible, Binary, List("vob"))
    lazy val `video/x-ms-wm`: MediaType =
      new MediaType(mainType, "x-ms-wm", Compressible, Binary, List("wm"))
    lazy val `video/x-ms-wmv`: MediaType =
      new MediaType(mainType, "x-ms-wmv", Uncompressible, Binary, List("wmv"))
    lazy val `video/x-ms-wmx`: MediaType =
      new MediaType(mainType, "x-ms-wmx", Compressible, Binary, List("wmx"))
    lazy val `video/x-ms-wvx`: MediaType =
      new MediaType(mainType, "x-ms-wvx", Compressible, Binary, List("wvx"))
    lazy val `video/x-msvideo`: MediaType =
      new MediaType(mainType, "x-msvideo", Compressible, Binary, List("avi"))
    lazy val `video/x-sgi-movie`: MediaType =
      new MediaType(mainType, "x-sgi-movie", Compressible, Binary, List("movie"))
    lazy val `video/x-smv`: MediaType =
      new MediaType(mainType, "x-smv", Compressible, Binary, List("smv"))
    lazy val all: List[MediaType] = List(
      `video/1d-interleaved-parityfec`,
      `video/3gpp`,
      `video/3gpp-tt`,
      `video/3gpp2`,
      `video/bmpeg`,
      `video/bt656`,
      `video/celb`,
      `video/dv`,
      `video/encaprtp`,
      `video/h261`,
      `video/h263`,
      `video/h263-1998`,
      `video/h263-2000`,
      `video/h264`,
      `video/h264-rcdo`,
      `video/h264-svc`,
      `video/h265`,
      `video/iso.segment`,
      `video/jpeg`,
      `video/jpeg2000`,
      `video/jpm`,
      `video/mj2`,
      `video/mp1s`,
      `video/mp2p`,
      `video/mp2t`,
      `video/mp4`,
      `video/mp4v-es`,
      `video/mpeg`,
      `video/mpeg4-generic`,
      `video/mpv`,
      `video/nv`,
      `video/ogg`,
      `video/parityfec`,
      `video/pointer`,
      `video/quicktime`,
      `video/raptorfec`,
      `video/raw`,
      `video/rtp-enc-aescm128`,
      `video/rtploopback`,
      `video/rtx`,
      `video/smpte291`,
      `video/smpte292m`,
      `video/ulpfec`,
      `video/vc1`,
      `video/vnd.cctv`,
      `video/vnd.dece.hd`,
      `video/vnd.dece.mobile`,
      `video/vnd.dece.mp4`,
      `video/vnd.dece.pd`,
      `video/vnd.dece.sd`,
      `video/vnd.dece.video`,
      `video/vnd.directv.mpeg`,
      `video/vnd.directv.mpeg-tts`,
      `video/vnd.dlna.mpeg-tts`,
      `video/vnd.dvb.file`,
      `video/vnd.fvt`,
      `video/vnd.hns.video`,
      `video/vnd.iptvforum.1dparityfec-1010`,
      `video/vnd.iptvforum.1dparityfec-2005`,
      `video/vnd.iptvforum.2dparityfec-1010`,
      `video/vnd.iptvforum.2dparityfec-2005`,
      `video/vnd.iptvforum.ttsavc`,
      `video/vnd.iptvforum.ttsmpeg2`,
      `video/vnd.motorola.video`,
      `video/vnd.motorola.videop`,
      `video/vnd.mpegurl`,
      `video/vnd.ms-playready.media.pyv`,
      `video/vnd.nokia.interleaved-multimedia`,
      `video/vnd.nokia.mp4vr`,
      `video/vnd.nokia.videovoip`,
      `video/vnd.objectvideo`,
      `video/vnd.radgamettools.bink`,
      `video/vnd.radgamettools.smacker`,
      `video/vnd.sealed.mpeg1`,
      `video/vnd.sealed.mpeg4`,
      `video/vnd.sealed.swf`,
      `video/vnd.sealedmedia.softseal.mov`,
      `video/vnd.uvvu.mp4`,
      `video/vnd.vivo`,
      `video/vp8`,
      `video/webm`,
      `video/x-f4v`,
      `video/x-fli`,
      `video/x-flv`,
      `video/x-m4v`,
      `video/x-matroska`,
      `video/x-mng`,
      `video/x-ms-asf`,
      `video/x-ms-vob`,
      `video/x-ms-wm`,
      `video/x-ms-wmv`,
      `video/x-ms-wmx`,
      `video/x-ms-wvx`,
      `video/x-msvideo`,
      `video/x-sgi-movie`,
      `video/x-smv`
    )
  }
}
