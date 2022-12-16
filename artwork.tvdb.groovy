#!/usr/bin/env filebot -script


// require at least 1 input folder
if (args.size() == 0) {
	die "Illegal usage: no input"
}


include('lib/htpc')


args.eachMediaFolder{ dir ->
	// fetch only missing artwork by default
	if (dir.hasFile{it.name == 'banner.jpg'}) {
		log.finest "Skipping $dir"
		return
	}

	def videos = dir.listFiles{ it.isVideo() }
	def query = _args.query ?: detectSeriesName(videos)
	def sxe = videos.findResult{ parseEpisodeNumber(it) }
	def locale = _args.language.locale

	if (query == null) {
		query = dir.dir.hasFile{ it.name =~ /Season/ && it.isDirectory() } ? dir.dir.name : dir.name
	}

	log.finest "$dir => Lookup by $query"
	def options = TheTVDB.lookup(query, locale)
	if (options.isEmpty()) {
		log.warning "TV Series not found: $query"
		return
	}

	// sort by relevance
	options = options.sortBySimilarity(query){ it.name }

	// auto-select series
	def series = options[0]

	// maybe require user input
	if (options.size() > 1 && _args.strict) {
		series = showInputDialog(options, query, 'Select TV Series')
	}

	if (series == null) {
		return null
	}

	// auto-detect structure
	def seriesDir = [dir.dir, dir].sortBySimilarity(series.name, { it.name })[0]
	def season = sxe && sxe.season > 0 ? sxe.season : 1

	log.fine "$dir => $series"
	fetchSeriesArtworkAndNfo(seriesDir, dir, TheTVDB.getSeriesInfo(series, locale), season, false, locale)
}
