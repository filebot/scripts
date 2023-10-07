#!/usr/bin/env filebot -script


def deleteRootFolder = any{ root.toBoolean() }{ false }

def ignore  = any{ ignore }{ /extrathumbs/ }
def exts    = any{ exts }{ /jpg|jpeg|png|gif|ico|nfo|info|xml|htm|html|log|m3u|cue|ffp|srt|sub|idx|smi|sup|md5|sfv|txt|rtf|url|website|db|dna|exe|log|tgmd|json|data|ignore|srv|srr|nzb|vbs|ini|vsmeta/ }
def terms   = any{ terms }{ /sample|trailer|extras|deleted.scenes|music.video|scrapbook|DS_Store|eaDir/ }
def minsize = any{ minsize.toLong() }{ 20 * 1024 * 1024 }
def maxsize = any{ maxsize.toLong() }{ 120 * 1024 * 1024 }


def testRun = _args.action.equalsIgnoreCase('test')


// sanity checks
if (args.size() == 0) {
	die "Illegal usage: no input"
} else if (args.any{ it in File.listRoots() }) {
	die "Illegal usage: input $args must not include a filesystem root"
}


// delete orphaned "clutter" files like nfo, jpg, etc and sample files
def isClutter = { f ->
	// whitelist
	if (f.path.findWordMatch(ignore))
		return false

	// file is either too small to have meaning, or to large to be considered clutter
	def fsize = f.length()

	// path contains blacklisted terms or extension is blacklisted
	if (f.extension?.findWordMatch(exts) && fsize < maxsize)
		return true

	if (f.path.findWordMatch(terms) && fsize < maxsize)
		return true

	// NOTE: some smb filesystem implementations are buggy and known to incorrectly return filesize 0 for all files
	if (f.isVideo() && fsize < minsize && fsize > 0)
		return true

	return false
}


def clean = { f ->
	log.info "Delete $f"

	// do a dry run via --action test
	if (testRun) {
		return false
	}

	return f.isDirectory() ? f.deleteDir() : f.delete()
}


// memoize media folder status for performance
def hasMediaFiles = { dir ->
	return dir.isDirectory() && dir.getFiles().any{ f -> (f.isVideo() || f.isAudio()) && !isClutter(f) }
}.memoize()


// delete clutter files in orphaned media folders
args.files.each{ f ->
	// keep non-clutter files
	if (!isClutter(f)) {
		log.finest "Keep $f (not clutter)"
		return
	}

	// keep sibling files of non-clutter files
	if (hasMediaFiles(f.dir)) {
		log.finest "Keep $f (parent folder contains media files)"
		return
	}

	clean(f)
}


// delete empty folders but exclude given args
args.folders.toSorted().reverse().each{ d ->
	// skip input folder
	if (!deleteRootFolder && args.contains(d)) {
		log.finest "Keep $d (root folder)"
		return
	}

	// skip non-empty folder
	if (d.hasFile{ f -> f.isDirectory() || !isClutter(f) }) {
		log.finest "Keep $d (not empty)"
		return
	}

	// delete folder
	clean(d)
}
