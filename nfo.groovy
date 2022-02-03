#!/usr/bin/env filebot -script


def fetchMovieNfo(m, f) {
	def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
	if (nfoFile.exists()) {
		return
	}

	log.info "Generate Movie NFO: $m [$nfoFile]"
	def i = m.info

	def xml = XML {
		movie {
			if (m.imdbId) {
				imdb(id:"tt" + m.imdbId.pad(7), "http://www.imdb.com/title/tt" + m.imdbId.pad(7))
			}
			if (m.tmdbId) {
				tmdb(id:m.tmdbId, "http://www.themoviedb.org/movie/" + m.tmdbId)
			}

			title(i.name)
			originaltitle(i.originalName)
			set(i.collection)
			year(i.released?.year)
			rating(i.rating)
			votes(i.votes)
			mpaa(i.certification)
			id(i.id)
			plot(i.overview)
			tagline(i.tagline)
			runtime(i.runtime)

			i.genres.each{ g ->
				genre(g)
			}
			i.keywords.each{ k ->
				tag(k)
			}
			i.productionCountries.each{ c ->
				country(c)
			}
			i.productionCompanies.each{ c ->
				studio(c)
			}

			creditsFragment(delegate, i.people)
			fileFragment(delegate, f)
		}
	}

	// write movie nfo file
	xml.saveAs(nfoFile)
}


def fetchSeriesNfo(m, f) {
	def seriesFolder = f.dir.dir
	def nfoFile = seriesFolder / seriesFolder.name + '.nfo'
	if (nfoFile.exists()) {
		return
	}

	log.info "Generate Series NFO: $m.seriesInfo [$nfoFile]"
	def s = m.seriesInfo.details

	def xml = XML {
		tvshow {
			if (s.database =~ 'TheTVDB') {
				uniqueid(type:'tvdb', s.id)
			}
			if (s.database =~ 'TheMovieDB') {
				uniqueid(type:'tmdb', s.id)
			}

			title(s.name)
			year(s.startDate?.year)
			rating(s.rating)
			votes(s.ratingCount)
			plot(s.overview)
			runtime(s.runtime)
			mpaa(s.certification)
			premiered(s.startDate)
			status(s.status)
			studio(s.network)
			episodeguide(s.id)
			id(s.id)

			s.genres.each{ g ->
				genre(g)
			}

			creditsFragment(delegate, s.crew)
		}
	}

	// write series nfo file
	xml.saveAs(nfoFile)
}


def fetchEpisodeNfo(m, f) {
	def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
	if (nfoFile.exists()) {
		return
	}

	log.info "Generate Episode NFO: $m [$nfoFile]"
	def s = m.seriesInfo

	def xml = XML {
		m.each{ episodePart ->
			// retrieve episode information for each episode or multi-episode component
			def e = episodePart.info

			episodedetails {
				if (s.database =~ 'TheTVDB') {
					uniqueid(type:'tvdb', e.id)
				}
				if (s.database =~ 'TheMovieDB') {
					uniqueid(type:'tmdb', e.id)
				}

				title(e.title)
				season(e.season)
				episode(e.episode)
				aired(e.airdate)
				plot(e.overview)
				thumb(aspect:'thumb', e.image)

				creditsFragment(delegate, e.people)
				fileFragment(delegate, f)
			}
		}
	}

	// write episode nfo file
	xml.saveAs(nfoFile)

	// write episode thumbnail to file
	def thumbnailFile = nfoFile.dir / nfoFile.nameWithoutExtension + '.jpg'
	if (thumbnailFile.exists()) {
		return
	}

	def thumbnailUrl = m.info.image
	if (thumbnailUrl) {
		log.fine "Fetch $thumbnailFile [$thumbnailUrl]"
		thumbnailUrl.saveAs(thumbnailFile)
	}
}


def creditsFragment(element, crew) {
	crew.each{ p ->
		if (p.director) {
			element.director(p.name)
		} else if (p.writer) {
			element.credits(p.name)
		} else if (p.actor) { 
			element.actor {
				name(p.name)
				role(p.character)
			}
		} else if (p.department == 'Writing') {
			element.credits("$p.name ($p.job)")
		}
	}
}


def fileFragment(element, file) {
	element.fileinfo {
		streamdetails {
			MediaInfo.snapshot(file).each { kind, streams ->
				def section = kind.toString().toLowerCase()
				streams.each { s ->
					if (section == 'video') {
						video {
							codec((s.'Encoded_Library/Name' ?: s.'CodecID/Hint' ?: s.'Format').replaceAll(/[ ].+/, '').trim())
							aspect(s.'DisplayAspectRatio')
							width(s.'Width')
							height(s.'Height')
						}
					}
					if (section == 'audio') {
						audio {
							codec((s.'CodecID/Hint' ?: s.'Format').replaceAll(/\p{Punct}/, '').trim())
							language(s.'Language/String3')
							channels(s.'Channel(s)_Original' ?: s.'Channel(s)')
						}
					}
					if (section == 'text') {
						subtitle { language(s.'Language/String3') }
					}
				}
			}
		}
	}
}




args.getFiles{ it.video }.each{ f ->
	def m = f.metadata
	switch(m) {
		case Movie:
			log.finest "[MOVIE] $m [$f]"
			fetchMovieNfo(m, f)
			break
		case Episode:
			log.finest "[EPISODE] $m [$f]"
			fetchSeriesNfo(m, f)
			fetchEpisodeNfo(m, f)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
