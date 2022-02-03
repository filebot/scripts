#!/usr/bin/env filebot -script


def fetchEpisodeNfo(outputFile, episodeObject, episodeFile) {
	def mi = MediaInfo.snapshot(episodeFile)
	def si = episodeObject.seriesInfo

	def xml = XML {
		episodeObject.each{ episodePart ->
			// retrieve episode information for each episode or multi-episode component
			def ei = episodePart.info

			episodedetails {
				if (si.database =~ 'TheTVDB') {
					uniqueid(type:'tvdb', ei.id)
				}
				if (si.database =~ 'TheMovieDB') {
					uniqueid(type:'tmdb', ei.id)
				}

				title(ei.title)
				showtitle(si.name)

				season(ei.season)
				episode(ei.episode)
				aired(ei.airdate)
				plot(ei.overview)
				runtime(si.runtime)
				thumb(aspect:'thumb', ei.image)

				ei.people.each { p ->
					if (p.director) {
						director(p.name)
					} else if (p.writer) {
						credits(p.name)
					} else if (p.actor) { 
						actor {
							name(p.name)
							role(p.character)
							order(p.order)
							thumb(p.image)
						}
					} else if (p.department == 'Writing') {
						credits("$p.name ($p.job)")
					}
				}

				fileinfo {
					streamdetails {
						mi.each { kind, streams ->
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
		}
	}

	// write episode nfo file
	xml.saveAs(outputFile)

	// write episode thumbnail to file
	def thumbnailFile = outputFile.dir / outputFile.nameWithoutExtension + '.jpg'
	if (thumbnailFile.exists()) {
		return
	}

	def thumbnailUrl = episodeObject.info.image
	if (thumbnailUrl) {
		log.fine "Fetch $thumbnailFile [$thumbnailUrl]"
		thumbnailUrl.saveAs(thumbnailFile)
	}
}




args.files.each{ f ->
	if (f.video) {
		def m = f.metadata
		if (m instanceof Episode) {
			def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
			if (nfoFile.exists()) {
				log.finest "Skip [$f.name] because [$nfoFile.name] already exists"
				return
			}

			log.info "Generate Episode NFO: $m [$nfoFile]"
			fetchEpisodeNfo(nfoFile, m, f)
		}
	}
}
