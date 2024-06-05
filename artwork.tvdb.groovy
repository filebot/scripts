#!/usr/bin/env -S filebot -script


// require at least 1 input folder
if (args.size() == 0) {
	die "Illegal usage: no input"
}


include('lib/htpc')


args.eachMediaFolder{ dir ->
	// fetch only missing artwork by default
	if (dir.hasFile{ it.name == 'poster.jpg' }) {
		log.finest "Skip [$dir] because [poster.jpg] already exists"
		return
	}

	def videos = dir.listFiles{ it.isVideo() }
	def sxe = videos.findResult{ parseEpisodeNumber(it) }
	def locale = _args.language.locale

	// use --q option value
	def query = _args.query
	// use series detection
	if (!query) {
		def s = detectSeries(videos)
		if (s) {
			query = (s.getExternalId('TheTVDB') as String) ?: s.name
		}
	}
	// use series folder name
	if (!query) {
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
	def id = options.first()

	// maybe require user input
	if (options.size() > 1 && _args.strict) {
		id = showInputDialog(options, query, 'Select TV Series')
	}

	if (id == null) {
		return null
	}

	// auto-detect structure
	def seriesInfo = TheTVDB.getSeriesInfo(id, locale)

	def seriesDir = [dir.dir, dir].sortBySimilarity(seriesInfo.name){ it.name }.first()
	def season = sxe && sxe.season > 0 ? sxe.season : 1

	log.fine "$dir => $seriesInfo.name [$seriesInfo]"
	fetchSeriesArtworkAndNfo(seriesDir, dir, seriesInfo, season, false, locale)
}
