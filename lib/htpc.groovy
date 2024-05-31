htpc = [
	kodi: [ http: 8080 ],
	plex: [ http: 32400],
	emby: [ http: 8096, https: 8920 ]
]


/**
 * Kodi helper functions
 */
def scanVideoLibrary(host, port) {
	def json = [jsonrpc: '2.0', method: 'VideoLibrary.Scan', id: 1]
	postKodiRPC(host, port, json)
}

def showNotification(host, port, title, message, image) {
	def json = [jsonrpc:'2.0', method:'GUI.ShowNotification', params: [title: title, message: message, image: image], id: 1]
	postKodiRPC(host, port, json)
}

def postKodiRPC(host, port, json) {
	def url = "http://$host:${port ?: htpc.kodi.http}/jsonrpc"
	def data = JsonOutput.toJson(json)

	log.finest "POST: $url $data"
	new URL(url).post(data.getBytes('UTF-8'), 'application/json', [:])
}



/**
 * Plex helpers
 */
def encodeQueryString(parameters) {
	def query = parameters.findAll{ k, v -> k && v }.collect{ k, v -> k + '=' + URLEncoder.encode(v, 'UTF-8') }.join('&')
	return query ? '?' + query : ''
}

def refreshPlexLibrary(server, port, token, files) {
	// use HTTPS if hostname is specified, use HTTP if IP is specified
	def protocol = server ==~ /localhost|[0-9.:]+/ ? 'http' : 'https'
	def endpoint = "${protocol}://${server}:${port ?: htpc.plex.http}/library/sections"
	// pass authentication token via query parameters
	def auth = ['X-Plex-Token': token]

	// try to narrow down the rescan request to a specific library and folder path
	def requests = [] as SortedSet
	if (files) {
		// request remote library information
		def libraryRoot = [:].withDefault{ [] }
		def xml = new XmlSlurper().parse(endpoint + encodeQueryString(auth))
		xml.'Directory'.'Location'.collect{ location ->
			def key = location.'..'.'@key'.text()
			def path = location.'@path'.text()
			def root = path.split(/[\\\/]/).last()
			libraryRoot[root] += [key: key, path: path]
		}

		def folders = files.findResults{ f -> f.dir } as SortedSet
		folders.collect{ f -> f.path.split(/[\\\/]/).tail() }.each{ components ->
			components.eachWithIndex{ c, i ->
				libraryRoot[c].each{ r ->
					def sectionKey = r.key
					def remotePath = r.path
					// scan specific library path
					if (i < components.size() - 1) {
						remotePath += '/' + components[i+1..-1].join('/')
					}
					requests += endpoint + '/' + sectionKey + '/refresh' + encodeQueryString(path: remotePath, *: auth)
				}
			}
		}
	}

	// refresh all libraries as a last resort
	if (requests.empty) {
		requests += endpoint + '/all/refresh' + encodeQueryString(auth)
	}

	requests.each{ url ->
		log.finest "GET: $url"
		new URL(url).get()
	}
}


/**
 * Jellyfin helpers
 */
def refreshJellyfinLibrary(server, port, token) {
	// use HTTPS if hostname is specified, use HTTP if IP is specified
	def protocol = server ==~ /localhost|[0-9.:]+/ ? 'http' : 'https'
	def url = "${protocol}://${server}:${port ?: htpc.emby[protocol]}/Library/Refresh"
	if (token) {
		url += "?api_key=$token"
	}
	log.finest "POST: $url"
	new URL(url).post([:], [:])
}


/**
 * TheTVDB artwork/nfo helpers
 */
def fetchSeriesBanner(outputFile, series, bannerType, bannerType2, season, override, locale) {
	if (outputFile.exists() && !override) {
		return outputFile
	}

	def artwork = series.getArtwork(bannerType, locale)
	if (artwork == null) {
		return null
	}

	def banner = artwork.find{ it.matches(bannerType2, season) }
	if (banner == null) {
		return null
	}

	log.finest "Fetching $outputFile => $banner"
	return banner.url.cache().saveAs(outputFile)
}

