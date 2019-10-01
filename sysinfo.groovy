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
	println MediaInfo.version().removeAll(/[^\d_.]/)
} catch(Throwable error) {
	println error
}


// 7-Zip-JBinding: OK
try {
	switch (System.getProperty('net.filebot.archive.extractor')) {
		case 'ApacheVFS':
			print 'Apache Commons VFS: '
			println org.apache.commons.vfs2.VFS.manager.schemes.findAll{ !(it =~ /file|gz|bz2|par|res|sar|war|tmp|ear|ejb3|jar|ram|ftp/) }
			break
		case 'ShellExecutables':
			net.filebot.archive.ShellExecutables.Command.values().each{
				print "$it: "
				println it.version()
			}
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


// FFprobe: 3.2.4
try {
	if (MediaCharacteristicsParser.getDefault() =~ /ffprobe/) {
		print 'FFprobe: '
		println new net.filebot.media.FFProbe().version().match(/version=(\S+)/)
	}
} catch(Throwable error) {
	println error
}


// Chromaprint: fpcalc version 1.4.2
try {
	print 'Chromaprint: '
	println AcoustID.version().removeAll(/[^\d_.]/)
} catch(Throwable error) {
	println error
}


// Extended File Attributes
try {
	print 'Extended Attributes: '
	if (Settings.useExtendedFileAttributes()) {
		// create new temp file
		def f = ApplicationFolder.AppData.resolve('xattr.txt')
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
	print 'Script Bundle: '
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
try {
	print 'JVM: '
	println "${com.sun.jna.Platform.is64Bit() ? 64 : 32}-bit ${System.getProperty('java.vm.name')}"
} catch(Throwable error) {
	println error
}

// CPU/MEM: 4 Core / 1 GB Max Memory / 15 MB Used Memory
println String.format('CPU/MEM: %s Core / %s Max Memory / %s Used Memory', Runtime.runtime.availableProcessors(), Runtime.runtime.maxMemory().getDisplaySize(), (Runtime.runtime.totalMemory() - Runtime.runtime.freeMemory()).getDisplaySize())

// Windows 7 (x86)
println String.format('OS: %s (%s)', System.getProperty('os.name'), System.getProperty('os.arch'))


// Linux diskstation 3.2.40 #23739 Fri Jun 8 12:48:05 CST 2018 armv7l GNU/Linux synology_armada370_213j
try {
	println 'HW: ' + ['uname', '-a'].execute().text.trim()
} catch(Throwable error) {
	// silently fail on non-Unix platforms
}


// apfs [/] @ 30 GB | smbfs [/Volumes/Media] @ 1.4 TB
try {
	print 'STORAGE: '
	println FileSystems.getDefault().getFileStores().findAll{
		!(it.type() =~ /rootfs|tmpfs/ || it =~ /private/ || it.getTotalSpace() < 200e6)
	}.collect{
		"${it.type()} [${it.toString().replaceTrailingBrackets() ?: it}] @ ${it.getUsableSpace().getDisplaySize()}"
	}.join(' | ') ?: 'NONE'
} catch(Throwable error) {
	println error
}


// C:\Users\FileBot\AppData\Roaming\FileBot
println 'DATA: ' + ApplicationFolder.AppData.get()


// SPK
println 'Package: ' + Settings.getApplicationDeployment().toUpperCase()


// FileBot License T1000 (Valid-Until: 2019-06-20)
try {
	print 'License: '
	println Settings.LICENSE.check()
} catch(Throwable error) {
	println error.getMessage()
}


// CHECK FOR UPDATES
if (!Settings.isAutoUpdateEnabled()) {
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
}
