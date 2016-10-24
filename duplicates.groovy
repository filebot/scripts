// filebot -script fn:duplicates --action delete /path/to/media


def delete = 'DELETE'.equalsIgnoreCase(_args.action)


args.getFiles{ it.isVideo() }.groupBy{ it.metadata }.each{ m, fs ->
	if (m && fs.size() > 1) {
		log.info "[*] $m"

		fs.toSorted(new VideoQuality().reversed()).eachWithIndex{ f, i ->
			if (i == 0) {
				log.finest "[+] 1. $f"
			} else if (delete) {
				log.warning "[DELETE] ${i+1}. $f"
				Files.delete(f.toPath())
			} else {
				log.fine "[-] ${i+1}. $f"
			}
		}
	}
}
