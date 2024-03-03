#!/usr/bin/env filebot -script


def fetchMovieNfo(m, f) {
	def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
	if (nfoFile.exists()) {
		log.finest "[SKIP] NFO already exists: $nfoFile"
		return
	}

	log.info "Generate Movie NFO: $m [$nfoFile]"
	def i = m.info

	def xml = XML {
		movie {
			title(i.name)
			originaltitle(i.originalName)
			set(i.collection)
			year(i.released?.year)
			rating(i.rating)
			votes(i.votes)
			mpaa(i.certification)
			plot(i.overview)
			tagline(i.tagline)
			runtime(i.runtime)
			id(i.id)

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

			certificationFragment(delegate, i)
			crewFragment(delegate, i)
			fileFragment(delegate, f)

			if (i.imdbId) {
				imdb(id:'tt' + i.imdbId.pad(7), 'https://www.imdb.com/title/tt' + i.imdbId.pad(7))
			}
			tmdb(id:i.id, 'https://www.themoviedb.org/movie/' + i.id)
		}
	}

	// write movie nfo file
	xml.saveAs(nfoFile)
}


def fetchSeriesNfo(m, f) {
	def seriesFolder = f.dir.dir
	def nfoFile = seriesFolder / seriesFolder.name + '.nfo'
	if (nfoFile.exists()) {
		log.finest "[SKIP] NFO already exists: $nfoFile"
		return
	}

	log.info "Generate Series NFO: $m.seriesInfo [$nfoFile]"
	def s = m.seriesInfo.details

	def xml = XML {
		tvshow {
			if (s.database == 'TheTVDB') {
				uniqueid(type:'tvdb', s.id)
			}
			if (s.database == 'TheMovieDB::TV') {
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

			certificationFragment(delegate, s)
			crewFragment(delegate, s)
		}
	}

	// write series nfo file
	xml.saveAs(nfoFile)
}


def fetchEpisodeNfo(m, f) {
	def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
	if (nfoFile.exists()) {
		log.finest "[SKIP] NFO already exists: $nfoFile"
		return
	}

	log.info "Generate Episode NFO: $m [$nfoFile]"
	def s = m.seriesInfo

	def xml = XML {
		m.each{ episodePart ->
			// retrieve episode information for each episode or multi-episode component
			def e = episodePart.info
			if (e == null) {
				return
			}

			episodedetails {
				if (s.database == 'TheTVDB') {
					uniqueid(type:'tvdb', e.id)
				}
				if (s.database == 'TheMovieDB::TV') {
					uniqueid(type:'tmdb', e.id)
				}

				title(e.title)
				season(e.season)
				episode(e.episode)
				aired(e.airdate)
				plot(e.overview)
				thumb(aspect:'thumb', e.image)

				crewFragment(delegate, e)
				fileFragment(delegate, f)
			}
		}
	}

	if (xml.empty) {
		log.warning "Episode NFO not supported: $s"
		return
	}

	// write episode nfo file
	xml.saveAs(nfoFile)
}


def crewFragment(element, info) {
	info.crew.each{ p ->
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


def certificationFragment(element, info) {
	info.certifications.each{ k, v ->
		element.certification {
			country(k)
			rating(v)
		}
	}
}



def fileFragment(element, file) {
	def mi = file.mediaInfo

	element.fileinfo {
		streamdetails {
			mi.Video.each{ s ->
				video {
					codec(s.'Encoded_Library/Name' ?: s.'CodecID/Hint' ?: s.'Format')
					aspect(s.'DisplayAspectRatio')
					width(s.'Width')
					height(s.'Height')
				}
			}
			mi.Audio.each{ s ->
				audio {
					codec(s.'CodecID/Hint' ?: s.'Format')
					language(s.'Language/String3')
					channels(s.'Channel(s)_Original' ?: s.'Channel(s)')
				}
			}
			mi.Text.each{ s ->
				subtitle {
					language(s.'Language/String3')
				}
			}
		}
	}
}




def videoFiles = args.getFiles{ it.video }

// require input arguments
if (args.size() == 0) {
	die "Illegal usage: no input arguments"
}

// require video files
if (videoFiles.size() == 0) {
	die "Illegal usage: no video files"
}


videoFiles.each{ f ->
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
