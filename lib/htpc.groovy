import static groovy.json.StringEscapeUtils.*


/**
 * XBMC helper functions
 */
def scanVideoLibrary(host, port) {
	tryLogCatch {
		telnet(host, port) { writer, reader ->
			writer.println("""{"jsonrpc":"2.0","method":"VideoLibrary.Scan","id":1}""")
		}
	}
}

def showNotification(host, port, title, message, image) {
	tryLogCatch {
		telnet(host, port) { writer, reader ->
			writer.println("""{"jsonrpc":"2.0","method":"GUI.ShowNotification","params":{"title":"${escapeJavaScript(title)}","message":"${escapeJavaScript(message)}", "image":"${escapeJavaScript(image)}"},"id":1}""")
		}
	}
}



/**
 * Plex helpers
 */
def refreshPlexLibrary(server, port = 32400, token = null) {
	tryLogCatch {
		// use HTTPS if hostname is specified, use HTTP if IP is specified
		def protocol = server.split('[.]').length == 4 ? 'http' : 'https'
		def url = "$protocol://$server:$port/library/sections/all/refresh"
		if (token) {
			url += "?X-Plex-Token=$token"
		}
		log.finest "GET: $url"
		new URL(url).get()
	}
}



/**
 * TheTVDB artwork/nfo helpers
 */
def fetchSeriesBanner(outputFile, series, bannerType, bannerType2, season, override, locale) {
	if (outputFile.exists() && !override) {
		log.finest "Banner already exists: $outputFile"
		return outputFile
	}

	// select and fetch banner
	def banner = [locale, null].findResult { TheTVDB.getBanner(series, [BannerType:bannerType, BannerType2:bannerType2, Season:season, Language:it]) }
	if (banner == null) {
		log.finest "Banner not found: $outputFile / $bannerType:$bannerType2"
		return null
	}
	log.finest "Fetching $outputFile => $banner"
	return banner.url.saveAs(outputFile)
}

def fetchSeriesFanart(outputFile, series, type, season, override, locale) {
	if (outputFile.exists() && !override) {
		log.finest "Fanart already exists: $outputFile"
		return outputFile
	}

	def fanart = [locale, null].findResult{ lang -> FanartTV.getSeriesArtwork(series.seriesId).find{ type == it.type && (season == null || season == it.season) && (lang == null || lang == it.language) }}
	if (fanart == null) {
		log.finest "Fanart not found: $outputFile / $type"
		return null
	}
	log.finest "Fetching $outputFile => $fanart"
	return fanart.url.saveAs(outputFile)
}

def fetchSeriesNfo(outputFile, seriesInfo, override, locale) {
	def i = seriesInfo
	XML {
		tvshow {
			title(i.name)
			sorttitle([i.name, i.firstAired as String].findAll{ it?.length() > 0 }.findResults{ it.sortName('$2') }.join(' :: '))
			year(i.firstAired?.year)
			rating(i.rating)
			votes(i.ratingCount)
			plot(i.overview)
			runtime(i.runtime)
			mpaa(i.contentRating)
			id(i.id)
			episodeguide {
				url(cache:"${i.id}.xml", "http://www.thetvdb.com/api/1D62F2F90030C444/series/${i.id}/all/${locale.language}.zip")
			}
			i.genres?.each{
				genre(it)
			}
			thumb(i.bannerUrl)
			premiered(i.firstAired)
			status(i.status)
			studio(i.network)
			i.actors?.each{ n ->
				actor {
					name(n)
				}
			}
			tvdb(id:i.id, "http://www.thetvdb.com/?tab=series&id=${i.id}")
		}
	}
	.saveAs(outputFile)
}

