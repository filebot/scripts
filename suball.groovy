// filebot -script fn:suball <options> <folder>

/*
 * Get subtitles for all your media files  
 */
args.eachMediaFolder {
	def videos = it.listFiles{ it.isVideo() }

	// ignore videos that already have embedded subtitles
	videos = videos.findAll{ getMediaInfo(file:it, format:'''{media.TextCodecList}''').isEmpty() }

	getMissingSubtitles(file:videos)
}
