// filebot -script "fn:amc" --output "X:/media" --action copy --conflict override --def subtitles=en music=y artwork=y "ut_dir=%D" "ut_file=%F" "ut_kind=%K" "ut_title=%N" "ut_label=%L" "ut_state=%S"

def input = []
def failOnError = _args.conflict == 'fail'

// print input parameters
_def.each{ n, v -> log.finer('Parameter: ' + [n, n =~ /pushover|pushbullet|gmail|mailto|myepisodes/ ? '*****' : v].join(' = ')) }
args.each{ log.finer("Argument: $it") }
args.findAll{ !it.exists() }.each{ throw new Exception("File not found: $it") }

// check user-defined pre-condition
if (tryQuietly{ !(ut_state ==~ ut_state_allow) }) {
	throw new Exception("Invalid state: ut_state = $ut_state (expected $ut_state_allow)")
}

// check ut mode vs standalone mode
if ((args.size() > 0 && (tryQuietly{ ut_dir }?.size() > 0 || tryQuietly{ ut_file }?.size() > 0)) || (args.size() == 0 && (tryQuietly{ ut_dir } == null && tryQuietly{ ut_file } == null))) {
	throw new Exception("Conflicting arguments: pass in either file arguments or ut_dir/ut_file parameters but not both")
}

// enable/disable features as specified via --def parameters
def unsorted  = tryQuietly{ unsorted.toBoolean() }
def music     = tryQuietly{ music.toBoolean() }
def subtitles = tryQuietly{ subtitles.split(/[ ,|]+/) as List }
def artwork   = tryQuietly{ artwork.toBoolean() && !'TEST'.equalsIgnoreCase(_args.action) }
def backdrops = tryQuietly{ backdrops.toBoolean() }
def clean     = tryQuietly{ clean.toBoolean() }
def exec      = tryQuietly{ exec.toString() }

// array of xbmc/plex hosts
def xbmc = tryQuietly{ xbmc.split(/[ ,|]+/) }
def plex = tryQuietly{ plex.split(/[ ,|]+/) }

// extra options, myepisodes updates and email notifications
def storeReport = tryQuietly{ storeReport.toBoolean() }
def skipExtract = tryQuietly{ skipExtract.toBoolean() }
def deleteAfterExtract = tryQuietly{ deleteAfterExtract.toBoolean() }
def excludeList = tryQuietly{ (excludeList as File).isAbsolute() ? (excludeList as File) : new File(_args.output, excludeList) }
def myepisodes = tryQuietly{ myepisodes.split(':', 2) }
def gmail = tryQuietly{ gmail.split(':', 2) }
def pushover = tryQuietly{ pushover.toString() }
def pushbullet = tryQuietly{ pushbullet.toString() }

// user-defined filters
def label = tryQuietly{ ut_label } ?: null
def ignore = tryQuietly{ ignore } ?: null
def minFileSize = tryQuietly{ minFileSize.toLong() }; if (minFileSize == null) { minFileSize = 50 * 1000L * 1000L }
def minLengthMS = tryQuietly{ minLengthMS.toLong() }; if (minLengthMS == null) { minLengthMS = 10 * 60 * 1000L }


// series/anime/movie format expressions
def format = [
	tvs:   tryQuietly{ seriesFormat } ?: '''TV Shows/{n}/{episode.special ? "Special" : "Season "+s.pad(2)}/{n} - {episode.special ? "S00E"+special.pad(2) : s00e00} - {t.replaceAll(/[`´‘’ʻ]/, "'").removeAll(/[!?.]+$/).replacePart(', Part $1')}{".$lang"}''',
	anime: tryQuietly{ animeFormat  } ?: '''Anime/{n}/{n} - {sxe} - {t.replaceAll(/[`´‘’ʻ]/, "'").removeAll(/[!?.]+$/).replacePart(', Part $1')}''',
	mov:   tryQuietly{ movieFormat  } ?: '''Movies/{n} ({y})/{n} ({y}){" CD$pi"}{".$lang"}''',
	music: tryQuietly{ musicFormat  } ?: '''Music/{n}/{album+'/'}{pi.pad(2)+'. '}{artist} - {t}'''
]


