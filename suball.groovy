// filebot -script fn:suball <options> <folder>


def accept(File f) {
	// ignore files that are too old
	if (f.lastModified() < System.currentTimeMillis() - tryQuietly{ maxAgeDays.toLong() * 24 * 60 * 60 * 1000 })
		return false
	
	// ignore files that already have subtitles
	if (getMediaInfo(file:f, format:'''{media.TextCodecList}''').isEmpty())
		return false
	
	return true
}


/*
 * Get subtitles for all your media files  
 */
args.eachMediaFolder {
	def videos = it.listFiles{ it.isVideo() }
	
	// ignore videos that already have embedded subtitles
	videos = videos.findAll{ 
		if (!accept(it)) {
			_log.finest "Exclude: " + it
			return false
		}
		return true
	}
	
	getMissingSubtitles(file:videos)
}
