// Usage: filebot -script fn:explain /path/to/files


args.getFiles{ it.isVideo() }.each{ f ->
	log.info 'File:   ' + f
	log.info 'Object: ' + f.xattr['net.filebot.metadata']
	log.info 'Media:  ' + any{ MediaInfo.snapshot(f) }{ null }

	if (f.metadata) {
		EpisodeMetrics.defaultSequence(false).each{ m ->
			log.finest m.name().padRight(20, ' ')
			log.finest m.getSimilarity(f, f.metadata).round(1)
		}
	}
}
