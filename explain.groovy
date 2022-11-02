#!/usr/bin/env filebot -script



/*
 * Print match metrics (if a file argument is passed along)
 */
if (args) {
	args.getFiles{ it.isVideo() }.each{ f ->
		println ' File / Object / MediaInfo '.center(80, '-')

		println 'File:    ' + f
		println 'Object:  ' + f.xattr['net.filebot.metadata']
		println 'Media:   ' + any{ MediaInfo.snapshot(f) }{ null }

		if (f.metadata) {
			println ' Episode Metrics '.center(80, '-')
			EpisodeMetrics.defaultSequence(false).each{ m ->
				println String.format('%-20s % 1.1f', m, m.getSimilarity(f, f.metadata))
			}
		}
	}
}



/*
 * Print local index matches (if the --q option is passed along)
 */
if (_args.query) {
	def q = _args.query
	// search series index
	WebServices.episodeListProviders.each{ db ->
		db.index.each{ s ->
			s.effectiveNames.each{ n ->
				if (n.findWordMatch(q) || s.id ==~ q) {
					println "$n [$db.identifier::$s.id]"
				}
			}
		}
	}
	// search movie index
	WebServices.movieLookupServices.each{ db ->
		db.index.each{ m ->
			m.effectiveNames.each{ n ->
				if (n.findWordMatch(q) || m.id ==~ q) {
					println "$n [$db.identifier::$m.id]"
				}
			}
		}
	}
	// search anime mappings
	WebServices.AnimeList.model.anime.each{ m ->
		if (m.name.findWordMatch(q) || m.tvdbname.findWordMatch(q) || m.anidbid ==~ q || m.tvdbid ==~ q) {
			println m
		}
	}
}
