#!/usr/bin/env filebot -script


// log input parameters
log.fine("Run script [$_args.script] at [$now]")



log.warning """
[PSA] Important Discussion of Proposed Changes:
https://www.filebot.net/forums/viewtopic.php?t=13406
"""



_def.each{ n, v -> log.finest('Parameter: ' + [n, n =~ /plex|kodi|emby|pushover|pushbullet|discord|mail|myepisodes/ ? '*****' : v].join(' = ')) }
args.withIndex().each{ f, i -> if (f.exists()) { log.finest "Argument[$i]: $f" } else { log.warning "Argument[$i]: File does not exist: $f" } }



// initialize variables
failOnError = _args.conflict.equalsIgnoreCase('fail')
testRun = _args.action.equalsIgnoreCase('test')

// --output folder must be a valid folder
outputFolder = _args.absoluteOutputFolder

// enable/disable features as specified via --def parameters
unsorted  = tryQuietly{ unsorted.toBoolean() }
music     = tryQuietly{ music.toBoolean() }
subtitles = tryQuietly{ subtitles.split(/\W+/) as List }
artwork   = tryQuietly{ artwork.toBoolean() }
clean     = tryQuietly{ clean.toBoolean() }
exec      = tryQuietly{ exec.toString() }

// array of kodi/plex/emby hosts
kodi = tryQuietly{ any{kodi}{xbmc}.split(/[ ,;|]+/)*.split(/:(?=\d+$)/).collect{ it.length >= 2 ? [host: it[0], port: it[1] as int] : [host: it[0]] } }
plex = tryQuietly{ plex.split(/[ ,;|]+/)*.split(/:/).collect{ it.length >= 3 ? [host: it[0], port: it[1] as int, token: it[2]] : it.length >= 2 ? [host: it[0], token: it[1]] : [host: it[0]] } }
emby = tryQuietly{ emby.split(/[ ,;|]+/)*.split(/:/).collect{ it.length >= 3 ? [host: it[0], port: it[1] as int, token: it[2]] : it.length >= 2 ? [host: it[0], token: it[1]] : [host: it[0]] } }

// extra options, myepisodes updates and email notifications
extractFolder      = tryQuietly{ extractFolder as File }
skipExtract        = tryQuietly{ skipExtract.toBoolean() }
deleteAfterExtract = tryQuietly{ deleteAfterExtract.toBoolean() }
excludeList        = tryQuietly{ def f = excludeList as File; f.absolute ? f : outputFolder.resolve(f.path) }
excludeLink        = tryQuietly{ excludeLink.toBoolean() }
myepisodes         = tryQuietly{ myepisodes.split(':', 2) as List }
gmail              = tryQuietly{ gmail.split(':', 2) as List }
mail               = tryQuietly{ mail.split(':', 5) as List }
pushover           = tryQuietly{ pushover.split(':', 2) as List }
pushbullet         = tryQuietly{ pushbullet.toString() }
discord            = tryQuietly{ discord.toString() }
storeReport        = tryQuietly{ def f = storeReport as File; f.absolute ? f : outputFolder.resolve(f.path) }
reportError        = tryQuietly{ reportError.toBoolean() }

// user-defined filters
label       = any{ ut_label }{ null }
ignore      = any{ ignore }{ null }
minFileAge  = any{ minFileAge.toDouble() }{ 0d }
minFileSize = any{ minFileSize.toLong() }{ 50 * 1000L * 1000L }
minLengthMS = any{ minLengthMS.toLong() }{ 10 * 60 * 1000L }

// database preferences
seriesDB = any{ seriesDB }{ 'TheMovieDB::TV' }
animeDB = any{ animeDB }{ seriesDB }
movieDB = any{ movieDB }{ 'TheMovieDB' }
musicDB = any{ musicDB }{ 'ID3' }

// series / anime / movie format expressions
seriesFormat   = any{ seriesFormat   }{ _args.format }{ '{ plex.id }' }
animeFormat    = any{ animeFormat    }{ _args.format }{ 'Anime/{ ~plex.id }' }
movieFormat    = any{ movieFormat    }{ _args.format }{ '{ plex.id }' }
musicFormat    = any{ musicFormat    }{ _args.format }{ '{ plex.id }' }
unsortedFormat = any{ unsortedFormat }{ 'Unsorted/{ relativeFile }' }

