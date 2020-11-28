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


// Tools: fpcalc/1.5.0
def tools = [:]

// fpcalc version 1.5.0
tools['fpcalc'] = { AcoustID.version().match(/[.\d]{3,}/) }

// 7-Zip-JBinding: OK
try {
	if (net.filebot.archive.Archive.extractor =~ /SevenZipNativeBindings/) {
		print '7-Zip-JBinding: '
		net.filebot.archive.SevenZipLoader.requireNativeLibraries()
		println net.filebot.archive.SevenZipLoader.getNativeVersion()
	}
	if (net.filebot.archive.Archive.extractor =~ /ShellExecutables/) {
		net.filebot.archive.ShellExecutables.Command.each{ c ->
			tools[c] = { c.version().match(/[.\d]{3,}/) }
		}
	}
} catch(Throwable error) {
	println error
}

// ffprobe version 3.3.7
try {
	if (MediaCharacteristicsParser.getDefault() =~ /ffprobe/) {
		tools['ffprobe'] = { new net.filebot.media.FFProbe().version().match(/version=(\S+)/) }
	}
} catch(Throwable error) {
	// ignore
}

// mkvpropedit v50.0.0
try {
	net.filebot.postprocess.Tag.Command.each{ c -> 
		tools[c] = { c.version().match(/[.\d]+/) }
	}
} catch(Throwable error) {
	// ignore
}

print 'Tools: '
println tools.findResults{ c, v -> any{ "${c}/${v()}" }{ null } }.join(' ') ?: 'NONE'


// Extended File Attributes
try {
	print 'Extended Attributes: '
	if (Settings.useExtendedFileAttributes()) {
		// create new temp file
		def f = ApplicationFolder.TemporaryFiles.resolve('xattr.txt')
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
	def f = ApplicationFolder.TemporaryFiles.resolve('龍飛鳳舞.txt').toPath()
	Files.createFile(f)
	Files.delete(f)

	println 'OK'
} catch(Throwable error) {
	println error
	log.warning "WARNING: sun.jnu.encoding = ${System.getProperty('sun.jnu.encoding')}"
}


// GIO and GVFS
if (Settings.useGVFS() && !java.awt.GraphicsEnvironment.isHeadless()) {
	try {
		print 'GVFS: '
		println net.filebot.platform.gnome.GVFS.getDefaultVFS()
	} catch(Throwable error) {
		println error
	}
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

// OpenJFX 14.0.2.1+1
def jfx = [version: System.getProperty('javafx.runtime.version'), error: System.getProperty('javafx.runtime.error')]
if (jfx.version) {
	println "JFX: OpenJFX $jfx.version"
} else if (jfx.error) {
	println "JFX: $jfx.error"
}

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

	def cpuinfo = [] as Set
	'/proc/cpuinfo'.toFile().splitEachLine(/\:\s+/){ row ->
		if (row[0] =~ /^Hardware|^model.name/) {
			cpuinfo << row[1].trim()
		}
	}

	def meminfo = [] as Set
	'/proc/meminfo'.toFile().splitEachLine(/\:\s+/){ row ->
		if (row[0] =~ /^Mem|^Swap/) {
			def space = row[1].match(/\d+/).toLong().multiply(1024)
			if (space > 0) {
				meminfo << row[0].trim() + ": " + space.getDisplaySize()
			}
		}
	}

	println String.format('CPU/MEM: %s [%s]', cpuinfo.join(' | '), meminfo.join(' | '))
} catch(Throwable error) {
	// silently fail on non-Unix platforms
}


// apfs [/] @ 30 GB | smbfs [/Volumes/Media] @ 1.4 TB
try {
	print 'STORAGE: '
	println any{
		FileSystems.getDefault().getFileStores().findResults{
			def fs = it.type()
			def label = it.toString().replaceTrailingBrackets() ?: it
			def space = it.usableSpace
			// exclude clutter
			if (fs =~ /rootfs|tmpfs/ || label =~ /usr|etc|tmp|var|lib|boot|snap|private|docker|System|Recovery|Backups/ || space == 0) {
				return null
			}
			return "$fs [$label] @ $space.displaySize"
		}.join(' | ')
	}{ 'NONE' }
} catch(Throwable error) {
	println error
}


// C:\Users\FileBot\AppData\Roaming\FileBot
if (!Settings.isUWP()) {
	println 'DATA: ' + ApplicationFolder.AppData.get()
}


// SPK
println 'Package: ' + Settings.getApplicationDeployment().toUpperCase()


// Confinement: Devmode
if (System.getenv('SNAP')) {
	if (FileSystems.getDefault().getFileStores().iterator().hasNext()) {
		println 'Confinement: Devmode'
	} else {
		println 'Confinement: Strict # Restricted File System Access'
	}
}


// FileBot License T1000 (Valid-Until: 2019-06-20)
try {
	print 'License: '
	println Settings.LICENSE.check()
} catch(Throwable error) {
	println error.message
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
		println error
	}
}