def fetchSeriesArtworkAndNfo(seriesDir, seasonDir, series, season, override = false, locale = Locale.ENGLISH) {
	tryLogCatch {
		// fetch nfo
		def seriesInfo = TheTVDB.getSeriesInfo(series, locale)
		fetchSeriesNfo(seriesDir.resolve('tvshow.nfo'), seriesInfo, override, locale)

		// fetch series banner, fanart, posters, etc
		['680x1000', null].findResult{ fetchSeriesBanner(seriesDir.resolve('poster.jpg'), series, 'poster', it, null, override, locale) }
		['graphical', null].findResult{ fetchSeriesBanner(seriesDir.resolve('banner.jpg'), series, 'series', it, null, override, locale) }

		// fetch highest resolution fanart
		['1920x1080', '1280x720', null].findResult{ fetchSeriesBanner(seriesDir.resolve('fanart.jpg'), series, 'fanart', it, null, override, locale) }

		// fetch season banners
		if (seasonDir != seriesDir) {
			fetchSeriesBanner(seasonDir.resolve('poster.jpg'), series, 'season', 'season', season, override, locale)
			fetchSeriesBanner(seasonDir.resolve('banner.jpg'), series, 'season', 'seasonwide', season, override, locale)
			
			// folder image (resuse series poster if possible)
			copyIfPossible(seasonDir.resolve('poster.jpg'), seasonDir.resolve('folder.jpg'))
		}

		// fetch fanart
		fetchSeriesFanart(seriesDir.resolve('clearart.png'), series, 'clearart', null, override, locale)
		fetchSeriesFanart(seriesDir.resolve('logo.png'), series, 'clearlogo', null, override, locale)
		fetchSeriesFanart(seriesDir.resolve('landscape.jpg'), series, 'tvthumb', null, override, locale)

		// fetch season fanart
		if (seasonDir != seriesDir) {
			fetchSeriesFanart(seasonDir.resolve('landscape.jpg'), series, 'seasonthumb', season, override, locale)
		}

		// folder image (resuse series poster if possible)
		copyIfPossible(seriesDir.resolve('poster.jpg'), seriesDir.resolve('folder.jpg'))
	}
}



/**
 * TheMovieDB artwork/nfo helpers
 */
def fetchMovieArtwork(outputFile, movieInfo, category, override, locale) {
	if (outputFile.exists() && !override) {
		log.finest "Artwork already exists: $outputFile"
		return outputFile
	}

	// select and fetch artwork
	def artwork = TheMovieDB.getArtwork(movieInfo.id as String)
	def selection = [locale.language, 'en', null].findResult{ l -> artwork.find{ (l == it.language || l == null) && it.category == category } }
	if (selection == null) {
		log.finest "Artwork not found: $outputFile"
		return null
	}
	log.finest "Fetching $outputFile => $selection"
	return selection.url.saveAs(outputFile)
}

def fetchAllMovieArtwork(outputFolder, movieInfo, category, override, locale) {
	// select and fetch artwork
	def artwork = TheMovieDB.getArtwork(movieInfo.id as String)
	def selection = [locale.language, 'en', null].findResults{ l -> artwork.findAll{ (l == it.language || l == null) && it.category == category } }.flatten().findAll{ it?.url }.unique()
	if (selection == null) {
		log.finest "Artwork not found: $outputFolder"
		return null
	}
	selection.eachWithIndex{ s, i ->
		def outputFile = new File(outputFolder, "$category-${(i+1).pad(2)}.jpg")
		if (outputFile.exists() && !override) {
			log.finest "Artwork already exists: $outputFile"
		} else {
			log.finest "Fetching $outputFile => $s"
			s.url.saveAs(outputFile)
		}
	}
}

def fetchMovieFanart(outputFile, movieInfo, type, diskType, override, locale) {
	if (outputFile.exists() && !override) {
		log.finest "Fanart already exists: $outputFile"
		return outputFile
	}

	def fanart = [locale, null].findResult{ lang -> FanartTV.getMovieArtwork(movieInfo.id).find{ type == it.type && (diskType == null || diskType == it.diskType) && (lang == null || lang == it.language) }}
	if (fanart == null) {
		log.finest "Fanart not found: $outputFile / $type"
		return null
	}
	log.finest "Fetching $outputFile => $fanart"
	return fanart.url.saveAs(outputFile)
}

