#!/usr/bin/env filebot -script


args.getFiles{ f -> f.xattr.size() > 0 }.each{ f ->
	log.finest "$f"
	f.xattr.each{ k, v -> log.fine "\t$k: $v" }

	// clear xattr mode
	if (_args.action == 'clear') {
		log.info "[CLEAR] $f.metadata [$f]"
		f.xattr.clear()
		return
	}

	if (_args.action == 'update') {
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
		return
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

		log.info "[IMPORT] Write tag plist to xattr [$xkey]: $tags"
		f.xattr[xkey] = plist
		return
	}
}
