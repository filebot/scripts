#!/usr/bin/env filebot -script


// log input parameters
log.fine("Run script [$_args.script] at [$now]")

_def.each{ n, v -> log.finest('Parameter: ' + [n, n =~ /plex|kodi|pushover|pushbullet|mail|myepisodes/ ? '*****' : v].join(' = ')) }
args.withIndex().each{ f, i -> if (f.exists()) { log.finest "Argument[$i]: $f" } else { log.warning "Argument[$i]: File does not exist: $f" } }



// initialize variables
failOnError = _args.conflict.equalsIgnoreCase('fail')
testRun = license == null || _args.action.equalsIgnoreCase('test')

// --output folder must be a valid folder
outputFolder = tryLogCatch{ any{ _args.output }{ '.' }.toFile().getCanonicalFile() }

// enable/disable features as specified via --def parameters
unsorted  = tryQuietly{ unsorted.toBoolean() }
music     = tryQuietly{ music.toBoolean() }
subtitles = tryQuietly{ subtitles.split(/\W+/) as List }
artwork   = tryQuietly{ artwork.toBoolean() && !testRun }
extras    = tryQuietly{ extras.toBoolean() }
clean     = tryQuietly{ clean.toBoolean() }
exec      = tryQuietly{ exec.toString() }

// array of kodi/plex/emby hosts
kodi = tryQuietly{ any{kodi}{xbmc}.split(/[ ,;|]+/)*.split(/:(?=\d+$)/).collect{ it.length >= 2 ? [host: it[0], port: it[1] as int] : [host: it[0]] } }
plex = tryQuietly{ plex.split(/[ ,;|]+/)*.split(/:/).collect{ it.length >= 2 ? [host: it[0], token: it[1]] : [host: it[0]] } }
emby = tryQuietly{ emby.split(/[ ,;|]+/)*.split(/:/).collect{ it.length >= 2 ? [host: it[0], token: it[1]] : [host: it[0]] } }

// extra options, myepisodes updates and email notifications
extractFolder      = tryQuietly{ extractFolder as File }
skipExtract        = tryQuietly{ skipExtract.toBoolean() }
deleteAfterExtract = tryQuietly{ deleteAfterExtract.toBoolean() }
excludeList        = tryQuietly{ def f = excludeList as File; f.isAbsolute() ? f : outputFolder.resolve(f.path) }
myepisodes         = tryQuietly{ myepisodes.split(':', 2) as List }
gmail              = tryQuietly{ gmail.split(':', 2) as List }
mail               = tryQuietly{ mail.split(':', 5) as List }
pushover           = tryQuietly{ pushover.split(':', 2) as List }
pushbullet         = tryQuietly{ pushbullet.toString() }
storeReport        = tryQuietly{ storeReport.toBoolean() }
reportError        = tryQuietly{ reportError.toBoolean() }

// user-defined filters
label       = any{ ut_label }{ null }
ignore      = any{ ignore }{ null }
minFileSize = any{ minFileSize.toLong() }{ 50 * 1000L * 1000L }
minLengthMS = any{ minLengthMS.toLong() }{ 10 * 60 * 1000L }

// series/anime/movie format expressions
seriesFormat   = any{ seriesFormat   }{ _args.format }{ '{plex}' }
animeFormat    = any{ animeFormat    }{ _args.format }{ '{plex}' }
movieFormat    = any{ movieFormat    }{ _args.format }{ '{plex}' }
musicFormat    = any{ musicFormat    }{ _args.format }{ '{plex}' }
unsortedFormat = any{ unsortedFormat }{ 'Unsorted/{file.structurePathTail}' }







// include artwork/nfo, pushover/pushbullet and ant utilities as required
if (artwork || kodi || plex || emby) { include('lib/htpc') }
if (pushover || pushbullet ) { include('lib/web') }
if (gmail || mail) { include('lib/ant') }



// error reporting functions
def sendEmailReport(title, message, messagetype) {
	if (gmail) {
		sendGmail(
			subject: title, message: message, messagemimetype: messagetype,
			to: any{ mailto } { gmail[0].contains('@') ? gmail[0] : gmail[0] + '@gmail.com' },		// mail to self by default
			user: gmail[0].contains('@') ? gmail[0] : gmail[0] + '@gmail.com', password: gmail[1]
		)
	}
	if (mail) {
		sendmail(
			subject: title, message: message, messagemimetype: messagetype,
			mailhost: mail[0], mailport: mail[1], from: mail[2], to: mailto,
			user: mail[3], password: mail[4]
		)
	}
}

def fail(message) {
	if (reportError) {
		sendEmailReport("[FileBot] $message", "Execute:\n$_args\n\nError:\n$message", 'text/plain')
	}
	die(message)
}



