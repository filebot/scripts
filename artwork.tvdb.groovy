#!/usr/bin/env filebot -script


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
	def locale = any{ _args.language.locale }{ Locale.ENGLISH }

	if (query == null) {
		query = dir.dir.hasFile{ it.name =~ /Season/ && it.isDirectory() } ? dir.dir.name : dir.name
	}

	log.finest "$dir => Search by $query"
	def options = TheTVDB.search(query, locale)
	if (options.isEmpty()) {
		log.warning "TV Series not found: $query"
		return
	}

	// sort by relevance
	options = options.sortBySimilarity(query, { it.name })

	// auto-select series
	def series = options[0]

	// maybe require user input
	if (options.size() != 1 && !_args.nonStrict && !java.awt.GraphicsEnvironment.headless) {
		series = javax.swing.JOptionPane.showInputDialog(null, 'Please select TV Show:', dir.name, 3, null, options.toArray(), series)
		if (series == null) {
			return
		}
	}

	// auto-detect structure
	def seriesDir = [dir.dir, dir].sortBySimilarity(series.name, { it.name })[0]
	def season = sxe && sxe.season > 0 ? sxe.season : 1

	log.fine "$dir => $series"
	tryLogCatch {
		fetchSeriesArtworkAndNfo(seriesDir, dir, series.id, season, false, locale)
	}
}