// force movie/series/anime logic
def forceMovie = { f ->
	label =~ /^(?i:Movie|Couch.Potato)/ || f.dir.listPath().any{ it.name ==~ /(?i:Movies)/ }  || f.path =~ /(?<=tt)\\d{7}/ || tryQuietly{ f.metadata?.object?.class.name =~ /Movie/ }
}

def forceSeries = { f ->
	label =~ /^(?i:TV|Kids.Shows)/ || f.dir.listPath().any{ it.name ==~ /(?i:TV.Shows)/ } || parseEpisodeNumber(f.path) || parseDate(f.path) || f.path =~ /(?i:Season)\D?[0-9]{1,2}\D/ || tryQuietly{ f.metadata?.object?.class.name =~ /Episode/ }
}

def forceAnime = { f ->
	label =~ /^(?i:Anime)/ || f.dir.listPath().any{ it.name ==~ /(?i:Anime)/ } || (f.isVideo() && (f.name =~ /(?i:HorribleSubs)/ || f.name =~ "[\\(\\[]\\p{XDigit}{8}[\\]\\)]" || (getMediaInfo(file:f, format:'''{media.AudioLanguageList} {media.TextCodecList}''').tokenize().containsAll(['Japanese', 'ASS']) && (parseEpisodeNumber(f.name, false) != null || getMediaInfo(file:f, format:'{minutes}').toInteger() < 60))))
}

def forceAudio = { f ->
	label =~ /^(?i:audio|music|music.video)/ || (f.isAudio() && !f.isVideo())
}

def forceIgnore = { f ->
	label =~ /^(?i:ebook|other|ignore)/ || f.path =~ ignore
}


// include artwork/nfo, pushover/pushbullet and ant utilities as required
if (artwork || xbmc || plex) { include('lib/htpc') }
if (pushover || pushbullet ) { include('lib/web') }
if (gmail) { include('lib/ant') }


// specify how to resolve input folders, e.g. grab files from all folders except disk folders
def resolveInput(f) {
	if (f.isDirectory() && !f.isDisk())
		return f.listFiles().toList().findResults{ resolveInput(it) }
	else
		return f
}

// collect input fileset as specified by the given --def parameters
def roots = []
if (args.empty) {
	// assume we're called with utorrent parameters (account for older and newer versions of uTorrents)
	if (ut_kind == 'single' || (ut_kind != 'multi' && ut_dir && ut_file)) {
		roots += new File(ut_dir, ut_file) // single-file torrent
	} else {
		roots += new File(ut_dir) // multi-file torrent
	}
} else {
	// assume we're called normally with arguments
	roots += args
}

// sanitize input
roots = roots.findAll{ it?.exists() }.collect{ it.canonicalFile }.unique() // roots could be folders as well as files

def relativeInputPath = { f ->
	def r = roots.find{ r -> f.path.startsWith(r.path) && r.isDirectory() && f.isFile() }
	if (r != null) {
		return f.path.substring(r.path.length() + 1)
	}
	return f.path
}


// flatten nested file structure
input = roots.flatten{ f -> resolveInput(f) }

// extract archives (zip, rar, etc) that contain at least one video file
def extractedArchives = []
def tempFiles = []
input = input.flatten{ f ->
	if (!skipExtract && (f.isArchive() || f.hasExtension('001'))) {
		def extractDir = new File(f.dir, f.nameWithoutExtension)
		def extractFiles = extract(file: f, output: new File(extractDir, f.dir.name), conflict: 'auto', filter: { it.isArchive() || it.isVideo() || (music && it.isAudio()) }, forceExtractAll: true) ?: []

		if (extractFiles.size() > 0) {
			extractedArchives += f
			tempFiles += extractDir
			tempFiles += extractFiles
		}
		return extractFiles
	}
	return f
}

// keep original input around so we can print excluded files later
def originalInputSet = input as LinkedHashSet
def videoFolderSet = input.findAll{ it.isVideo() }.findResults{ it.parentFile } as LinkedHashSet

// process only media files
input = input.findAll{ f -> (f.isVideo() && !tryQuietly{ f.hasExtension('iso') && !f.isDisk() }) || f.isSubtitle() || (f.isDirectory() && f.isDisk()) || (music && f.isAudio()) }

