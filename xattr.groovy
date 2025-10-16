#!/usr/bin/env -S filebot -script


def xattrFiles = []
def xattrFolders = [] as Set

args.flatten{ f -> f.isDirectory() ? f.listFiles(File::exists) : f }.each{ f ->
	// sanity check input file path
	if (!f.exists()) {
		log.warning "File does not exist: $f"
		return
	}

	// read / write custom xattr values
	_def.each{ k, v -> setXattrKey(f, k, v) }

	// select files with xattr metadata
	if (!_args.strict || f.xattr.list().any{ k -> isUserKey(k) }) {
		xattrFiles += f
	}

	// manage .xattr folders
	if (f.name == /net.filebot.metadata/ || f.name == /net.filebot.mediainfo/) {
		xattrFolders += f.dir.dir
	}
}


xattrFiles.each{ f ->
	log.finest "$f"
	f.xattr.each{ k, v ->
		if (v ==~ /(?U)[\p{Print}\p{Space}]*/) {
			log.fine "\t$k: $v"
		} else {
			// use 1 replacement character for each digit of magnitude
			def holder = v == null ? '␀' : '�' * v.length().toString().length()
			log.fine "\t$k: $holder"
		}
	}

	if (_args.action =~ /clear/) {
		clearXattr(f)
	}
	if (_args.action =~ /export/) {
		exportXattr(f)
	}
	if (_args.action =~ /dump/) {
		dumpXattr(f)
	}
	if (_args.action =~ /refresh/) {
		refreshMetadata(f)
	}
	if (_args.action =~ /finder/) {
		kMDItemUserTags(f)
	}
}


xattrFolders.each{ dir ->
	if (_args.action =~ /clear/) {
		clearXattrFolder(dir)
	}
	if (_args.action =~ /prune/) {
		pruneXattrFolder(dir)
	}
	if (_args.action =~ /import/) {
		dir.dir.listFiles(File::isFile).each{ f -> importXattr(f) }
	}
}


if (xattrFiles && _args.action =~ /clear/) {
	['mediainfo', 'ffprobe', 'CRC32'].each{ n ->
		def cache = Cache.getCache(n, CacheType.Monthly)
		if (cache.keys) {
			help "[CLEAR] ${cache} cache (${cache.keys.size()})"
			cache.clear()
		}
	}
}


if (!xattrFiles && !xattrFolders) {
	log.warning "No xattr metadata found"
}




// hide system xattr keys
def isUserKey(k) {
	return k.startsWith('net.filebot.') ||  k ==~ /\w+/
}


// clear xattr metadata
def setXattrKey(f, k, v) {
	if (v) {
		log.info "[SET] $f:$k = $v"
		f.xattr[k] = v
	} else if (f.xattr[k]) {
		log.info "[UNSET] $f:$k"
		f.xattr[k] = null
	}
}


// clear xattr metadata
def clearXattr(f) {
	log.info "[CLEAR] $f.metadata [$f]"
	f.xattr.clear()
}

// import .xattr folders into native xattr
def importXattr(f) {
	def xattrFolder = f.dir / '.xattr' / f.name
	if (xattrFolder.exists()) {
		xattrFolder.listFiles(File::isFile).each{ v ->
			if (isUserKey(v.name)) {
				log.info "[IMPORT] ${v}"
				f.xattr[v.name] = v.getText('UTF-8')
			}
		}
	}
}

// export native xattr into .xattr folders
def exportXattr(f) {
	f.xattr.list().findAll{ k -> isUserKey(k) }.each{ k ->
		def v = f.xattr.read(k).saveAs(f.dir / '.xattr' / f.name / k)
		log.info "[EXPORT] ${v}"
	}
}

// dump xattr metadata as plain/text files
def dumpXattr(f) {
	f.xattr.list().each{ k ->
		def v = f.xattr.read(k).saveAs("${f}#${k}")
		log.info "[DUMP] ${v} (${v.displaySize})"
	}
}


// refresh xattr metadata
def refreshMetadata(f) {
	def e = f.metadata
	if (e instanceof Episode) {
		def i = e.seriesInfo
		log.finest "[UPDATE] $i | $e [$f]"
		if (i instanceof SeriesInfo) {
			def episodeList = WebServices.getEpisodeListProvider(i.database).getEpisodeList(i.id, i.order as SortOrder, i.language as Locale)
			if (e instanceof MultiEpisode) {
				e = e.episodes.collect{ p -> episodeList.find{ it.id == p.id } } as MultiEpisode
			} else {
				e = episodeList.find{ it.id == e.id } as Episode
			}
			// update xattr metadata
			if (e) {
				f.metadata = e
			}
		}
	}
}


// export xattr metadata into Mac OS X Finder tags (UAYOR)
def kMDItemUserTags(f) {
	def tags = getMediaInfo(f, _args.format ?: '{movie; /Movie/}|{episode; /Episode/}|{y}|{source}|{vf}|{hd}').tokenize('|').findResults{ it }

	def plist = XML{
		plist(version:'1.0') {
			array {
				tags.each{
					string(it)
				}
			}
		}
	}

	log.info "[FINDER] Write tag plist to xattr [com.apple.metadata:_kMDItemUserTags]: $plist"
	f.xattr['com.apple.metadata:_kMDItemUserTags'] = plist
}


// delete .xattr folders
def clearXattrFolder(dir) {
	log.info "[DELETE] $dir"
	dir.trash()
}


// delete .xattr/<name> folders if <name> no longer exists
def pruneXattrFolder(dir) {
	dir.listFiles().each{ key ->
		def f = key.dir.dir / key.name
		if (!f.exists()) {
			log.info "[DELETE] $key"
			key.trash()
		}
	}
}

