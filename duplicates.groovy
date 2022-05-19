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
		links.groupBy{ it.value[0].length() }.each{ size, size_fs ->
			if (size_fs.size() == 1) {
				groups += [ (size_fs[0].key) : size_fs[0].value ]
				return
			}
			// 2. Group by MovieHash
			size_fs.groupBy{ it.value[0].hash('moviehash') }.each{ hash, hash_fs ->
				if (hash_fs.size() == 1) {
					groups += [ (hash_fs[0].key) : hash_fs[0].value ]
					return
				}
				// 3. Group by CRC32 via Xattr
				hash_fs.groupBy{ it.value[0].CRC32 }.each{ crc, crc_fs ->
					groups += [ ([size, hash, crc]) : crc_fs.collectMany{ it.value } ]
				}
			}
		}
		return groups
	}

	// Logical Duplicates: Group by Xattr Metadata Object
	return files.findAll{ it.isVideo() }.groupBy{ f ->
		return allOf{ f.metadata }{ f.metadata && _args.format ? getMediaInfo(f, _args.format) : null }
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
	}
}


// select video files (and preserve input argument order)
def files = args.collectMany{ it.files }
def duplicates = []


group(files).each{ g, fs ->
	if (g && fs.size() > 1) {
		log.info "[*] $g"
		order(fs).unique().eachWithIndex{ f, i ->
			if (i == 0) {
				log.finest "[+] 1. $f"
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
log.fine "${duplicates.size()} duplicates"


// -mediainfo post-processing
if (_args.mediaInfo) {
	getMediaInfo(file: duplicates)
}


// -rename post-processing
if (_args.rename) {
	rename(file: duplicates, db: binary ? 'file' : 'xattr')
}


// delete duplicate files
if (delete) {
	duplicates.each{ f ->
		log.info "[DELETE] $f"
		f.trash()
	}
}