// ignore clutter files
input = input.findAll{ f -> !(relativeInputPath(f) =~ /(?<=\b|_)(?i:sample|trailer|extras|music.video|scrapbook|behind.the.scenes|extended.scenes|deleted.scenes|s\d{2}c\d{2}|mini.series|NCED|NCOP|(OP|ED)\p{Digit}\p{Alpha})(?=\b|_)/) }

// ignore video files that don't conform with the file-size and video-length limits
input = input.findAll{ f -> !(f.isVideo() && ((minFileSize > 0 && f.length() < minFileSize) || (minLengthMS > 0 && tryQuietly{ getMediaInfo(file:f, format:'{duration}').toLong() < minLengthMS }))) }

// ignore subtitles files that are not stored in the same folder as the movie
input = input.findAll{ f -> !(f.isSubtitle() && !videoFolderSet.contains(f.parentFile)) }

// check and update exclude list (e.g. to make sure files are only processed once)
if (excludeList) {
	// check excludes from previous runs
	def excludePathSet = excludeList.exists() ? excludeList.text.split('\n') as HashSet : []
	input = input.findAll{ f -> !excludePathSet.contains(f.path) }
}

// print exclude and input sets for logging
input.each{ f -> log.finer("Input: $f") }
(originalInputSet - input).each{ f -> log.finest("Exclude: $f") }


// group episodes/movies and rename according to XBMC standards
def groups = input.groupBy{ f ->
	// skip auto-detection if possible
	if (forceIgnore(f))
		return []
	if (music && forceAudio(f)) // process audio only if music mode is enabled
		return [music: f.dir.name]
	if (forceMovie(f))
		return [mov:   detectMovie(f, false)]
	if (forceSeries(f))
		return [tvs:   detectSeriesName(f, true, false) ?: detectSeriesName(f.dir.listFiles{ it.isVideo() }, true, false)]
	if (forceAnime(f))
		return [anime: detectSeriesName(f, false, true) ?: detectSeriesName(f.dir.listFiles{ it.isVideo() }, false, true)]
	
	
	def tvs = detectSeriesName(f, true, false)
	def mov = detectMovie(f, false)
	log.fine("$f.name [series: $tvs, movie: $mov]")
	
	// DECIDE EPISODE VS MOVIE (IF NOT CLEAR)
	if (tvs && mov) {
		def norm = { s -> s.ascii().normalizePunctuation().lower().space(' ') }
		def dn = norm(guessMovieFolder(f)?.name ?: '')
		def fn = norm(f.nameWithoutExtension)
		def sn = norm(tvs)
		def mn = norm(mov.name)
		
		/**
		println '--- EPISODE FILTER (POS) ---'
		println parseEpisodeNumber(fn, true) || parseDate(fn)
		println ([dn, fn].find{ it =~ sn && matchMovie(it, true) == null } && (parseEpisodeNumber(stripReleaseInfo(fn.after(sn), false), false) || fn.after(sn) =~ /\D\d{1,2}\D{1,3}\d{1,2}\D/) && matchMovie(fn, true) == null)
		println (fn.after(sn) ==~ /.{0,3} - .+/ && matchMovie(fn, true) == null)
		println f.dir.listFiles{ it.isVideo() && (dn =~ sn || norm(it.name) =~ sn) && it.name =~ /\d{1,3}/}.findResults{ it.name.matchAll(/\d{1,3}/) as Set }.unique().size() >= 10
		println '--- EPISODE FILTER (NEG) ---'
		println (mov.year >= 1950 && f.listPath().reverse().take(3).find{ it.name =~ mov.year })
		println (mn =~ sn && [dn, fn].find{ it =~ /(19|20)\d{2}/ })
		println '--- MOVIE FILTER (POS) ---'
		println (similarity(mn, fn) >= 0.8 || [dn, fn].find{ it.findAll( ~/\d{4}/ ).findAll{ y -> [mov.year-1, mov.year, mov.year+1].contains(y.toInteger()) }.size() > 0 } != null)
		println ([dn, fn].find{ it =~ mn && !(it.after(mn) =~ /\b\d{1,3}\b/) && (similarity(it, mn) > 0.2 + similarity(it, sn)) } != null)
		println (detectMovie(f, true) && [dn, fn].find{ it =~ /(19|20)\d{2}/ } != null)
		**/
		
		// S00E00 | 2012.07.21 | One Piece 217 | Firefly - Serenity | [Taken 1, Taken 2, Taken 3, Taken 4, ..., Taken 10]
		if ((parseEpisodeNumber(fn, true) || parseDate(fn) || ([dn, fn].find{ it =~ sn && matchMovie(it, true) == null } && (parseEpisodeNumber(stripReleaseInfo(fn.after(sn), false), false) || fn.after(sn) =~ /\D\d{1,2}\D{1,3}\d{1,2}\D/) && matchMovie(fn, true) == null) || (fn.after(sn) ==~ /.{0,3} - .+/ && matchMovie(fn, true) == null) || f.dir.listFiles{ it.isVideo() && (dn =~ sn || norm(it.name) =~ sn) && it.name =~ /\d{1,3}/}.findResults{ it.name.matchAll(/\d{1,3}/) as Set }.unique().size() >= 10 || mov.year < 1900) && !( (mov.year >= 1950 && f.listPath().reverse().take(3).find{ it.name =~ mov.year }) || (mn =~ sn && [dn, fn].find{ it =~ /(19|20)\d{2}/ }) ) ) {
			log.fine("Exclude Movie: $mov")
			mov = null
		} else if ((similarity(mn, fn) >= 0.8 || [dn, fn].find{ it.findAll( ~/\d{4}/ ).findAll{ y -> [mov.year-1, mov.year, mov.year+1].contains(y.toInteger()) }.size() > 0 } != null) || ([dn, fn].find{ it =~ mn && !(it.after(mn) =~ /\b\d{1,3}\b/) && (similarity(it, mn) > 0.2 + similarity(it, sn)) } != null) || (detectMovie(f, false) && [dn, fn].find{ it =~ /(19|20)\d{2}|(?i:CD)[1-9]/ } != null)) {
			log.fine("Exclude Series: $tvs")
			tvs = null
		}
	}
	
	// CHECK CONFLICT
	if (((mov && tvs) || (!mov && !tvs))) {
		if (failOnError) {
			throw new Exception("Media detection failed")
		} else {
			log.fine("Unable to differentiate: [$f.name] => [$tvs] VS [$mov]")
			return [tvs: null, mov: null, anime: null]
		}
	}
	
	return [tvs: tvs, mov: mov, anime: null]
}

