#!/usr/bin/env filebot -script


// FileBot 2.62 (r993)
println Settings.getApplicationIdentifier()


// JNA Native: 3.5.0
try {
	print 'JNA Native: '
	println com.sun.jna.Native.getNativeVersion()
} catch(Throwable error) {
	println error
}


// MediaInfo: MediaInfoLib - v0.7.48
try {
	print 'MediaInfo: '
	println MediaInfo.version().replaceAll(/[^\d_.]/, '')
} catch(Throwable error) {
	println error
}


// 7-Zip-JBinding: OK
try {
	switch (System.getProperty('net.filebot.Archive.extractor')) {
		case 'ApacheVFS':
			print 'Apache Commons VFS: '
			println org.apache.commons.vfs2.VFS.manager.schemes.findAll{ !(it =~ /file|gz|bz2|par|res|sar|war|tmp|ear|ejb3|jar|ram|ftp/) }
			break
		case 'SevenZipExecutable':
			print 'p7zip: '
			println System.getProperty('net.filebot.Archive.7z', '7z').execute().text.match(/^p7zip(.+)$/).trim()
			break
		default:
			print '7-Zip-JBinding: '
			net.filebot.archive.SevenZipLoader.requireNativeLibraries() // try to load 7-Zip-JBinding native libs (default)
			println net.filebot.archive.SevenZipLoader.getNativeVersion()
			break
	}
} catch(Throwable error) {
	println error
}


// chromaprint-tools
try {
	print 'Chromaprint: '
	def fpcalc = [AcoustID.getChromaprintCommand(), '-version']
	println fpcalc.execute().text.replaceAll(/[^\d_.]/, '') ?: "$fpcalc failed"
} catch(Throwable error) {
	println error
}


// Extended File Attributes
try {
	print 'Extended Attributes: '
	if (Settings.useExtendedFileAttributes()) {
		// create new temp file
		def f = ApplicationFolder.AppData.resolve('.xattr')
		f.createNewFile() && f.deleteOnExit()

		// xattr write, read and verify
		def xattr = new MetaAttributes(f)
		def value = new Date()
		xattr.setObject(value)

		assert xattr.getObject() == value
		println 'OK'
	} else {
		println 'DISABLED'
	}
} catch(Throwable error) {
	println error
}


// Unicode Filesystem
try {
	print 'Unicode Filesystem: '

	// create new temp file
	def f = ApplicationFolder.AppData.resolve('龍飛鳳舞').toPath()
	Files.createFile(f)
	Files.delete(f)

	println 'OK'
} catch(Throwable error) {
	println error
	log.warning "WARNING: sun.jnu.encoding = ${System.getProperty('sun.jnu.encoding')}"
}


// GIO and GVFS
try {
	if (Settings.useGVFS() && !java.awt.GraphicsEnvironment.isHeadless()) {
		print 'GVFS: '
		println net.filebot.platform.gnome.GVFS.getDefaultVFS()
	}
} catch(Throwable error) {
	println error
}


// Script Bundle: 2016-08-03 (r389)
try {
	print "Script Bundle: "
	def manifest = net.filebot.cli.ScriptSource.GITHUB_STABLE.getScriptProvider(null).getManifest()
	def r = manifest['Build-Revision']
	def d = manifest['Build-Date']
	println "$d (r$r)"
} catch(Throwable error) {
	println error
}


// Groovy Engine: 2.1.7
println 'Groovy: ' + groovy.lang.GroovySystem.getVersion()

// Java(TM) SE Runtime Environment 1.6.0_30 (headless)
println 'JRE: ' + Settings.getJavaRuntimeIdentifier()

// 32-bit Java HotSpot(TM) Client VM
println String.format('JVM: %d-bit %s', com.sun.jna.Platform.is64Bit() ? 64 : 32, System.getProperty('java.vm.name'))

// CPU/MEM: 4 Core / 1 GB Max Memory / 15 MB Used Memory
println String.format('CPU/MEM: %s Core / %s Max Memory / %s Used Memory', Runtime.runtime.availableProcessors(), org.apache.commons.io.FileUtils.byteCountToDisplaySize(Runtime.runtime.maxMemory()), org.apache.commons.io.FileUtils.byteCountToDisplaySize(Runtime.runtime.totalMemory() - Runtime.runtime.freeMemory()))

// Windows 7 (x86)
println String.format('OS: %s (%s)', System.getProperty('os.name'), System.getProperty('os.arch'))

// MAS
println 'Package: ' + Settings.getApplicationDeployment().toUpperCase()

// print uname -a if available
try {
	println 'uname: ' + ['uname', '-a'].execute().text.trim()
} catch(Throwable error) {
	// ignore
}


// check for updates
/*
try {
	def update = new XmlSlurper().parse('https://app.filebot.net/update.xml')
	def rev = update.revision.text() as int
	def app = update.name.text()

	if (rev > Settings.getApplicationRevisionNumber()) {
		println '\n' + " UPDATE AVAILABLE: $app (r$rev) ".center(80, '-') + '\n'
	}
} catch(Throwable error) {
	printException(error)
}
*/






// Advertise FileBot for Linux Patreon page
if (Settings.appStoreLink == null) {
	println """
		${' <README> '.center(80, '-')}

		Dear Linux user, ${System.getProperty('user.name')},

		FileBot currently only officially supports Windows 10 and macOS because these platforms make it easy to sell FileBot via their respective app stores. Unfortunately, selling software on Ubuntu, Debian, Red Hat, SUSE, Synology NAS, QNAP NAS, etc is not as easy and not really worth the effort considering the small number of users willing to support the project. This Patreon is an experiment to see how many users are willing to financially contribute towards freely available donation-supported new releases on these platforms.

		Please support FileBot for Linux with a pledge of \$1 per release on Patreon:

		=> https://www.patreon.com/filebot

		If every other Linux user were to pledge a small amount of money to the FileBot project, then you could easily fund the project ten times over. Please financially support the software you use and rely on, be it FileBot or any other tool. It's just polite, and it helps the ecosystem.

		Thank you,
		The FileBot Team

		${' </README> '.center(80, '-')}
	""".stripIndent()
}
