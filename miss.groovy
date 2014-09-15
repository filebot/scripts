// filebot -script fn:miss /path/to/media --log off

def episodes = []
def shows = []

args.getFiles().each{ f ->
	if (f.isVideo()) {
		def episode = f.metadata
		def show = any{ ['tvdb', episode.series, episode.series.seriesId] }{ ['anidb', episode.series, episode.series.animeId] }
		
		log.finest "${show} | ${episode} | ${f}"
		
		if (episode != null && show != null) {
			episodes << episode
			shows << show
		}
	}
}


def episodeList = shows.collectMany{ s -> 
	(s[0] == 'tvdb' ? TheTVDB : AniDB).getEpisodeList(s[1])
}

// keep only normal episodes
episodeList = episodeList.findAll{ e ->
	e.episode != null && e.special == null
}


episodeList = episodeList as LinkedHashSet
episodeList.removeAll(episodes)

// print missing episodes
episodeList.each{ e ->
	println e
}
