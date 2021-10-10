#!/usr/bin/env filebot -script


def fetchMovieArtwork(file, movie, language) {
	fetch movie.getArtwork('posters', language)
	fetch movie.getArtwork('backdrops', null)
}


def fetchSeriesArtwork(file, series, language) {
	switch(series.database) {
		case ~/TheTVDB/:
			fetch series.getArtwork('poster', language)
			fetch series.getArtwork('series', language)
			fetch series.getArtwork('fanart', language)
			fetch series.getArtwork('season', language)
			fetch series.getArtwork('seasonwide', language)
			break
		case ~/TheMovieDB/:
			fetch series.getArtwork('posters', language)
			fetch series.getArtwork('backdrops', null)
			break
		default:
			fetch series.getArtwork()
			break
	}
}


def fetch(artwork) {
	artwork.eachWithIndex{ a, i ->
		log.fine "$i: $a"
	}
}


args.getFiles{ it.video }.each{ f ->
	def m = f.metadata

	switch(m) {
		case Movie:
			log.finest "[MOVIE] $m [$f]"
			fetchMovieArtwork(f, m, m.language)
			break
		case Episode:
			log.finest "[EPISODE] $m [$f]"
			fetchSeriesArtwork(f, m.seriesInfo, m.seriesInfo.language)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
