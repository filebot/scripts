#!/usr/bin/env filebot -script


def xattrFiles = []
def xattrFolders = [] as Set

args.flatten{ f -> f.isDirectory() ? f.listFiles{ true } : f }.each{ f ->
	// sanity check input file path
	if (!f.exists()) {
		log.warning "File does not exist: $f"
		return
	}

	// read / write custom xattr values
	_def.each{ k, v -> setXattrKey(f, k, v) }

	// select files with xattr metadata
	if (f.xattr.keySet().any{ k -> !isSystemKey(k) }) {
		xattrFiles += f
	}

	// manage .xattr folders
	if (f.name == /net.filebot.metadata/) {
		xattrFolders += f.dir.dir
	}
}


xattrFiles.each{ f ->
	log.finest "$f"
	f.xattr.each{ k, v -> log.fine "\t$k: $v" }

	if (_args.action =~ /clear/) {
		clear(f)
	}
	if (_args.action =~ /refresh/) {
		refresh(f)
	}
	if (_args.action =~ /import/) {
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
}


if (!xattrFiles && !xattrFolders) {
	log.warning "No xattr metadata found"
}




// hide system xattr keys
def isSystemKey(k) {
	k.startsWith('com.apple.') && _args.strict
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
def clear(f) {
	log.info "[CLEAR] $f.metadata [$f]"
	f.xattr.clear()
}


// refresh xattr metadata
def refresh(f) {
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


// import xattr metadata into Mac OS X Finder tags (UAYOR)
def kMDItemUserTags(f) {
	def xkey = 'com.apple.metadata:_kMDItemUserTags'
	def info = getMediaInfo(f, '''{if (movie) 'Movie'};{if (episode) 'Episode'};{y};{source};{vf};{hd}''')
	def tags = info.split(';')*.trim().findAll{ it.length() > 0 }

	def plist = XML{
		plist(version:'1.0') {
			array {
				tags.each{
					string(it)
				}
			}
		}
	}

	log.info "[IMPORT] Write tag plist to xattr [$xkey]: $tags"
	f.xattr[xkey] = plist
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

