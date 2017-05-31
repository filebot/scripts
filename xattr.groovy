#!/usr/bin/env filebot -script


args.getFiles{ f -> f.xattr.size() > 0 }.each{ f ->
	log.finest "$f"

	f.xattr.each{ k, v ->
		log.info "\t$k: $v"
	}

	// clear xattr mode
	if (_args.action == 'clear') {
		f.xattr.clear()
		log.finest '*** CLEARED ***'
	}

	// import xattr metadata into Mac OS X Finder tags (UAYOR)
	if (_args.action == 'import') {
		def xkey = 'com.apple.metadata:_kMDItemUserTags'
		def info = getMediaInfo(f, '''{if (movie) 'Movie'};{if (episode) 'Episode'};{source};{vf};{sdhd}''')
		def tags = info.split(';')*.trim().findAll{ it.length() > 0 }

		def plist = '''<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n''' + XML{
			plist(version:'1.0') {
				array {
					tags.each{
						string(it)
					}
				}
			}
		}

		log.info "*** Write tag plist to xattr [$xkey]: $tags ***"
		f.xattr[xkey] = plist
	}
}