def fetchSeriesFanart(outputFile, series, type, season, override, locale) {
	if (outputFile.exists() && !override) {
		return outputFile
	}

	def artwork = FanartTV.getArtwork(series.id, "tv", locale)
	def fanart = artwork.find{ it.matches(type, season) }
	if (fanart == null) {
		return null
	}

	log.finest "Fetching $outputFile => $fanart"
	return fanart.url.cache().saveAs(outputFile)
}

def fetchSeriesNfo(outputFile, i, locale) {
	// generate nfo file
	log.finest "Generate Series NFO: $i.name [$i]"
	def xml = XML {
		tvshow {
			title(i.name)
			sorttitle([i.name, i.startDate as String].findAll{ it?.length() > 0 }.findResults{ it.sortName() }.join(' :: '))
			year(i.startDate?.year)
			rating(i.rating)
			votes(i.ratingCount)
			plot(i.overview)
			runtime(i.runtime)
			mpaa(i.certification)
			id(i.id)
			i.genres.each{
				genre(it)
			}
			premiered(i.startDate)
			status(i.status)
			studio(i.network)
			episodeguide(i.id)
			tvdb(id:i.id, 'https://thetvdb.com/series/' + i.slug)
		}
	}

	xml.saveAs(outputFile)
}

def getSeriesID(series, locale) {
	if (series.database =~ /TheTVDB/) {
		return TheTVDB.getSeriesInfo(series.id, locale)
	}
	if (series.database =~ /TheMovieDB/) {
		def eid = TheMovieDB_TV.getExternalIds(series.id).'tvdb_id'
		if (eid) {
			return TheTVDB.getSeriesInfo(eid as int, locale)
		}
	}
	return null
}

