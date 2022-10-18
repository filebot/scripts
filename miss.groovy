#!/usr/bin/env filebot -script


// sanity checks
if (args.size() == 0) {
	die "Illegal usage: no input"
}


def episodes = []
def shows = [] as LinkedHashSet

args.getFiles().each{ f ->
	if (f.isVideo()) {
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
}


if (episodes.size() == 0) {
	die "No xattr tagged files", ExitCode.FAILURE
}


def episodeList = shows.collectMany{ s ->
	def episodeList = tryLogCatch{
		return getService(s.database).getEpisodeList(s.id, s.order as SortOrder, s.language as Locale)
	}

	// abort if we cannot receive the current episode list information
	if (!episodeList) {
		die "Failed to fetch episode list for $s.name [$s]"
	}

	// ignore special episodes
	return episodeList.findAll{ e -> e.regular }
} as LinkedHashSet


// print missing episodes
def missingEpisodes = episodeList - episodes


// print missing episodes directly to standard console output (and not to the --log-file)
missingEpisodes.each{ e ->
	println e
}


// return Exit Code 0 (no missing episodes) or Exit Code 100 (some missing episodes)
if (missingEpisodes) {
	die "[${missingEpisodes.size()}] missing episodes", ExitCode.NOOP
} else {
	log.finest "No missing episodes"
}
