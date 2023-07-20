#!/usr/bin/env filebot -script

args.files.each{ f ->
	log.fine "\n[$f]"

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
