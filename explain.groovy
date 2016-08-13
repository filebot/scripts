// Usage: filebot -script fn:explain /path/to/files


args.getFiles{ it.isVideo() }.each{ f ->
	println ' File / Object / MediaInfo '.center(80, '-')

	println 'File:   ' + f
	println 'Object: ' + f.xattr['net.filebot.metadata']
	println 'Media:  ' + any{ MediaInfo.snapshot(f) }{ null }

	if (f.metadata) {
		println ' Episode Metrics '.center(80, '-')
		EpisodeMetrics.defaultSequence(false).each{ m ->
			print m.name().padRight(20, ' ')
			println m.getSimilarity(f, f.metadata).round(1)
		}
	}
}