// group entries by unique tvs/mov descriptor
groups = groups.groupBy{ group, files -> group.collectEntries{ type, query -> [type, query ? query.toString().ascii().normalizePunctuation().lower() : null] } }.collectEntries{ group, maps -> [group, maps.values().flatten()] }

// log movie/series/anime detection results
groups.each{ group, files -> log.finest("Group: $group => ${files*.name}") }

// process each batch
groups.each{ group, files ->
	// fetch subtitles (but not for anime)
	if (group.anime == null && subtitles != null && files.findAll{ it.isVideo() }.size() > 0) {
		subtitles.each{ languageCode ->
			def subtitleFiles = getMissingSubtitles(file:files, lang:languageCode, strict:true, output:'srt', encoding:'UTF-8', format:'MATCH_VIDEO_ADD_LANGUAGE_TAG') ?: []
			files += subtitleFiles
			tempFiles += subtitleFiles // if downloaded for temporarily extraced files delete later
		}
	}
	
	// EPISODE MODE
	if ((group.tvs || group.anime) && !group.mov) {
		// choose series / anime config
		def config = group.tvs ? [name:group.tvs,   format:format.tvs,   db:'TheTVDB', seasonFolder:true ]
		                       : [name:group.anime, format:format.anime, db:'AniDB',   seasonFolder:false]
		def dest = rename(file: files, format: config.format, db: config.db)
		if (dest && artwork) {
			dest.mapByFolder().each{ dir, fs ->
				def sxe = fs.findResult{ eps -> parseEpisodeNumber(eps) }
				def options = TheTVDB.search(detectSeriesName(fs, true, false), _args.locale)
				if (options.isEmpty()) {
					log.warning "TV Series not found: $config.name"
					return
				}
				def series = options.sortBySimilarity(config.name, { s -> s.name }).get(0)
				log.fine "Fetching series artwork for [$series] to [$dir]"
				fetchSeriesArtworkAndNfo(config.seasonFolder ? dir.dir : dir, dir, series, sxe && sxe.season > 0 ? sxe.season : 1)
			}
		}
		if (dest == null && failOnError) {
			throw new Exception("Failed to rename series: $config.name")
		}
	}
	
	// MOVIE MODE
	else if (group.mov && !group.tvs && !group.anime) {
		def dest = rename(file:files, format:format.mov, db:'TheMovieDB')
		if (dest && artwork) {
			dest.mapByFolder().each{ dir, fs ->
				def movieFile = fs.findAll{ it.isVideo() }.sort{ it.length() }.reverse().findResult{ it }
				if (movieFile != null) {
					def movie = detectMovie(movieFile, false)
					log.fine "Fetching movie artwork for [$movie] to [$dir]"
					fetchMovieArtworkAndNfo(dir, movie, movieFile, backdrops)
				}
			}
		}
		if (dest == null && failOnError) {
			throw new Exception("Failed to rename movie: $group.mov")
		}
	}
	
	// MUSIC MODE
	else if (group.music) {
		def dest = rename(file:files, format:format.music, db:'AcoustID')
		if (dest == null && failOnError) {
			throw new Exception("Failed to rename music: $group.music")
		}
	}
}


