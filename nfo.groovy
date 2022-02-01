#!/usr/bin/env filebot -script


def fetchEpisodeNfo(outputFile, episodeObject, episodeFile) {
	log.finest "Generate Episode NFO: $episodeObject [$outputFile]"

	def si = episodeObject.seriesInfo
	def ei = episodeObject.info
	def mi = MediaInfo.snapshot(episodeFile)

	def xml = XML {
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

	xml.saveAs(outputFile)
}



args.files.each{ f ->
	if (f.video) {
		def m = f.metadata
		if (m instanceof Episode) {
			def nfoFile = f.dir / f.nameWithoutExtension + '.nfo'
			fetchEpisodeNfo(nfoFile, m, f)
		}		
	}
}
