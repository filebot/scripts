// filebot -script fn:suball <options> <folder>


def lastModifiedLimit = tryQuietly{ System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000) }
def minFileSize = tryQuietly{ minFileSize.toLong() }; if (minFileSize == null) { minFileSize = 50 * 1000L * 1000L }
def minLengthMS = tryQuietly{ minLengthMS.toLong() }; if (minLengthMS == null) { minLengthMS = 10 * 60 * 1000L }


def accept = { f ->
	// ignore files that are too old
	if (lastModifiedLimit != null && f.lastModified() < lastModifiedLimit)
		return false
	
	// ignore files that are too small	
	if (minFileSize > 0 && f.length() < minFileSize)
		return false
	
	// ignore files that are too short
	if ((minLengthMS > 0 && tryQuietly{ getMediaInfo(file:f, format:'{duration}').toLong() < minLengthMS }))
		return false
	
	// ignore files that already have subtitles
	if (getMediaInfo(file:f, format:'''{media.TextCodecList}''').length() > 0)
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
