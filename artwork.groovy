#!/usr/bin/env filebot -script


def fetchMovieArtwork(file, movie) {
	fetch movie.getArtwork('posters', movie.language)
	fetch movie.getArtwork('backdrops', null)
}


def fetchSeriesArtwork(file, series) {
	switch(series.database) {
		case ~/TheTVDB/:
			fetch series.getArtwork('poster', series.language)
			fetch series.getArtwork('series', series.language)
			fetch series.getArtwork('season', series.language)
			fetch series.getArtwork('seasonwide', series.language)
			break
		case ~/TheMovieDB/:
			fetch series.getArtwork('posters', movie.language)
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
			fetchMovieArtwork(f, m)
			break
		case Episode:
			log.finest "[EPISODE] $m [$f]"
			fetchSeriesArtwork(f, m.seriesInfo)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
