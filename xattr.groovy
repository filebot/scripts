// filebot -script fn:xattr --action clear /path/to/files

args.getFiles{ f -> f.xattr.size() > 0 }.each{ f ->
	println f
	
	f.xattr.each{ k, v ->
		println "\t$k: $v"
		
		// auto-update pre-v41 xattr keys
		if (k in ['metadata', 'filename']) {
			def newk = "net.filebot.$k"
			if (f.xattr[newk] == null) {
				// UPDATE 
				println "\t\tIMPORT xattr $k => $newk"
				f.xattr[newk] = v
			}
		}
	}
	
	// clear xattr mode
	if (_args.action == 'clear') {
		f.xattr.clear()
		println '*** CLEARED ***'
	}
}
