#!/usr/bin/env filebot -script


def extras = any{ extras.toBoolean() }{ false }


// sanity checks
if (args.size() == 0) {
	die "Illegal usage: no input"
}


include('lib/htpc')


args.eachMediaFolder{ dir ->
	// fetch only missing artwork by default
	if (dir.hasFile{it.name == 'movie.nfo'} && dir.hasFile{it.name == 'poster.jpg'} && dir.hasFile{it.name == 'fanart.jpg'}) {
		log.finest "Skipping $dir"
		return
	}

	def videos = dir.listFiles{ it.isVideo() }
	def query = _args.query
	def locale = any{ _args.language.locale }{ Locale.ENGLISH }
	def options = []

	if (query) {
		// manual search & sort by relevance
		options = TheMovieDB.searchMovie(query, locale).sortBySimilarity(query, { it.name })
	} else if (videos.size() > 0) {
		// run movie auto-detection for video files
		options = MediaDetection.detectMovie(videos[0], TheMovieDB, locale, true)
	}

	if (options.isEmpty()) {
		log.warning "$dir ${videos.name} => movie not found"
		return
	}

	// auto-select movie
	def movie = options[0]

	// maybe require user input
	if (options.size() > 1 && _args.strict) {
		movie = showInputDialog(options, dir.name, 'Select Movie')
	}

	if (movie == null) {
		return null
	}

	log.fine "$dir => $movie"
	tryLogCatch {
		fetchMovieArtworkAndNfo(dir, movie, dir.getFiles{ it.isVideo() }.sort{ it.length() }.reverse().findResult{ it }, extras, false, locale)
	}
}
