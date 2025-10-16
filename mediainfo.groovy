#!/usr/bin/env -S filebot -script


// raw / slow mode (i.e. use libmediainfo and write xattr)
if (_args.mode == /raw/) {
	log.finest "# ${MediaInfo.version()}"

	// reset cache to force xattr reads
	def cache = Cache.getCache('mediainfo', CacheType.Monthly)
	if (cache.keys) {
		help "[CLEAR] ${cache} cache (${cache.keys.size()})"
		cache.clear()
	}

	return args.files.findAll{ f -> f.video || f.audio }.each{ f ->
		try(def mi = new MediaInfo()) {
			def read = mi.read(f, 8192)
			def raw = mi.raw()

			// print stats
			log.fine "\n# $f (${read.displaySize} of ${f.displaySize})"
			log.info "\n$raw"

			// minify and write to xattr
			if (raw) {
				f.xattr['net.filebot.mediainfo'] = raw.split(/\R+/).findResults{ it.replaceFirst(/[ ]+[:][ ]+/, '\t') }.join('\n')
				f.xattr['net.filebot.mediainfo.mtime'] = f.lastModified() as String
			}
		}
	}
}


// default / fast mode (i.e. use local cache or xattr or libmediainfo)
args.files.each{ f ->
	log.fine "\n# $f"
	try {
		f.mediaInfo.each{ kind ->
			kind.each{ stream ->
				log.finest "\n[${kind}]"

				// find optimal padding
				def pad = stream.keySet().flatten().collect{ it.length() }.max()
				stream.each{ k,v ->
					log.info "${k.padRight(pad)} : $v"
				}
			}
		}
	} catch (error) {
		log.severe "$error.message"
	}
}
