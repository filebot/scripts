// filebot -script fn:miss /path/to/media --def specials=y

def episodes = []
def shows = []
specials  = tryQuietly{ specials.toBoolean() }

args.getFiles().each{ f ->
	if (f.isVideo()) {
		def episode = f.metadata
		def show = episode?.seriesInfo

		log.finest "$show | $episode | $f"

		if (episode != null && show != null) {
			episodes << episode
			shows << show

			// Multi-Episodes
			if (episode instanceof MultiEpisode){
				episode.getEpisodes().each{ep ->
					episodes << ep
				}
			}
		}
	}
}


def episodeList = shows.collectMany{
	return WebServices.getEpisodeListProvider(it.database).getEpisodeList(it.id, it.order as SortOrder, new Locale(it.language))
} as LinkedHashSet

episodeList.removeAll(episodes)


// print missing episodes
episodeList.each{
	if (it.getSpecial()){
		if (specials) {println it}
	}else{
	println it
	}
}