// run program on newly processed files
if (exec) {
	getRenameLog().each{ from, to ->
		def command = getMediaInfo(format: exec, file: to)
		log.finest("Execute: $command")
		execute(command)
	}
}


// messages used for xbmc / plex / pushover notifications
def getNotificationTitle = { "FileBot finished processing ${getRenameLog().size()} files" }
def getNotificationMessage = { tryQuietly{ ut_title } ?: input.collect{ relativeInputPath(it) as File }*.getRoot()*.getNameWithoutExtension().unique().sort{ it.toLowerCase() }.collect{ "• $it" }.join('\n') }

// make XMBC scan for new content and display notification message
if (xbmc) {
	xbmc.each{ host ->
		log.info "Notify XBMC: $host"
		tryLogCatch{
			showNotification(host, 9090, getNotificationTitle(), getNotificationMessage(), 'http://www.filebot.net/images/icon.png')
			scanVideoLibrary(host, 9090)
		}
	}
}

// make Plex scan for new content
if (plex) {
	plex.each{
		log.info "Notify Plex: $it"
		refreshPlexLibrary(it)
	}
}

// mark episodes as 'acquired'
if (myepisodes) {
	log.info 'Update MyEpisodes'
	executeScript('update-mes', [login:myepisodes.join(':'), addshows:true], getRenameLog().values())
}

if (pushover) {
	log.info 'Sending Pushover notification'
	Pushover(pushover).send(getNotificationTitle(), getNotificationMessage())
}


// messages used for email / pushbullet reports
def getReportSubject = { tryQuietly { ut_title } ?: input.collect{ relativeInputPath(it) as File }*.getRoot()*.getNameWithoutExtension()*.trim().unique().sort{ it.toLowerCase() }.join(', ') }
def getReportTitle = { '[FileBot] ' + getReportSubject() }
def getReportMessage = { 
	def renameLog = getRenameLog()
	'''<!DOCTYPE html>\n''' + XML {
		html {
			head {
				meta(charset:'UTF-8')
				style('''
					p{font-family:Arial,Helvetica,sans-serif}
					p b{color:#07a}
					hr{border-style:dashed;border-width:1px 0 0 0;border-color:lightgray}
					small{color:#d3d3d3;font-size:xx-small;font-weight:normal;font-family:Arial,Helvetica,sans-serif}
					table a:link{color:#666;font-weight:bold;text-decoration:none}
					table a:visited{color:#999;font-weight:bold;text-decoration:none}
					table a:active,table a:hover{color:#bd5a35;text-decoration:underline}
					table{font-family:Arial,Helvetica,sans-serif;color:#666;background:#eaebec;margin:15px;border:#ccc 1px solid;border-radius:3px;box-shadow:0 1px 2px #d1d1d1}
					table th{padding:15px;border-top:1px solid #fafafa;border-bottom:1px solid #e0e0e0;background:#ededed}
					table th{text-align:center;padding-left:20px}
					table tr:first-child th:first-child{border-top-left-radius:3px}
					table tr:first-child th:last-child{border-top-right-radius:3px}
					table tr{text-align:left;padding-left:20px}
					table td:first-child{text-align:left;padding-left:20px;border-left:0}
					table td{padding:15px;border-top:1px solid #fff;border-bottom:1px solid #e0e0e0;border-left:1px solid #e0e0e0;background:#fafafa;white-space:nowrap}
					table tr.even td{background:#f6f6f6}
					table tr:last-child td{border-bottom:0}
					table tr:last-child td:first-child{border-bottom-left-radius:3px}
					table tr:last-child td:last-child{border-bottom-right-radius:3px}
					table tr:hover td{background:#f2f2f2}
				''')
				title(getReportTitle())
			}
			body {
				p {
					mkp.yield("FileBot finished processing ")
					b(getReportSubject())
					mkp.yield(" (${renameLog.size()} files).")
				}
				hr(); table {
					tr { th('Original Name'); th('New Name'); th('New Location') }
					renameLog.each{ from, to ->
						tr { [from.name, to.name, to.parent].each{ cell -> td(cell) } }
					}
				}
				hr(); small("// Generated by ${Settings.getApplicationIdentifier()} on ${InetAddress.localHost.hostName} at ${now.dateTimeString}")
			}
		}
	}
}