def fetchSeriesArtworkAndNfo(seriesDir, seasonDir, series, season, override = false, locale = Locale.ENGLISH) {
	tryLogCatch {
		def sid = getSeriesID(series, locale)

		if (sid == null) {
			log.finest "Artwork not supported: $series"
			return
		}

		// fetch nfo
		fetchSeriesNfo(seriesDir.resolve('tvshow.nfo'), sid, locale)

		// series artwork
		fetchSeriesBanner(seriesDir.resolve('poster.jpg'), sid, 'posters', 'series', null, override, locale)
		fetchSeriesBanner(seriesDir.resolve('banner.jpg'), sid, 'banners', 'series', null, override, locale)
		fetchSeriesBanner(seriesDir.resolve('fanart.jpg'), sid, 'backgrounds', 'series', null, override, locale)

		// season artwork
		if (seasonDir != seriesDir) {
			fetchSeriesBanner(seasonDir.resolve('poster.jpg'), sid, 'posters', 'season', season, override, locale)
			fetchSeriesBanner(seasonDir.resolve('banner.jpg'), sid, 'banners', 'season', season, override, locale)
		}

		// external series artwork
		['hdclearart', 'clearart'].findResult{ type -> fetchSeriesFanart(seriesDir.resolve('clearart.png'), sid, type, null, override, locale) }
		['hdtvlogo', 'clearlogo'].findResult{ type -> fetchSeriesFanart(seriesDir.resolve('logo.png'), sid, type, null, override, locale) }
		fetchSeriesFanart(seriesDir.resolve('landscape.jpg'), sid, 'tvthumb', null, override, locale)

		// external season artwork
		if (seasonDir != seriesDir) {
			fetchSeriesFanart(seasonDir.resolve('landscape.jpg'), sid, 'seasonthumb', season, override, locale)
		}

		// folder image (resuse series / season poster if possible)
		copyIfPossible(seasonDir.resolve('poster.jpg'), seasonDir.resolve('folder.jpg'))
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
	def artwork = TheMovieDB.getArtwork(movieInfo.id, category, locale)
	def selection = artwork[0]
	if (selection == null) {
		log.finest "Artwork not found: $outputFile"
		return null
	}
	log.finest "Fetching $outputFile => $selection"
	return selection.url.cache().saveAs(outputFile)
}

def fetchMovieFanart(outputFile, movieInfo, type, diskType, override, locale) {
	if (outputFile.exists() && !override) {
		log.finest "Fanart already exists: $outputFile"
		return outputFile
	}

	def artwork = FanartTV.getArtwork(movieInfo.id, "movies", locale)
	def fanart = artwork.find{ it.matches(type, diskType) }
	if (fanart == null) {
		log.finest "Fanart not found: $outputFile / $type"
		return null
	}
	log.finest "Fetching $outputFile => $fanart"
	return fanart.url.cache().saveAs(outputFile)
}

def fetchMovieNfo(outputFile, i, movieFile) {
	log.finest "Generate Movie NFO: $i.name [$i.id]"
	def mi = tryLogCatch{ movieFile?.mediaInfo }
	def xml = XML {
		movie {
			title(i.name)
			originaltitle(i.originalName)
			sorttitle([i.collection, i.name, i.released as String].findAll{ it?.length() > 0 }.findResults{ it.sortName() }.join(' :: '))
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
			i.keywords.each{
				tag(it)
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
					credits(p.name)
				} else if (p.actor) { 
					actor {
						name(p.name)
						role(p.character)
					}
				} else if (p.department == 'Writing') {
					credits("$p.name ($p.job)")
				}
			}
			fileinfo {
				streamdetails {
					mi?.Video.each{ s ->
						video {
							codec((s.'Encoded_Library/Name' ?: s.'CodecID/Hint' ?: s.'Format').replaceAll(/[ ].+/, '').trim())
							aspect(s.'DisplayAspectRatio')
							width(s.'Width')
							height(s.'Height')
						}
					}
					mi?.Audio.each{ s ->
						audio {
							codec((s.'CodecID/Hint' ?: s.'Format').replaceAll(/\p{Punct}/, '').trim())
							language(s.'Language/String3')
							channels(s.'Channel(s)_Original' ?: s.'Channel(s)')
						}
					}
					mi?.Text.each{ s ->
						subtitle { language(s.'Language/String3') }
					}
				}
			}
			imdb(id:"tt" + (i.imdbId ?: 0).pad(7), "http://www.imdb.com/title/tt" + (i.imdbId ?: 0).pad(7))
			tmdb(id:i.id, "http://www.themoviedb.org/movie/${i.id}")

			/** <trailer> element not supported due to lack of specification on acceptable values for both Plex and Kodi **/
		}
	}
	xml.saveAs(outputFile)
}

def fetchMovieArtworkAndNfo(movieDir, movie, movieFile = null, override = false, locale = Locale.ENGLISH) {
	tryLogCatch {
		def movieInfo = TheMovieDB.getMovieInfo(movie, locale, true)

		// fetch nfo
		fetchMovieNfo(movieDir.resolve('movie.nfo'), movieInfo, movieFile)

		// fetch series banner, fanart, posters, etc
		fetchMovieArtwork(movieDir.resolve('poster.jpg'), movieInfo, 'posters', override, locale)
		fetchMovieArtwork(movieDir.resolve('fanart.jpg'), movieInfo, 'backdrops', override, Locale.ROOT) // prefer no language backdrops

		['hdmovieclearart', 'movieart'].findResult { type -> fetchMovieFanart(movieDir.resolve('clearart.png'), movieInfo, type, null, override, locale) }
		['hdmovielogo', 'movielogo'].findResult { type -> fetchMovieFanart(movieDir.resolve('logo.png'), movieInfo, type, null, override, locale) }
		['bluray', 'dvd', null].findResult { diskType -> fetchMovieFanart(movieDir.resolve('disc.png'), movieInfo, 'moviedisc', diskType, override, locale) }

		// folder image (reuse movie poster if possible)
		copyIfPossible(movieDir.resolve('poster.jpg'), movieDir.resolve('folder.jpg'))
	}
}

def copyIfPossible(File src, File dst) {
	if (src.exists() && !dst.exists()) {
		dst.bytes = src.bytes
	}
}