// check input parameters
def ut = _def.findAll{ k, v -> k.startsWith('ut_') }.collectEntries{ k, v ->
	if (v ==~ /[%$]\p{Alnum}|\p{Punct}+/) {
		log.warning "Bad $k value: $v"
		v = null
	}
	return [k.substring(3), v ? v : null]
}



// sanity checks
if (outputFolder == null || !outputFolder.isDirectory()) {
	fail "Illegal usage: output folder must exist and must be a directory: $outputFolder"
}

if (ut.dir) {
	if (ut.state_allow && !(ut.state ==~ ut.state_allow)) {
		die "Illegal state: $ut.state != $ut.state_allow", ExitCode.NOOP
	}
	if (args.size() > 0) {
		fail "Illegal usage: use either script parameters $ut or file arguments $args but not both"
	}
	if (ut.dir == '/') {
		fail "Illegal usage: No! Are you insane? You can't just pass in the entire filesystem. Think long and hard about what you just tried to do."
	}
	if (ut.dir.toFile() in outputFolder.listPath()) {
		fail "Illegal usage: output folder [$outputFolder] must be separate from input folder $ut"
	}
} else if (args.size() == 0) {
	fail "Illegal usage: no input"
} else if (args.any{ f -> f in outputFolder.listPath() }) {
	fail "Illegal usage: output folder [$outputFolder] must be separate from input arguments $args"
} else if (args.any{ f -> f in File.listRoots() }) {
	fail "Illegal usage: input $args must not include a filesystem root"
}



// collect input fileset as specified by the given --def parameters
roots = args

if (args.size() == 0) {
	// assume we're called with utorrent parameters (account for older and newer versions of uTorrents)
	if (ut.kind == 'single' || (ut.kind != 'multi' && ut.dir && ut.file)) {
		roots = [new File(ut.dir, ut.file).getCanonicalFile()] // single-file torrent
	} else {
		roots = [new File(ut.dir).getCanonicalFile()] // multi-file torrent
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
		} catch(Exception e) {
			fail "Failed to load excludeList: $e"
		}
		log.fine "Use excludes: $excludeList (${excludePathSet.size()})"
	} else {
		log.fine "Use excludes: $excludeList"
		if ((!excludeList.parentFile.isDirectory() && !excludeList.parentFile.mkdirs()) || (!excludeList.isFile() && !excludeList.createNewFile())) {
			fail "Failed to create excludeList: $excludeList"
		}
	}
}


extractedArchives = []
temporaryFiles = []

def extract(f) {
	def folder = new File(extractFolder ?: f.dir, f.nameWithoutExtension)
	def files = extract(file: f, output: folder.resolve(f.dir.name), conflict: 'auto', filter: { it.isArchive() || it.isVideo() || it.isSubtitle() || (music && it.isAudio()) }, forceExtractAll: true) ?: []

	extractedArchives += f
	temporaryFiles += folder
	temporaryFiles += files

	// resolve newly extracted files and deal with disk folders and hidden files correctly
	return folder
}


def acceptFile(f) {
	if (f.isHidden()) {
		log.finest "Ignore hidden: $f"
		return false
	}

	if (f.isDirectory() && f.name ==~ /[.@].+|bin|initrd|opt|sbin|var|dev|lib|proc|sys|var.defaults|etc|lost.found|root|tmp|etc.defaults|mnt|run|usr|System.Volume.Information/) {
		log.finest "Ignore system path: $f"
		return false
	}

	if (f.isVideo() && f.name =~ /(?<=\b|_)(?i:Sample|Trailer|Extras|Extra.Episodes|Bonus.Features|Music.Video|Scrapbook|Behind.the.Scenes|Extended.Scenes|Deleted.Scenes|Mini.Series|s\d{2}c\d{2}|S\d+EXTRA|\d+xEXTRA|NCED|NCOP|(OP|ED)\d+|Formula.1.\d{4})(?=\b|_)/) {
		log.finest "Ignore video extra: $f"
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
		log.fine "Skip small video file: $f (${org.apache.commons.io.FileUtils.byteCountToDisplaySize(f.length())})"
		return false
	}

	// ignore short videos
	if (minLengthMS > 0 && f.isVideo() && any{ f.mediaCharacteristics.duration.toMillis() < minLengthMS }{ false }) {
		log.fine "Skip short video: $f"
		return false
	}

	// ignore subtitle files without matching video file in the same or parent folder
	if (f.isSubtitle() && ![f, f.dir].findResults{ it.dir }.any{ it.listFiles{ it.isVideo() && f.isDerived(it) }}) {
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
			return f
		}
		return f.listFiles{ acceptFile(it) }.collect{ resolveInput(it) }
	}

	if (f.isArchive() || f.hasExtension('001')) {
		def folder = extract(f)
		if (folder.isDirectory()) {
			return resolveInput(folder)
		}
	}

	return f
}