// store processing report
if (storeReport) {
	def reportFolder = new File(Settings.getApplicationFolder(), 'reports').getCanonicalFile()
	def reportFile = getReportMessage().saveAs(new File(reportFolder, "AMC ${now.format('''[yyyy-MM-dd HH'h'mm'm']''')} ${getReportSubject().take(50).trim()}.html".validateFileName()))
	log.finest("Saving report as ${reportFile}")
}

// send pushbullet report
if (pushbullet) {
	log.info 'Sending PushBullet report'
	PushBullet(pushbullet).sendHtml(getReportTitle(), getReportMessage())
}

// send email report
if (gmail) {
	sendGmail(
		subject: getReportTitle(),
		message: getReportMessage(), 
		messagemimetype: 'text/html',
		to: tryQuietly{ mailto } ?: gmail[0] + '@gmail.com', // mail to self by default
		user: gmail[0], password: gmail[1]
	)
}


if (deleteAfterExtract) {
	extractedArchives.each{ a ->
		log.finest("Delete archive $a")
		a.delete()
		a.dir.listFiles().toList().findAll{ v -> v.name.startsWith(a.nameWithoutExtension) && v.extension ==~ /r\d+/ }.each{ v ->
			log.finest("Delete archive volume $v")
			v.delete()
		}
	}
}


if (unsorted) {
	def action = StandardRenameAction.forName(_args.action)
	(input - getRenameLog().keySet()).each{ original ->
		def destination = new File(_args.output, getMediaInfo(file:original, format:'''Unsorted/{fn}.{ext}'''))
		log.info("[$action] Rename [$original] to [$destination]")
		tryLogCatch{
			action.rename(original, destination)
		}
	}
}


// clean empty folders, clutter files, etc after move
if (clean) {
	if (['COPY', 'HARDLINK'].find{ it.equalsIgnoreCase(_args.action) } && tempFiles.size() > 0) {
		log.info 'Clean temporary extracted files'
		// delete extracted files
		tempFiles.findAll{ it.isFile() }.sort().each{
			log.finest "Delete $it"
			it.delete()
		}
		// delete remaining empty folders
		tempFiles.findAll{ it.isDirectory() }.sort().reverse().each{
			log.finest "Delete $it"
			if (it.getFiles().isEmpty()) it.deleteDir()
		}
	}
	
	// deleting remaining files only makes sense after moving files
	if ('MOVE'.equalsIgnoreCase(_args.action)) {
		def cleanerInput = !args.empty ? args : ut_kind == 'multi' && ut_dir ? [ut_dir as File] : []
		cleanerInput = cleanerInput.findAll{ f -> f.exists() }
		if (cleanerInput.size() > 0) {
			log.info 'Clean clutter files and empty folders'
			executeScript('cleaner', args.empty ? [root:true] : [root:false], cleanerInput)
		}
	}
}


// update excludes with input of this run
if (excludeList) {	
	def excludePathSet = excludeList.exists() ? excludeList.text.split('\n') as HashSet : []
	excludePathSet += input
	excludePathSet.join('\n').saveAs(excludeList)
}