def fetchMovieNfo(outputFile, movieInfo, movieFile, override) {
	def i = movieInfo
	def mi = tryLogCatch{ movieFile?.isFile() ? MediaInfo.snapshot(movieFile) : null }
	XML {
		movie {
			title(i.name)
			originaltitle(i.originalName)
			sorttitle([i.collection, i.name, i.released as String].findAll{ it?.length() > 0 }.findResults{ it.sortName('$2') }.join(' :: '))
			set(i.collection)
			year(i.released?.year)
			rating(i.rating)
			votes(i.votes)
			mpaa(i.certification)
			id('tt' + (i.imdbId ?: 0).pad(7))
			plot(i.overview)
			tagline(i.tagline)
			runtime(i.runtime)
			i.genres.each{
				genre(it)
			}
			i.productionCountries.each{
				country(it)
			}
			i.productionCompanies.each{
				studio(it)
			}
			i.people.each{ p ->
				if (p.director) {
					director(p.name)
				} else if (p.writer) {
					writer(p.name)
				} else if (p.actor) { 
					actor {
						name(p.name)
						role(p.character)
					}
				} else if (p.job ==~ /Writer|Screenplay|Story|Novel/) {
					credits("$p.name ($p.job)")
				}
			}
			i.trailers.each{ t ->
				t.sources.each { s, v ->
					trailer(type:t.type, name:t.name, size:s, v)
				}
			}
			fileinfo {
				streamdetails {
					mi?.each { kind, streams ->
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
									channels(s.'Channel(s)')
								}
							}
							if (section == 'text') {
								subtitle { language(s.'Language/String3') }
							}
						}
					}
				}
			}
			imdb(id:"tt" + (i.imdbId ?: 0).pad(7), "http://www.imdb.com/title/tt" + (i.imdbId ?: 0).pad(7))
			tmdb(id:i.id, "http://www.themoviedb.org/movie/${i.id}")
		}
	}
	.saveAs(outputFile)
}

def fetchMovieArtworkAndNfo(movieDir, movie, movieFile = null, extras = false, override = false, locale = Locale.ENGLISH) {
	tryLogCatch {
		def movieInfo = TheMovieDB.getMovieInfo(movie, locale, true)

		// fetch nfo
		fetchMovieNfo(movieDir.resolve('movie.nfo'), movieInfo, movieFile, override)

		// generate url files
		if (extras) {
			[[db:'imdb', id:movieInfo.imdbId, url:'http://www.imdb.com/title/tt' + (movieInfo.imdbId ?: 0).pad(7)], [db:'tmdb', id:movieInfo.id, url:"http://www.themoviedb.org/movie/${movieInfo.id}"]].each{
				if (it.id > 0) {
					def content = "[InternetShortcut]\nURL=${it.url}\n"
					content.saveAs(movieDir.resolve("${it.db}.url"))
				}
			}
		}

		// fetch series banner, fanart, posters, etc
		fetchMovieArtwork(movieDir.resolve('poster.jpg'), movieInfo, 'posters', override, locale)
		fetchMovieArtwork(movieDir.resolve('fanart.jpg'), movieInfo, 'backdrops', override, locale)

		fetchMovieFanart(movieDir.resolve('clearart.png'), movieInfo, 'movieart', null, override, locale)
		fetchMovieFanart(movieDir.resolve('logo.png'), movieInfo, 'movielogo', null, override, locale)
		['bluray', 'dvd', null].findResult { diskType -> fetchMovieFanart(movieDir.resolve('disc.png'), movieInfo, 'moviedisc', diskType, override, locale) }

		if (extras) {
			fetchAllMovieArtwork(movieDir.resolve('backdrops'), movieInfo, 'backdrops', override, locale)
		}

		// folder image (reuse movie poster if possible)
		copyIfPossible(movieDir.resolve('poster.jpg'), movieDir.resolve('folder.jpg'))
	}
}

def copyIfPossible(File src, File dst) {
	if (src.exists() && !dst.exists()) {
		src.copyAs(dst)
	}
}
