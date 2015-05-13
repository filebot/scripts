// filebot -script fn:cleaner [--action test] /path/to/media/
def deleteRootFolder = tryQuietly{ root.toBoolean() }

/*
 * Delete orphaned "clutter" files like nfo, jpg, etc and sample files
 */
def isClutter(f) {
	// white list
	def ignore  = tryQuietly{ ignore }          ?: /extrathumbs/
	if (f.path.findMatch(ignore))
		return false
	
	// black list
	def exts    = tryQuietly{ exts }            ?: /jpg|jpeg|png|gif|nfo|info|xml|htm|html|log|srt|sub|idx|md5|sfv|txt|rtf|url|db|dna|log|tgmd|json|data|srv|srr|nzb|par\d+|part\d+/
	def terms   = tryQuietly{ terms }           ?: /sample|trailer|extras|deleted.scenes|music.video|scrapbook|DS_Store/
	def minsize = tryQuietly{ minsize as Long } ?:  20 * 1024 * 1024
	def maxsize = tryQuietly{ maxsize as Long } ?: 100 * 1024 * 1024
	
	// file is either too small to have meaning, or to large to be considered clutter
	def fsize = f.length()

	// path contains blacklisted terms or extension is blacklisted
	if (f.extension ==~ "(?i)($exts)" && fsize < maxsize)
		return true

	if (f.path =~ "(?i)\\b($terms)\\b" && fsize < maxsize)
		return true

	if ((f.isVideo() || f.isAudio()) && fsize < minsize)
		return true

	return false
}


def clean(f) {
	println "Delete $f"
	
	// do a dry run via --action test
	if (_args.action == 'test') {
		return false
	}
	
	return f.isDirectory() ? f.deleteDir() : f.delete()
}


// memoize media folder status for performance
def hasMediaFiles = { dir -> dir.isDirectory() && dir.getFiles().find{ (it.isVideo() || it.isAudio()) && !isClutter(it) } }.memoize()

// delete clutter files in orphaned media folders
args.getFiles{ isClutter(it) && !hasMediaFiles(it.dir) }.each { clean(it) }

// delete empty folders but exclude given args
args.getFolders().sort().reverse().each { if (it.isDirectory() && it.listFiles()?.length == 0) { if (deleteRootFolder || !args.contains(it)) clean(it) } }
