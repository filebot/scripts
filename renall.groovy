#!/usr/bin/env filebot -script


log.warning '''@Deprecated Please use -rename -r (for simple tasks) or the amc script (for complex tasks) instead of the renall script. The renall script primarily serves as minimal example for script developers.'''


/*
 * Rename all tv shows, anime or movies folder by folder using given or default options.
 */
def target = tryQuietly{ target } ?: 'file'
def byfile = tryQuietly{ byfile.toBoolean() }


args.withIndex().each{ f, i -> if (f.isDirectory()) { log.finest "Argument[$i]: $f" } else { log.warning "Argument[$i]: Path must be a directory: $f" } }


args.eachMediaFolder {
	if (it.isDisk()) {
		return rename(file:it) // rename disk folders instead of files regardless of mode
	}

	switch(target) {
		case 'file'   :   return byfile ? it.listFiles().findAll{ it.isVideo() || it.isSubtitle() }.findResults{ rename(file:it) } : rename(folder:it) // rename files within each folder
		case 'folder' :   return rename(file:it)   // rename folders as if they were files
	}
}
