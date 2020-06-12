#!/usr/bin/env filebot -script


delete = 'DELETE'.equalsIgnoreCase _args.action
binary = 'BINARY'.equalsIgnoreCase _args.mode


// sanity checks
if (args.size() == 0) {
	die "Invalid usage: no input"
}


def group(files) {
	// Binary Duplicates: Group by File Size, then Fast MovieHash, then CRC32 via Xattr
	if (binary) {
		def groups = [:]

		// 0. Group by File Key
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
	return files.groupBy{ it.metadata }
}


def order(files) {
	// Binary Duplicates: Keep Input Argument Order
	if (binary) {
		return files
	}

	// Logical Duplicates: Order by Video Quality
	return files.toSorted(VideoQuality.DESCENDING_ORDER)
}


// select video files (and preserve input argument order)
def files = args.collectMany{ it.getFiles{ it.isVideo() } }
def duplicates = []


group(files).each{ m, fs ->
	if (m && fs.size() > 1) {
		log.info "[*] $m"
		order(fs).eachWithIndex{ f, i ->
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
