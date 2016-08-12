// Usage: filebot -script fn:explain /path/to/files


args.getFiles{ it.isVideo() }.each{ f ->
	println ' File / Object '.center(80, '-')

	def o = f.metadata
	println 'File:   ' + f
	println 'Object: ' + o?.objectToJson()

	if (o) {
		println ' Episode Metrics '.center(80, '-')
		EpisodeMetrics.defaultSequence(false).each{ m ->
			print "$m".padRight(20, ' ')
			println m.getSimilarity(f, o).round(1)
		}
	}
}
