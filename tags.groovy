#!/usr/bin/env filebot -script


// require mkvtoolnix and mp4v2 tools
execute 'mkvpropedit', '--version'
execute 'mp4tags',     '--version'


void mkv(f, m) {
	def xml = null

	if (m instanceof Episode) {
		xml = XML {
			Tags {
				Tag {
					Targets {
						TargetTypeValue('70')
					}
					if (m.seriesInfo.database =~ /TheTVDBs/) {
						Simple {
							Name('TVDB')
							String(m.seriesInfo.id.pad(5))
						}
					}
					if (m.seriesInfo.database =~ /TheMovieDB/) {
						Simple {
							Name('TMDB')
							String('tv/' + m.seriesInfo.id)
						}
					}
				}
				Tag {
					Targets {
						TargetTypeValue('60')
					}
					Simple {
						Name('PART_NUMBER')
						String(m.season ?: 0)
					}
				}
				Tag {
					Targets {
						TargetTypeValue('50')
					}
					Simple {
						Name('PART_NUMBER')
						String(m.episode ?: 0)
					}
					Simple {
						Name('XATTR')
						String(m.toJsonString())
					}
				}				
			}
		}
	}

	if (m instanceof Movie) {
		xml = XML {
			Tags {
				Tag {
					Targets {
						TargetTypeValue('70')
					}
					if (m.imdbId > 0) {
						Simple {
							Name('IMDB')
							String('tt' + m.imdbId.pad(7))
						}
					}
					if (m.tmdbId > 0) {
						Simple {
							Name('TMDB')
							String('movie/' + m.tmdbId)
						}
					}
				}
			}
		}
	}

	def tags = File.createTempFile('tags', '.xml')
	tags.deleteOnExit()

	log.finest(xml)
	xml.saveAs(tags)	

	execute 'mkvpropedit', '--verbose', f, '--edit', 'info', '--set', "title=$m", '--tags', "global:$tags"
}


void mp4(f, m) {
	def options = [
			'-song'        : m,
			'-hdvideo'     : f.mediaCharacteristics?.height >= 1000 ? '1' : '0'
	]

	if (m instanceof Episode) {
		options << [
			'-type'        : 'tvshow',
			'-year'        : m.airdate,
			'-show'        : m.seriesName,
			'-episode'     : m.episode,
			'-season'      : m.season,
			'-description' : m.title,
			'-artist'      : m.info?.director,
			'-genre'       : m.seriesInfo?.genres[0],
			'-network'     : m.seriesInfo?.network,
			'-longdesc'    : m.info?.overview
		]
	}

	if (m instanceof Movie) {
		options << [
			'-type'        : 'movie',
			'-year'        : m.info?.released ?: m.year,
			'-artist'      : m.info?.director,
			'-grouping'    : m.info?.collection,
			'-genre'       : m.info?.genres[0],
			'-description' : m.info?.tagline,
			'-longdesc'    : m.info?.overview
		]
	}

	def args = options.findAll{ k, v -> v }.collectMany{ k, v -> [k, v] }
	execute('mp4tags', *args, f)
}



args.getFiles{ it.video }.each{ f ->
	def m = f.metadata
	if (m) {
		log.info "[TAG] Write [$m] to [$f]"
		switch(f.extension) {
			case ~/mkv/:
				mkv(f, m)
				break
			case ~/mp4|m4v/:
				mp4(f, m)
				break
			default:
				log.warning "[TAGS NOT SUPPORTED] $f"
				break
		}
	} else {
		log.finest "[XATTR NOT FOUND] $f"
	}
}
