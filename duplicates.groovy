#!/usr/bin/env filebot -script


delete = 'DELETE'.equalsIgnoreCase _args.action
binary = 'BINARY'.equalsIgnoreCase _args.mode


def group(files) {
	// binary duplicates: Group by File Size, then Fast MovieHash, then CRC32 via Xattr
	if (binary) {
		def groups = [:]
		// 1. Group by File Size
		files.groupBy{ it.length() }.each{ size, size_fs ->
			if (size_fs.size() == 1) {
				groups += [[size] : size_fs]
				return
			}
			// 2. Group by MovieHash
			size_fs.groupBy{ it.hash 'moviehash' }.each{ hash, hash_fs ->
				if (hash_fs.size() == 1) {
					groups += [[size, hash] : hash_fs]
					return
				}
				// 3. Group by CRC32 via Xattr
				hash_fs.groupBy{ it.CRC32 }.each{ crc, crc_fs ->
					groups += [[size, hash, crc] : crc_fs]
				}
			}
		}
		return groups
	}

	// logical duplicates: Group by Xattr Metadata Object
	return files.groupBy{ it.metadata }
}


def order(files) {
	// binary duplicates: Keep Input Argument Order
	if (binary) {
		return files
	}

	// logical duplicates: Order by Video Quality
	return files.toSorted(VideoQuality.DESCENDING_ORDER)
}


// select video files (and preserve input argument order)
def files = args.collectMany{ it.getFiles{ it.isVideo() } }


group(files).each{ m, fs ->
	if (m && fs.size() > 1) {
		log.info "[*] $m"

		order(fs).eachWithIndex{ f, i ->
			if (i == 0) {
				log.finest "[+] 1. $f"
			} else if (delete) {
				log.warning "[DELETE] ${i+1}. $f"
				f.trash()
			} else {
				log.fine "[-] ${i+1}. $f"
			}
		}
	}
}