// default anime mapper expression
animeMapper = any{ _args.mapper }{ animeDB ==~ /(?i:AniDB)/ ? null : 'allOf{ episode }{ order.absolute.episode }{ AnimeList.AniDB }' }



// include artwork/nfo, pushover/pushbullet and ant utilities as required
if (artwork || kodi || plex || emby) { include('lib/htpc') }
if (pushover || pushbullet || gmail || mail || discord) { include('lib/web') }



// check input parameters
def ut = _def.findAll{ k, v -> k.startsWith('ut_') }.collectEntries{ k, v ->
	if (v ==~ /[%$][\p{Alnum}\p{Punct}]+/) {
		log.warning "Bad $k value: $v"
		v = null
	}
	return [k.substring(3), v ? v : null]
}

_def.each{ k, v ->
	if (v =~ /^[@'"]|[@'"]$/) {
		log.warning "Bad $k value: $v"
	}
	if (k =~ /^[A-Z]/) {
		log.warning "Invalid usage: upper-case script parameter --def $k has no effect"
	}
}

if (_args.db) {
	log.warning "Invalid usage: The --db option has no effect"
}
if (testRun && artwork) {
	log.warning "[TEST] --def artwork is incompatible with --action TEST and has been disabled"
}
if (testRun && clean) {
	log.warning "[TEST] --def clean is incompatible with --action TEST and has been disabled"
}
if (testRun && unsorted) {
	log.warning "[TEST] --def unsorted is incompatible with --action TEST and has been disabled"
}

if (outputFolder == null) {
	die "Invalid usage: The --output folder option is required"
}

if (!outputFolder.isDirectory() || !outputFolder.canWrite()) {
	log.severe "Invalid usage: output folder must exist and must be a writable directory: $outputFolder"
}

if (ut.dir) {
	if (ut.state_allow && !(ut.state ==~ ut.state_allow)) {
		die "Invalid state: $ut.state != $ut.state_allow", ExitCode.NOOP
	}
	if (args.size() > 0) {
		die "Invalid usage: input file path must be passed via script parameters $ut or via file arguments $args but not both"
	}
	if (ut.dir == '/') {
		die "Invalid usage: No! Are you insane? You can't just pass in the entire filesystem. Think long and hard about what you just tried to do."
	}
	if (ut.dir.toFile() in outputFolder.listPath()) {
		die "Invalid usage: output folder [$outputFolder] must not start with input folder [$ut.dir]"
	}
	if (outputFolder in ut.dir.toFile().listPath()) {
		log.warning "Invalid usage: input folder [$ut.dir] must not start with output folder [$outputFolder]"
	}
} else if (args.size() == 0) {
	die "Invalid usage: no input"
} else if (args.any{ f -> f in outputFolder.listPath() }) {
	die "Invalid usage: output folder [$outputFolder] must not be the same as or be inside of input folder $args"
} else if (args.any{ f -> f in File.listRoots() }) {
	die "Invalid usage: input folder $args must not be a filesystem root"
}



// collect input fileset as specified by the given --def parameters
roots = args

if (args.size() == 0) {
	// assume we're called with utorrent parameters (account for older and newer versions of uTorrents)
	def d = ut.dir as File
	def f = ut.file as File
	def single = ut.kind != 'multi'

	if (single && d && f) {
		roots = [(f.absolute ? f : d / f).canonicalFile] // single-file torrent
	} else {
		roots = [d.canonicalFile] // multi-file torrent
	}
}

// helper function to work with the structure relative path rather than the whole absolute path
def relativeInputPath(f) {
	def r = roots.find{ r -> f.path.startsWith(r.path) && r.isDirectory() && f.isFile() }
	if (r != null) {
		return f.path.substring(r.path.length() + 1)
	}
	return f.name
}



// define and load exclude list (e.g. to make sure files are only processed once)
excludePathSet = new FileSet()

if (excludeList) {
	if (excludeList.exists()) {
		try {
			excludePathSet.load(excludeList)
		} catch(e) {
			die "Failed to read excludes: $excludeList: $e.message"
		}
		log.fine "Use excludes: $excludeList (${excludePathSet.size()})"
	} else {
		log.fine "Use excludes: $excludeList"
		try {
			if ((!excludeList.parentFile.isDirectory() && !excludeList.parentFile.mkdirs()) || (!excludeList.isFile() && !excludeList.createNewFile())) {
				die "Failed to create excludes: $excludeList"
			}
		} catch(e) {
			die "Failed to create excludes: $excludeList: $e.message"
		}
	}
}


extractedArchives = []
temporaryFiles = []

def extract(f) {
	// avoid cyclical archives that extract to the same output folder over and over
	if (f in extractedArchives) {
		return []
	}

	def folder = new File(extractFolder ?: f.dir, f.nameWithoutExtension)
	def files = extract(file: f, output: folder.resolve(f.dir.name), conflict: 'auto', filter: { it.isArchive() || it.isVideo() || it.isSubtitle() || (music && it.isAudio()) }, forceExtractAll: true) ?: []

	extractedArchives += f
	temporaryFiles += folder
	temporaryFiles += files

	// resolve newly extracted files and deal with disk folders and hidden files correctly
	return [folder]
}


def acceptFile(f) {
	if (f.isHidden()) {
		log.finest "Ignore hidden: $f"
		return false
	}

	if (f.isSystem()) {
		log.finest "Ignore system path: $f"
		return false
	}

	if (f.name.findWordMatch(/(Sample|Trailer(?!.Park.Boys)|Extras|Featurettes|Extra.Episodes|Bonus.Features|Music.Video|Scrapbook|Behind.the.Scenes|Extended.Scenes|Deleted.Scenes|Mini.Series|s\d{2}c\d{2}|S\d+EXTRA|\d+xEXTRA|SP\d+|NCED|NCOP|(OP|ED)\d+|Formula.1.\d{4})|(?<=[-])(s|t|sample|trailer|deleted|featurette|scene|other|behindthescenes)(?=[.])/)) {
		log.finest "Ignore extra: $f"
		return false
	}

	// ignore if the user-defined ignore pattern matches
	if (f.path.findMatch(ignore)) {
		log.finest "Ignore pattern: $f"
		return false
	}

	// ignore archives that are on the exclude path list
	if (excludePathSet.contains(f)) {
		return false
	}

	// accept folders right away and skip file sanity checks
	if (f.isDirectory()) {
		return true
	}

	// check if file exists
	if (!f.isFile()) {
		log.warning "File does not exist: $f"
		return false
	}

	// ignore previously linked files
	if (excludeLink && (f.symlink || f.linkCount != 1)) {
		log.finest "Exclude superfluous link: $f [$f.linkCount] $f.key"
		return false
	}

	// ignore young files
	if (minFileAge > 0 && f.ageLastModified < minFileAge) {
		log.finest "Skip young file: $f [Last-Modified: $f.ageLastModified days ago"
		return false
	}

	// accept archives if the extract feature is enabled
	if (f.isArchive() || f.hasExtension('001')) {
		return !skipExtract
	}

	// ignore iso images that do not contain a video disk structure
	if (f.hasExtension('iso') && !f.isDisk()) {
		log.fine "Ignore disk image: $f"
		return false
	}

	// ignore small video files
	if (minFileSize > 0 && f.isVideo() && f.length() < minFileSize) {
		log.fine "Skip small video file: $f [$f.displaySize]"
		return false
	}

	// ignore short videos
	if (minLengthMS > 0 && f.isVideo() && any{ f.mediaCharacteristics.duration.toMillis() < minLengthMS }{ false }) {
		log.fine "Skip short video: $f [$f.mediaCharacteristics.duration]"
		return false
	}

	// ignore subtitle files without matching video file in the same or parent folder (in strict mode only)
	if (_args.strict && f.isSubtitle() && ![f, f.dir].findResults{ it.dir }.any{ it.listFiles{ it.isVideo() && f.isDerived(it) }}) {
		log.fine "Ignore orphaned subtitles: $f"
		return false
	}

	// process only media files (accept audio files only if music mode is enabled)
	return f.isVideo() || f.isSubtitle() || (music && f.isAudio())
}


// specify how to resolve input folders, e.g. grab files from all folders except disk folders and already processed folders (i.e. folders with movie/tvshow nfo files)
def resolveInput(f) {
	// resolve folder recursively, except disk folders
	if (f.isDirectory()) {
		if (f.isDisk()) {
			log.finest "Disk Folder: $f"
			return f
		}
		return f.listFiles{ acceptFile(it) }.collect{ resolveInput(it) }
	}

	if (f.isArchive() || f.hasExtension('001')) {
		return extract(f).findAll{ acceptFile(it) }.collect{ resolveInput(it) }
	}

	return f
}



// flatten nested file structure
def input = roots.findAll{ acceptFile(it) }.flatten{ resolveInput(it) }

// update exclude list with all input that will be processed during this run
if (excludeList && !testRun) {
	try {
		excludePathSet.append(excludeList, extractedArchives, input)
	} catch(e) {
		die "Failed to write excludes: $excludeList: $e"
	}
}

// print exclude and input sets for logging
input.each{ f ->
	log.fine "Input: $f"
	// print xattr metadata
	if (f.metadata) {
		log.fine "       └─ Metadata: $f.metadata"
	}
}

// early abort if there is nothing to do
if (input.size() == 0) {
	die "No files selected for processing", ExitCode.NOOP
}



// force Movie / TV Series / Anime behaviour
def forceGroup() {
	switch(label) {
		case ~/.*(?i:Movie|Film|Concert|UFC).*/:
			log.fine "Process as Movie [$label]"
			return AutoDetection.Group.Movie
		case ~/.*(?i:TV|Show|Series|Documentary).*/:
			log.fine "Process as TV Series [$label]"
			return AutoDetection.Group.Series
		case ~/.*(?i:Anime).*/:
			log.fine "Process as Anime [$label]"
			return AutoDetection.Group.Anime
		case ~/.*(?i:Audio|Music|Music.Video).*/:
			log.fine "Process as Music [$label]"
			return AutoDetection.Group.Music
		case ~/.*(?i:games|book|other|ignore).*/:
			log.fine "Process as Unsorted [$label]"
			return AutoDetection.Group.None
		default:
			return null
	}
}


def group(files) {
	def singleGroupKey = forceGroup()
	if (singleGroupKey) {
		return [(singleGroupKey): files]
	}
	return AutoDetection.group(files, _args.language.locale)
}



// group episodes / movies
def groups = group(input)

// log movie/series/anime detection results
groups.each{ group, files -> log.finest "Group: $group => ${files*.name}" }

// keep track of files that have been processed successfully
def destinationFiles = []

// keep track of unsorted files or files that could not be processed for some reason
def unsortedFiles = []

// process each batch
groups.each{ group, files ->
	// fetch subtitles (but not for anime)
	if ((group.isMovie() || group.isSeries()) && subtitles != null && files.findAll{ it.isVideo() }.size() > 0) {
		subtitles.each{ languageCode ->
			def subtitleFiles = getMissingSubtitles(file: files, lang: languageCode, strict: true, output: 'srt', encoding: 'UTF-8', format: 'MATCH_VIDEO_ADD_LANGUAGE_TAG') ?: []
			files += subtitleFiles
			temporaryFiles += subtitleFiles
		}
	}

	// EPISODE MODE
	if ((group.isSeries() || group.isAnime()) && !group.isMovie()) {
		// choose series / anime
		def rfs = group.isSeries() ? rename(file: files, format: seriesFormat, db: seriesDB) : rename(file: files, format: animeFormat, db: animeDB, mapper: animeMapper)

		if (rfs) {
			destinationFiles += rfs

			if (artwork && !testRun) {
				rfs.mapByFolder().each{ dir, fs ->
					def hasSeasonFolder = any{ dir =~ /Specials|Season.\d+/ || dir.parentFile.structurePathTail.listPath().size() > 0 }{ false }	// MAY NOT WORK WELL FOR CERTAIN FORMATS

					fs.findResults{ it.metadata }.collect{ [series: it.seriesInfo, season: it.special ? 0 : it.season] }.unique().each{ s ->
						log.fine "Fetching series artwork for [$s.series / Season $s.season] to [$dir]"
						fetchSeriesArtworkAndNfo(hasSeasonFolder ? dir.parentFile : dir, dir, s.series, s.season, false, _args.language.locale)
					}
				}
			}
		} else if (failOnError && rfs == null) {
			die "Failed to process group: $group"
		} else {
			unsortedFiles += files
		}
	}

	// MOVIE MODE
	else if (group.isMovie() && !group.isSeries() && !group.isAnime()) {
		def rfs = rename(file: files, format: movieFormat, db: movieDB)

		if (rfs) {
			destinationFiles += rfs

			if (artwork && !testRun) {
				rfs.mapByFolder().each{ dir, fs ->
					def movieFile = fs.findAll{ it.isVideo() || it.isDisk() }.toSorted{ it.length() }.reverse().findResult{ it }
					if (movieFile) tryLogCatch {
						def movieInfo = movieFile.metadata
						log.fine "Fetching movie artwork for [$movieInfo] to [$dir]"
						if (movieInfo) {
							fetchMovieArtworkAndNfo(dir, movieInfo, movieFile, false, _args.language.locale)
						}
					}
				}
			}
		} else if (failOnError && rfs == null) {
			die "Failed to process group: $group"
		} else {
			unsortedFiles += files
		}
	}

	// MUSIC MODE
	else if (group.isMusic()) {
		def rfs = rename(file: files, format: musicFormat, db: musicDB)

		if (rfs) {
			destinationFiles += rfs
		} else if (failOnError && rfs == null) {
			die "Failed to process group: $group"
		} else {
			unsortedFiles += files
		}
	}

	// UNSORTED
	else {
		unsortedFiles += files
	}
}


// ---------- POST PROCESSING ---------- //


// process the remaining files that cannot be sorted automatically
if (unsorted && !testRun) {
	// skip file paths that are no longer valid (e.g. due to a partially processed but ultimately failed group)
	unsortedFiles.removeAll{ f -> !f.exists() }

	if (unsortedFiles.size() > 0) {
		log.fine "Processing ${unsortedFiles.size()} unsorted files"

		def rfs = rename(map: unsortedFiles.collectEntries{ original ->
			def destination = getMediaInfo(original, unsortedFormat) as File

			// sanity check user-defined unsorted format
			if (destination == null) {
				die "Invalid usage: --def unsortedFormat [$unsortedFormat] must yield a valid target file path for unsorted file [$original]"
			}

			// resolve relative paths
			if (!destination.isAbsolute()) {
				destination = outputFolder.resolve(destination.path)
			}

			return [original, destination]
		})

		if (rfs != null) {
			destinationFiles += rfs
		}
	}
}

// run program on newly processed files
if (exec) {
	destinationFiles.collect{ getMediaInfo(it, exec) }.unique().each{ command ->
		log.fine "Execute: $command"
		execute(command)
	}
}


// ---------- REPORTING ---------- //


def renameLog = getRenameLog()

if (renameLog.size() > 0) {
	// messages used for kodi / plex / emby pushover notifications
	def getNotificationTitle = {
		def count = renameLog.count{ k, v -> !v.isSubtitle() }
		return "FileBot finished processing $count files"
	}.memoize()

	def getNotificationMessage = { prefix = '• ', postfix = '\n' -> 
		return ut.title ?: (input.findAll{ !it.isSubtitle() } ?: input).collect{ relativeInputPath(it) as File }.root.nameWithoutExtension.unique().collect{ prefix + it }.join(postfix).trim()
	}.memoize()

	// make Kodi scan for new content and display notification message
	if (kodi) tryLogCatch {
		kodi.each{ instance ->
			log.fine "Notify Kodi [$instance.host]"
			showNotification(instance.host, instance.port, getNotificationTitle(), getNotificationMessage(), 'https://app.filebot.net/icon.png')
			scanVideoLibrary(instance.host, instance.port)
		}
	}

	// make Plex scan for new content
	if (plex) tryLogCatch {
		plex.each{ instance ->
			log.fine "Notify Plex [$instance.host]"
			refreshPlexLibrary(instance.host, instance.port, instance.token, destinationFiles)
		}
	}

	// make Emby scan for new content
	if (emby) tryLogCatch {
		emby.each{ instance ->
			log.fine "Notify Emby [$instance.host]"
			refreshEmbyLibrary(instance.host, instance.port, instance.token)
		}
	}

	// mark episodes as 'acquired'
	if (myepisodes) tryLogCatch {
		log.fine 'Update MyEpisodes'
		executeScript('update-mes', [login:myepisodes.join(':'), addshows:true], destinationFiles)
	}

	// pushover only supports plain text messages
	if (pushover) tryLogCatch {
		log.fine 'Sending Pushover notification'
		Pushover(pushover[0], pushover[1] ?: 'wcckDz3oygHSU2SdIptvnHxJ92SQKK').send(getNotificationTitle(), getNotificationMessage())
	}

	// messages used for email / pushbullet reports
	def getReportSubject = { getNotificationMessage('', '; ') }
	def getReportTitle = { '[FileBot] ' + getReportSubject() }
	def getReportMessage = { 
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
					hr(); small("// Generated by ${Settings.applicationIdentifier} on ${InetAddress.localHost.hostName} at ${now}")
				}
			}
		}
	}

	// store processing report
	if (storeReport) {
		def reportName = [now.format(/[yyyy-MM-dd HH mm]/), getReportSubject().take(50)].join(' ').validateFileName().space('_')
		def reportFile = storeReport.resolve(reportName + '.html')
		log.fine "Saving HTML report to [$reportFile]"
		getReportMessage().saveAs(reportFile)
	}

	// send pushbullet report
	if (pushbullet) tryLogCatch {
		log.fine 'Sending PushBullet report'
		PushBullet(pushbullet).sendFile(getNotificationTitle() + '.html', getReportMessage(), 'text/html', getNotificationMessage(), any{ mailto }{ null })
	}

	// send gmail report
	if (gmail) tryLogCatch {
		log.fine 'Sending Gmail report'
		def account = gmail[0] =~ /@/ ? gmail[0] : gmail[0] + '@gmail.com'
		Gmail(account, gmail[1]).sendHtml(account, any{ mailto }{ account }, getReportTitle(), getReportMessage())
	}

	// send email report
	if (mail) tryLogCatch {
		log.fine 'Sending Email report'
		def account = mail[2]
		Email(mail[0], mail[1], mail[3], mail[4]).sendHtml(account, any{ mailto }{ account }, getReportTitle(), getReportMessage())
	}

	// call discord webhook
	if (discord) tryLogCatch {
		log.fine 'Calling Discord webhook'
		Discord(discord).postRenameMap(getReportSubject(), renameLog)
	}
}


