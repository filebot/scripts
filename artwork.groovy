#!/usr/bin/env filebot -script


def fetchMovieArtwork(movie, language, movieFolder) {
	fetchArtwork(movie, 'posters', language, movieFolder / 'poster.jpg')
	fetchArtwork(movie, 'backdrops', null, movieFolder / 'backdrop.jpg')
}


def fetchSeriesArtwork(series, language, seriesFolder, seasonFolder) {
	if (series.database == 'TheTVDB') {
		fetchArtwork(series, 'poster', language, seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'series', language, seriesFolder / 'series.jpg')
		fetchArtwork(series, 'fanart', language, seriesFolder / 'fanart.jpg')
		fetchArtwork(series, 'season', language, seasonFolder / 'season.jpg')
		fetchArtwork(series, 'seasonwide', language, seasonFolder / 'seasonwide.jpg')
		return
	}

	if (series.database == 'TheMovieDB::TV') {
		fetchArtwork(series, 'posters', language, seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'backdrops', null, seriesFolder / 'backdrop.jpg')
		return
	}

	log.warning "Artwork not supported: $series"
	return
}


def fetchEpisodeArtwork(episode, episodeFile) {
	def thumbnailFile = episodeFile.dir / episodeFile.nameWithoutExtension + '.jpg'
	if (thumbnailFile.exists()) {
		log.finest "[SKIP] Artwork already exists: $thumbnailFile"
		return
	}

	def i = episode.info
	if (i == null) {
		log.warning "Artwork not supported: $episode.seriesInfo"
		return
	}
	if (i.image == null) {
		log.warning "Artwork not found: $episode"
		return
	}

	log.fine "Fetch $i.image [$thumbnailFile]"
	i.image.saveAs(thumbnailFile)
}


def fetchArtwork(object, category, language, file) {
	if (file.exists()) {
		log.finest "[SKIP] Artwork already exists: $file"
		return
	}

	def artwork = object.getArtwork(category, language)
	if (artwork == null || artwork.empty) {
		log.warning "Artwork not found: $object"
		return
	}

	def a = artwork[0]
	log.fine "Fetch $a [$file]"
	a.url.saveAs(file)
}




args.getFiles{ it.video }.each{ f ->
	def m = f.metadata
	switch(m) {
		case Movie:
			log.finest "[MOVIE] $m [$f]"
			fetchMovieArtwork(m, m.language, f.dir)
			break
		case Episode:
			log.finest "[EPISODE] $m [$f]"
			fetchSeriesArtwork(m.seriesInfo, m.seriesInfo.language, f.dir.dir, f.dir)
			fetchEpisodeArtwork(m, f)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
