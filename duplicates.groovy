#!/usr/bin/env filebot -script


delete = 'DELETE'.equalsIgnoreCase(_args.action)
binary = 'BINARY'.equalsIgnoreCase(_args.mode)

// Binary Duplicates: Keep Input Argument Order
// Logical Duplicates: Order by Video Quality
order  = 'INPUT'  .equalsIgnoreCase(_args.order) ? 'INPUT'
       : 'QUALITY'.equalsIgnoreCase(_args.order) ? 'QUALITY'
       : 'SIZE'   .equalsIgnoreCase(_args.order) ? 'SIZE'
       : 'DATE'   .equalsIgnoreCase(_args.order) ? 'DATE'
       : 'TIME'   .equalsIgnoreCase(_args.order) ? 'TIME'
       : _args.order ==~ /^[{].*[}]$/ ? __shell.callable(_args.order) // e.g. --order '{ a, b -> 0 }'
       : binary ? 'INPUT' : 'QUALITY'


// sanity checks
if (args.size() == 0) {
	die "Invalid usage: no input"
}


def group(files) {
	// Binary Duplicates: Group by File Size, then Fast MovieHash, then CRC32 via Xattr
	if (binary) {
		def groups = [:]

		// 0. Group by File Key (i.e. physical link duplicates are always binary duplicates)
		def links = files.groupBy{ f -> any{ f.key }{ f.canonicalFile }{ f } }.entrySet()

		// 1. Group by File Size
		links.groupBy{ it.value[0].length() }.sort{ -it.key }.each{ size, size_fs ->
			if (size_fs.size() == 1) {
				groups.put(size_fs[0].key, size_fs[0].value)
				return
			}
			log.finest "# Same Size: ${size} (${size_fs.size()})"

			// 2. Group by MovieHash
			size_fs.groupBy{ it.value[0].hash('moviehash') }.each{ hash, hash_fs ->
				if (hash_fs.size() == 1) {
					groups.put(hash_fs[0].key, hash_fs[0].value)
					return
				}
				log.finest "## Same Header: $hash (${hash_fs.size()})"

				// 3. Group by CRC32 via Xattr
				hash_fs.groupBy{ it.value[0].CRC32 }.each{ crc, crc_fs ->
					if (crc_fs.size() == 1) {
						groups.put(crc_fs[0].key, crc_fs[0].value)
						return
					}
					log.finest "### Same CRC: $crc (${crc_fs.size()})"

					def duplicates = crc_fs.value.flatten()
					log.fine "${'\n┌' + duplicates[0] + duplicates[1..<-1].collect{ f -> '│' + f + '\n' } +'└' + duplicates[-1] + '\n'}"

					// CHECK FOR HASH COLLISION (VERY SLOW) IN DELETE MODE ONLY
					def collision = delete && duplicates.tail().any{ f ->
						log.finest "#### CHECK BYTE FOR BYTE: $f (${f.displaySize})"
						def mismatch = f.mismatch(duplicates.head())
						if (mismatch >= 0) {
							log.severe "MISMATCH AT BYTE ${mismatch} [${f} ≠ ${duplicates.head()}]"
							return true
						}
						return false
					}
					if (collision) {
						die "HASH COLLISION DETECTED"
					}
					groups.put([size, hash, crc], duplicates)
				}
			}
		}
		return groups
	}

	// Logical Duplicates: Group by Xattr Metadata Object
	return files.findAll{ it.isVideo() }.groupBy{ f ->
		def m = f.metadata
		if (m == null) {
			log.finest "[XATTR NOT FOUND] $f"
			return null
		}
		// Strict Mode: group by metadata
		// Non-Lenient Mode: group by metadata and video format and HDR type
		return _args.strict ? m : [m, getMediaInfo(f, '{vf} {hdr}')]
	}
}


def order(files) {
	switch(order) {
		case 'INPUT':
			return files
		case 'QUALITY':
			return files.toSorted(VideoQuality.DESCENDING_ORDER)
		case 'SIZE':
			return files.toSorted{ -(it.length()) }
		case 'DATE':
			return files.toSorted{ -(it.mediaCharacteristics?.creationTime?.toEpochMilli() ?: it.creationDate) }
		case 'TIME':
			return files.toSorted{ -(it.lastModified()) }
		default:
			return files.toSorted(order)
	}
}


// select video files (and preserve input argument order)
def files = args.files
def duplicates = []


group(files).each{ g, fs ->
	if (g && fs.size() > 1) {
		log.finest "[*] $g"
		order(fs).unique().eachWithIndex{ f, i ->
			if (i == 0) {
				log.info "[+] 1. $f"
			} else {
				log.warning "[-] ${i+1}. $f"
				duplicates += f
			}
		}
	}
}


// no duplicates; return with NOOP
if (duplicates.size() == 0) {
	die "0 duplicates", ExitCode.NOOP
}


// continue with post-processing
log.info "${duplicates.size()} duplicates"


// select duplicate files and then pipe them to -rename as input
if (_args.rename) {
	rename(file: duplicates, db: binary ? 'file' : 'xattr')
	return
}


// select files from history and then pipe them to -mediainfo --format or -find -exec as input
if (_args.format || _args.exec || _args.apply) {
	getMediaInfo(file: duplicates)
	return
}


// delete duplicate files
if (delete) {
	duplicates.each{ f ->
		log.warning "[DELETE] $f"
		f.trash()
	}
}