// clean up temporary files that may be left behind after extraction
if (deleteAfterExtract) {
	extractedArchives.each{ a ->
		log.finest "Delete archive $a"
		a.delete()
		a.dir.listFiles().toList().findAll{ v -> v.name.startsWith(a.nameWithoutExtension) && v.extension ==~ /r\d+/ }.each{ v ->
			log.finest "Delete archive volume $v"
			v.delete()
		}
	}
}


// abort and skip clean-up logic if we didn't process any files
if (destinationFiles.size() == 0) {
	die "Finished without processing any files", !testRun ? ExitCode.FAILURE : ExitCode.NOOP
}


// clean empty folders, clutter files, etc after move
if (clean && !testRun) {
	if (temporaryFiles && _args.action ==~ /(?i:COPY|HARDLINK|DUPLICATE)/) {
		log.fine 'Clean temporary extracted files'
		// delete extracted files
		temporaryFiles.findAll{ it.isFile() }.toSorted().each{
			log.finest "Delete $it"
			it.delete()
		}
		// delete remaining empty folders
		temporaryFiles.findAll{ it.isDirectory() }.toSorted().reverse().each{
			log.finest "Delete $it"
			if (it.getFiles().size() == 0) {
				it.deleteDir()
			}
		}
	}

	// deleting remaining files only makes sense after moving files
	if (_args.action ==~ /(?i:MOVE)/) {
		def cleanerInput = args.size() > 0 ? args : ut.kind == 'multi' && ut.dir ? [ut.dir as File] : []
		cleanerInput = cleanerInput.findAll{ f -> f.exists() }
		if (cleanerInput.size() > 0) {
			log.fine 'Clean clutter files and empty folders'
			executeScript('cleaner', args.size() == 0 ? [root:true, ignore: ignore] : [root:false, ignore: ignore], cleanerInput)
		}
	}
}


// update exclude list with files that were added during the run (e.g. subtitles)
if (temporaryFiles && excludeList && !testRun) {
	try {
		excludePathSet.append(excludeList, temporaryFiles.findAll{ it.isFile() })
	} catch(e) {
		die "Failed to write excludes: $excludeList: $e"
	}
}
