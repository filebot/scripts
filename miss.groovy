#!/usr/bin/env filebot -script


// sanity checks
if (args.size() == 0) {
	die "Illegal usage: no input"
}


def episodes = []
def shows = [] as LinkedHashSet

args.getFiles{ f -> f.video }.each{ f ->
	def episode = f.metadata
	def seriesInfo = any{ episode.seriesInfo }{ null }

	if (episode instanceof Episode && seriesInfo instanceof SeriesInfo) {
		log.finest "$seriesInfo | $episode | $f"

		shows += seriesInfo

		if (episode instanceof MultiEpisode) {
			episodes += episode.episodes as List
		} else {
			episodes += episode
		}
	}
}


if (episodes.size() == 0) {
	die "No xattr tagged files", ExitCode.FAILURE
}


def episodeList = shows.collectMany{ s ->
	def episodeList = tryLogCatch{
		return getService(s.database).getEpisodeList(s.id, s.order as SortOrder, s.language as Locale)
	}
	if (episodeList) {
		return episodeList	
	}
	// abort if we cannot receive the current episode list information
	die "Failed to fetch episode list for $s.name [$s]"
}


// check for episode information changes
if (_args.strict) {
	def actual = episodeList.collectEntries{ e -> [e.id, e as String] }
	def expected = episodes.collectEntries{ e -> [e.id, e as String] }
	expected.each{ k, v ->
		if (v != actual[k]) {
			help "* Episode #$k has changed: $v -> ${actual[k]}"
		}
	}
}


// print missing episodes (ignore special episodes)
def regularEpisodes = episodeList.findAll{ e -> e.episode } as LinkedHashSet
def missingEpisodes = regularEpisodes - episodes

// support custom output formatting
def format = _args.expressionFormat

// print missing episodes directly to standard console output (and not to the --log-file)
missingEpisodes.each{ e ->
	if (format) {
		try {
			println format.apply(e)
		} catch(error) {
			log.warning "$error.message [$e]"
		}
	} else {
		println e
	}
}


// return Exit Code 0 (no missing episodes) or Exit Code 100 (some missing episodes)
if (missingEpisodes) {
	die "[${missingEpisodes.size()}] missing episodes", ExitCode.NOOP
} else {
	log.finest "No missing episodes"
}
