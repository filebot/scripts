#!/usr/bin/env -S filebot -script


def hashType = any{ _args.mode.toUpperCase() }{ 'CRC32' }


parallel(args.files.collect{ f ->
	return {
		def attr_hash = f.xattr[hashType]
		def calc_hash = f.hash(hashType)

		if (attr_hash) {
			log.finest "$attr_hash $calc_hash $f"

			// abort if a mismatch has been found
			if (attr_hash != calc_hash) {
				die "$hashType mismatch: $attr_hash does not match: $calc_hash $f"
			}
		} else {
			// compute checksum if it cannot be read from xattr
			log.warning "Set $hashType xattr: $calc_hash $f"

			f.xattr[hashType] = calc_hash
			f.xattr[hashType + '.mtime'] = f.lastModified() as String

			// verify that xattr has been set correctly
			if (f.xattr[hashType] != calc_hash) {
				die "Failed to set $hashType xattr: $f"
			}
		}
	}
})
