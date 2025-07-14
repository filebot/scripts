#!/usr/bin/env -S filebot -script


def fetchMovieArtwork(movie, language, movieFolder) {
	fetchArtwork(movie, 'posters', language, [], movieFolder / 'poster.jpg')
	fetchArtwork(movie, 'backdrops', null, [], movieFolder / 'backdrop.jpg')
}


def fetchSeriesArtwork(series, language, season, seriesFolder, seasonFolder) {
	if (series.database == 'TheTVDB') {
		if (season) {
			fetchArtwork(series, 'posters', language, ['season', season], seasonFolder / 'poster.jpg')
			fetchArtwork(series, 'banners', language, ['season', season], seasonFolder / 'banner.jpg')	
		}
		fetchArtwork(series, 'posters', language, ['series'], seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'banners', language, ['series'], seriesFolder / 'banner.jpg')
		fetchArtwork(series, 'backgrounds', language, ['series'], seriesFolder / 'background.jpg')
		fetchArtwork(series, 'clearart', language, ['series'], seriesFolder / 'clearart.png')
		fetchArtwork(series, 'clearlogo', language, ['series'], seriesFolder / 'clearlogo.png')
		fetchArtwork(series, 'icons', language, ['series'], seriesFolder / 'icon.png')
		return
	}

	if (series.database == 'TheMovieDB::TV') {
		fetchArtwork(series, 'posters', language, [], seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'backdrops', null, [], seriesFolder / 'backdrop.jpg')
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
		log.warning "Artwork not found: $episode [$thumbnailFile]"
		return
	}

	log.fine "Fetch $i.image [$thumbnailFile]"
	try {
		i.image.saveAs(thumbnailFile)
	} catch(e) {
		log.severe "Failed to fetch artwork: $e.message"
	}
}


def fetchArtwork(object, category, language, tag, file) {
	if (file.exists()) {
		log.finest "[SKIP] Artwork already exists: $file"
		return
	}

	def artwork = object.getArtwork(category, language)?.find{ it.matches(*tag) }
	if (artwork) {
		log.fine "Fetch $artwork [$file]"
		try {
			artwork.url.saveAs(file)
		} catch(e) {
			log.severe "Failed to fetch artwork: $e.message"
		}
	} else {
		log.warning "Artwork not found: $object [$file]"
	}
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
			fetchSeriesArtwork(m.seriesInfo, m.seriesInfo.language, m.season, f.dir.dir, f.dir)
			fetchEpisodeArtwork(m, f)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
