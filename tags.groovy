#!/usr/bin/env filebot -script


// require mkvtoolnix and mp4v2 tools
execute 'mkvpropedit', '--version'
execute 'mp4tags',     '--version'


void tag(f, m) {
	switch(f.extension) {
		case ~/mkv/:
			execute 'mkvpropedit', '--verbose', f, '--edit', 'info', '--set', "title=${m}"
			return
		case ~ /mp4|m4v/:
			def options = [
				'-song'        : '{object}',
				'-year'        : '{y}',
				'-show'        : '{n}',
				'-episode'     : '{e}',
				'-season'      : '{s}',
				'-description' : '{t}',
				'-hdvideo'     : '{hd =~ /HD/ ? 1 : 0}',
				'-type'        : '{any{episode; /tvshow/}{movie; /movie/}}',
				'-artist'      : '{any{episode.info.writer}{director}}',
				'-albumartist' : '{director}',
				'-genre'       : '{genre}',
				'-grouping'    : '{collection}',
				'-network'     : '{info.network}',
				'-longdesc'    : '{any{episode.info.overview}{info.overview}}'
			].collectMany{ option, format ->
				def value = getMediaInfo(f, format)
				return value ? [option, value] : []
			}
			execute('mp4tags', *options, f)
			return
		default:
			log.warning "[TAGS NOT SUPPORTED] $f"
			return
	}
}


args.getFiles{ it.video }.each{ f ->
	def m = f.metadata
	if (m) {
		log.config "[TAG] Write [$m] to [$f]"
		tag(f, m)
	} else {
		log.finest "[XATTR NOT FOUND] $f"
	}
}