// flatten nested file structure
def input = roots.findAll{ acceptFile(it) }.flatten{ resolveInput(it) }.toSorted()

// update exclude list with all input that will be processed during this run
if (excludeList && !testRun) {
	excludePathSet.append(excludeList, extractedArchives, input)
}

// print exclude and input sets for logging
input.each{ f -> log.fine "Input: $f" }

// print xattr metadata
input.each{ f -> if (f.metadata) log.finest "xattr: [$f.name] => [$f.metadata]" }

// early abort if there is nothing to do
if (input.size() == 0) {
	die "No files selected for processing", ExitCode.NOOP
}



// force Movie / TV Series / Anime behaviour
def forceGroup() {
	switch(label) {
		case ~/^(?i:Movie|Film|Concert|UFC)/:
			return new AutoDetection.Group().setMovie()
		case ~ /^(?i:TV|Show|Series|Documentary)/:
			return new AutoDetection.Group().setSeries()
		case ~ /^(?i:Anime)/:
			return new AutoDetection.Group().setAnime()
		case ~/^(?i:audio|music|music.video)/:
			return new AutoDetection.Group().setMusic()
		case ~/^(?i:games|ebook|other|ignore)/:
			return new AutoDetection.Group()
		default:
			return null
	}
}


def group(files) {
	def singleGroupKey = forceGroup()
	if (singleGroupKey) {
		return [(singleGroupKey): files]
	}

	return new AutoDetection(files, false, _args.language.locale).group()
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
			input += subtitleFiles // make sure subtitles are added to the exclude list and other post processing operations
			temporaryFiles += subtitleFiles // if downloaded for temporarily extraced files delete later
		}
	}

	// EPISODE MODE
	if ((group.isSeries() || group.isAnime()) && !group.isMovie()) {
		// choose series / anime
		def dest = group.isSeries() ? rename(file: files, format: seriesFormat, db: 'TheTVDB') : Settings.applicationRevisionNumber >= 6385 ? rename(file: files, format: animeFormat, order: 'Absolute', db: 'TheTVDB') : rename(file: files, format: animeFormat, db: 'AniDB')

		if (dest != null) {
			destinationFiles += dest

			if (artwork) {
				dest.mapByFolder().each{ dir, fs ->
					def hasSeasonFolder = any{ dir =~ /Specials|Season.\d+/ || dir.parentFile.structurePathTail.listPath().size() > 0 }{ false }	// MAY NOT WORK FOR CERTAIN FORMATS

					fs.findResults{ it.metadata }.findAll{ it.seriesInfo.database == 'TheTVDB' }.collect{ [name: it.seriesName, season: it.special ? 0 : it.season, id: it.seriesInfo.id] }.unique().each{
						log.fine "Fetching series artwork for [$it.name / Season $it.season] to [$dir]"
						fetchSeriesArtworkAndNfo(hasSeasonFolder ? dir.parentFile : dir, dir, it.id, it.season, false, _args.language.locale)
					}
				}
			}
		} else if (failOnError) {
			fail "Failed to process group: $group"
		} else {
			unsortedFiles += files
		}
	}

	// MOVIE MODE
	else if (group.isMovie() && !group.isSeries() && !group.isAnime()) {
		def dest = rename(file: files, format: movieFormat, db: 'TheMovieDB')

		if (dest != null) {
			destinationFiles += dest

			if (artwork) {
				dest.mapByFolder().each{ dir, fs ->
					def movieFile = fs.findAll{ it.isVideo() || it.isDisk() }.sort{ it.length() }.reverse().findResult{ it }
					if (movieFile) {
						def movieInfo = movieFile.metadata
						log.fine "Fetching movie artwork for [$movieInfo] to [$dir]"
						fetchMovieArtworkAndNfo(dir, movieInfo, movieFile, extras, false, _args.language.locale)
					}
				}
			}
		} else if (failOnError) {
			fail "Failed to process group: $group"
		} else {
			unsortedFiles += files
		}
	}

	// MUSIC MODE
	else if (group.isMusic()) {
		def dest = rename(file: files, format: musicFormat, db: 'ID3')

		if (dest != null) {
			destinationFiles += dest
		} else if (failOnError) {
			fail "Failed to process group: $group"
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

// deal with remaining files that cannot be sorted automatically
if (unsorted) {
	if (unsortedFiles.size() > 0) {
		log.fine "Processing ${unsortedFiles.size()} unsorted files"

		def dest = rename(map: unsortedFiles.collectEntries{ original ->
			def destination = getMediaInfo(original, unsortedFormat) as File

			// sanity check user-defined unsorted format
			if (destination == null) {
				fail "Illegal usage: unsorted format must yield valid file path"
			}

			// resolve relative paths
			if (!destination.isAbsolute()) {
				destination = outputFolder.resolve(destination.path)
			}

			return [original, destination]
		})

		if (dest != null) {
			destinationFiles += dest
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


if (getRenameLog().size() > 0) {
	// messages used for kodi / plex / emby pushover notifications
	def getNotificationTitle = {
		def count = getRenameLog().count{ k, v -> !v.isSubtitle() }
		return "FileBot finished processing $count files"
	}.memoize()

	def getNotificationMessage = { prefix = '• ', postfix = '\n' -> 
		return ut.title ?: (input.findAll{ !it.isSubtitle() } ?: input).collect{ relativeInputPath(it) as File }.root.nameWithoutExtension.unique().collect{ prefix + it }.join(postfix).trim()
	}.memoize()

	// make Kodi scan for new content and display notification message
	if (kodi) {
		kodi.each{ instance ->
			log.fine "Notify Kodi: $instance"
			tryLogCatch {
				showNotification(instance.host, instance.port, getNotificationTitle(), getNotificationMessage(), 'https://app.filebot.net/icon.png')
				scanVideoLibrary(instance.host, instance.port)
			}
		}
	}

	// make Plex scan for new content
	if (plex) {
		plex.each{ instance ->
			log.fine "Notify Plex: $instance"
			tryLogCatch {
				refreshPlexLibrary(instance.host, null, instance.token)
			}
		}
	}

	// make Emby scan for new content
	if (emby) {
		emby.each{ instance ->
			log.fine "Notify Emby: $instance"
			tryLogCatch {
				refreshEmbyLibrary(instance.host, null, instance.token)
			}
		}
	}

	// mark episodes as 'acquired'
	if (myepisodes) {
		log.fine 'Update MyEpisodes'
		tryLogCatch {
			executeScript('update-mes', [login:myepisodes.join(':'), addshows:true], getRenameLog().values())
		}
	}

	if (pushover) {
		log.fine 'Sending Pushover notification'
		tryLogCatch {
			Pushover(pushover[0], pushover[1] ?: 'wcckDz3oygHSU2SdIptvnHxJ92SQKK').send(getNotificationTitle(), getNotificationMessage())
		}
	}

	// messages used for email / pushbullet reports
	def getReportSubject = { getNotificationMessage('', ' | ') }
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
					hr(); small("// Generated by ${Settings.applicationIdentifier} on ${InetAddress.localHost.hostName} at ${now}")
				}
			}
		}
	}

	// store processing report
	if (storeReport) {
		def reportFolder = ApplicationFolder.AppData.resolve('reports')
		def reportName = [now.format(/[yyyy-MM-dd HH mm]/), getReportSubject().take(50)].join(' ').validateFileName().space('_')
		def reportFile = getReportMessage().saveAs(reportFolder.resolve(reportName + '.html'))
		log.finest "Saving report as ${reportFile}"
	}

	// send pushbullet report
	if (pushbullet) {
		log.fine 'Sending PushBullet report'
		tryLogCatch {
			PushBullet(pushbullet).sendFile(getNotificationTitle() + '.html', getReportMessage(), 'text/html', getNotificationMessage(), any{ mailto }{ null })
		}
	}

	// send email report
	if (gmail || mail) {
		tryLogCatch {
			sendEmailReport(getReportTitle(), getReportMessage(), 'text/html')
		}
	}
}


// ---------- CLEAN UP ---------- //


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

// clean empty folders, clutter files, etc after move
if (clean) {
	if (['DUPLICATE', 'COPY', 'HARDLINK'].any{ it.equalsIgnoreCase(_args.action) } && temporaryFiles.size() > 0) {
		log.fine 'Clean temporary extracted files'
		// delete extracted files
		temporaryFiles.findAll{ it.isFile() }.sort().each{
			log.finest "Delete $it"
			it.delete()
		}
		// delete remaining empty folders
		temporaryFiles.findAll{ it.isDirectory() }.sort().reverse().each{
			log.finest "Delete $it"
			if (it.getFiles().size() == 0) {
				it.deleteDir()
			}
		}
	}

	// deleting remaining files only makes sense after moving files
	if ('MOVE'.equalsIgnoreCase(_args.action)) {
		def cleanerInput = args.size() > 0 ? args : ut.kind == 'multi' && ut.dir ? [ut.dir as File] : []
		cleanerInput = cleanerInput.findAll{ f -> f.exists() }
		if (cleanerInput.size() > 0) {
			log.fine 'Clean clutter files and empty folders'
			executeScript('cleaner', args.size() == 0 ? [root:true, ignore: ignore] : [root:false, ignore: ignore], cleanerInput)
		}
	}
}



if (destinationFiles.size() == 0) {
	fail "Finished without processing any files"
}
