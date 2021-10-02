#!/usr/bin/env filebot -script


// require mkvtoolnix and atomicparsley
mkvpropedit   = any{ mkvpropedit   }{ 'mkvpropedit'   }
atomicparsley = any{ atomicparsley }{ 'AtomicParsley' }


def mkv(f, m) {
	def args = ['--verbose', f, '--edit', 'info', '--set', "title=$m"]

	def xml = null
	def cover = null

	if (m instanceof Episode) {
		xml = XML {
			Tags {
				Tag {
					Targets {
						TargetTypeValue('70')
					}
					if (m.seriesInfo.database =~ /TheTVDB/) {
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
		cover = poster(m)
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
					Simple {
						Name('XATTR')
						String(m.toJsonString())
					}
				}
			}
		}
		cover = poster(m)
	}

	if (xml) {
		def tags = File.createTempFile('tags', '.xml')
		tags.deleteOnExit()

		log.finest(xml)
		xml.saveAs(tags)

		args += ['--tags', "global:$tags"]
	}
	
	if (cover) {
		if (f.mediaInfo.General.Attachments =~ /cover.png/) {
			args += ['--attachment-name', 'cover.png', '--attachment-mime-type', 'image/png', '--replace-attachment', "name:cover.png:$cover"]
		} else {
			args += ['--attachment-name', 'cover.png', '--attachment-mime-type', 'image/png', '--add-attachment', cover]
		}
	}

	execute(mkvpropedit, *args)
}


def mp4(f, m) {
	def options = [
			'--title'        : m,
			'--hdvideo'      : f.mediaCharacteristics?.height >= 1000
	]

	if (m instanceof Episode) {
		options << [
			'--stik'         : 'TV Show',
			'--year'         : m.airdate?.toInstant(),
			'--TVShowName'   : m.seriesName,
			'--TVEpisodeNum' : m.episode,
			'--TVSeasonNum'  : m.season,
			'--description'  : m.title,
			'--genre'        : m.seriesInfo?.genres[0],
			'--TVNetwork'    : m.seriesInfo?.network,
			'--artist'       : m.info?.director,
			'--longdesc'     : m.info?.overview,
			'--artwork'      : poster(m)
		]
	}

	if (m instanceof Movie) {
		options << [
			'--stik'        : 'Movie',
			'--year'        : m.info?.released?.toInstant() ?: m.year,
			'--artist'      : m.info?.director,
			'--grouping'    : m.info?.collection,
			'--genre'       : m.info?.genres[0],
			'--description' : m.info?.tagline,
			'--longdesc'    : m.info?.overview,
			'--artwork'     : poster(m)
		]
	}

	if (m instanceof MoviePart) {
		options << [
			'--disk'        : m.partIndex + '/' + m.partCount
		]
	}

	def args = options.findAll{ k, v -> v }.collectMany{ k, v -> [k, v] }

	// override existing artwork
	if (options.'--artwork') {
		args = ['--artwork', 'REMOVE_ALL', *args]
	}

	execute(atomicparsley, f, *args, '--overWrite')
}


def poster(m) {
	def url = null

	if (m instanceof Episode) {
		url = any{ m.series.poster }{ null }
	}
	if (m instanceof Movie) {
		url = any{ m.info.poster }{ null }
	}

	if (url) {
		try {
			def bytes = url.cache().get()
			def image = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes))

			def file = File.createTempFile('poster', '.png')
			file.deleteOnExit()

			javax.imageio.ImageIO.write(image, 'png', file)
			return file
		} catch(e) {
			log.warning "$e.message [$url]"
		}
	}

	log.finest "[POSTER NOT FOUND] $m"
	return null
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
