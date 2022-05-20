#!/usr/bin/env filebot -script


parallel(args.files.collect{ f ->
	return {
		def attr_hash = f.xattr['CRC32']
		def calc_hash = f.hash('crc32')

		if (attr_hash) {
			log.finest "$attr_hash $calc_hash $f"

			// abort if a CRC32 mismatch has been found
			if (attr_hash != calc_hash) {
				die "CRC32 mismatch: $attr_hash does not match: $calc_hash $f"
			}
		} else {
			// compute checksum if it cannot be read from xattr
			log.warning "Set CRC32 xattr: $calc_hash $f"

			f.xattr['CRC32'] = calc_hash
			f.xattr['CRC32.mtime'] = f.lastModified() as String

			// verify that xattr has been set correctly
			if (f.xattr['CRC32'] != calc_hash) {
				die "Failed to set CRC32 xattr: $f"
			}
		}
	}
})
