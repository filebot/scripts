// filebot -script fn:osdb.explain /path/to/video

args.getFiles{ it.isVideo() }.each{
	def rs = WebServices.OpenSubtitles.getSubtitleList([it] as File[], _args.locale.ISO3Language)
	rs.each{ f, ds ->
		println "\n$f"
		ds.each{ d ->
			println "\n\t$d"
			OpenSubtitlesSubtitleDescriptor.Property.values().each{ p ->
				println "\t\t${p} = ${d.getProperty(p)}"
			}
		}
	}
}
