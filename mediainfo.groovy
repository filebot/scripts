#!/usr/bin/env filebot -script


// raw / slow mode (i.e. use libmediainfo and write xattr)
if (_args.mode == /raw/) {
	log.finest "# ${MediaInfo.version()}"

	args.files.findAll{ f -> f.video || f.audio }.each{ f ->
		try(MediaInfo mi = new MediaInfo()) {
			def read = mi.openViaBuffer(f)
			def raw = mi.raw()

			// print stats
			log.finest "# $f [${read.displaySize} of ${f.displaySize}]"

			// write xattr
			if (raw) {
				f.xattr['net.filebot.mediainfo'] = raw
				f.xattr['net.filebot.mediainfo.mtime'] = f.lastModified() as String	
			}
		}
	}
}


// default / fast mode (i.e. use local cache or xattr or libmediainfo)
args.files.each{ f ->
	log.fine "\n# $f"

	f.mediaInfo.each{ kind ->
		kind.each{ stream -> 
			log.finest "\n${stream.StreamKind} #${stream.StreamKindID}"

			// find optimal padding
			def pad = stream.keySet().flatten().collect{ it.length() }.max()
			stream.each{ k,v -> 
				log.info "${k.padRight(pad)} : $v"
			}
		}
	}
}
