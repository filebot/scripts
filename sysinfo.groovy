#!/usr/bin/env -S filebot -script


// FileBot 2.62 (r993)
println Settings.ApplicationIdentifier


def sys = System.Properties
def env = System.Env
def jre = Runtime.Runtime


// JNA Native: 3.5.0
try {
	print 'JNA Native: '
	println com.sun.jna.Native.NativeVersion
} catch(Throwable error) {
	println error
}

// MediaInfo: 23.07
try {
	print 'MediaInfo: '
	if (Settings.ApplicationRevisionNumber > 9970) {
		println MediaInfoTool.INSTANCE.version().removeAll(/[^\d_.]/)
	} else {
		println MediaInfo.version().removeAll(/[^\d_.]/)
	}
} catch(Throwable error) {
	println error
}


// Tools: fpcalc/1.5.1 7zz/21.06
def tools = [:]

// fpcalc/1.5.0
tools['fpcalc'] = { AcoustID.version().match(/[.\d]{3,}/) }


// 7-Zip-JBinding: OK
import net.filebot.archive.*

try {
	if (Archive.extractor =~ /SevenZipNativeBindings/) {
		print '7-Zip-JBinding: '
		SevenZipLoader.requireNativeLibraries()
		println SevenZipLoader.NativeVersion
	}
	if (Archive.extractor =~ /ShellExecutables/) {
		ShellExecutables.Command.each{ c ->
			tools[c] = { c.version().match(/[.\d]{3,}/) }
		}
	}
} catch(Throwable error) {
	println error
}

// ffprobe/4.1.9
try {
	if (MediaCharacteristicsParser.Default =~ /ffprobe/) {
		tools['ffprobe'] = { FFProbe.version().match(/version=(\S+)/) }
	}
	if (MediaInfoTable.mediainfo) {
		tools['mediainfo'] = { MediaInfoTable.mediainfo.version().removeAll(/[^\d_.]/) }
	}
} catch(Throwable error) {
	// ignore
}

// mkvpropedit v50.0.0
try {
	WriteTags.Command.each{ c -> 
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
		def f = getTemporaryFolder('sysinfo').createFile('Drægōñ飛Phöníx舞.txt')

		// ensure that file can be created
		Files.exists(f.toPath()) || Files.createFile(f.toPath())

		// xattr write, read and verify
		def xattr = new MetaAttributes(f)
		def value = new Date()
		xattr.setObject(value)
		assert xattr.getObject() == value

		// ensure that file can be deleted
		Files.delete(f.toPath())

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

	// create new temporary file
	def f = getTemporaryFolder('sysinfo').createFile('Drægōñ飛Phöníx舞.txt')

	// ensure that file can be created
	Files.exists(f.toPath()) || Files.createFile(f.toPath())

	// ensure that file can be written
	f.nameWithoutExtension.saveAs(f)

	// ensure that libmediainfo can work with the file
	if (Settings.ApplicationRevisionNumber > 9970) {
		def mi = any{ new MediaInfo() }{ null }
		if (mi) {
			mi.read(f, 32)
			if (mi.raw().length() < 750) {
				throw new EOFException("MediaInfo::read")
			}
			mi.close()
		}
	}

	// ensure that file operations work
	Files.delete(StandardRenameAction.COPY.rename(f, f.dir / 'Drægōñ飛Phöníx舞 (1).txt').toPath())
	Files.delete(StandardRenameAction.MOVE.rename(f, f.dir / 'Drægōñ飛Phöníx舞 (2).txt').toPath())

	println 'OK'
} catch(Throwable error) {
	println error
	log.warning "WARNING: sun.jnu.encoding = ${sys.'sun.jnu.encoding'}"
}


// GIO and GVFS
if (Settings.useGVFS() && !java.awt.GraphicsEnvironment.Headless) {
	try {
		print 'GVFS: '
		println net.filebot.platform.posix.GVFS.getDefaultVFS()
	} catch(Throwable error) {
		println error
	}
}


// Script Bundle: 2016-08-03 (r389)
println net.filebot.cli.ScriptSource.GITHUB_STABLE.Manifest

// Groovy Engine: 2.1.7
println 'Groovy: ' + GroovySystem.Version

// Java(TM) SE Runtime Environment 1.6.0_30 (headless)
println 'JRE: ' + Settings.JavaRuntimeIdentifier

// OpenJFX 14.0.2.1+1
['javafx.runtime.version', 'javafx.runtime.error'].each{ property ->
	if (sys[property]) {
		println "JFX: ${sys[property]}"
	}
}

// 32-bit Java HotSpot(TM) Client VM
try {
	print 'JVM: '
	def bit = com.sun.jna.Platform.is64Bit() ? '64-Bit' : '32-Bit'
	def jvm = sys.'java.vm.name'
	println(jvm =~ bit ? jvm : "$bit $jvm")

	// Synology NAS ships with Zero VM (which is extremely slow and has a very low default memory limit)
	if (jvm =~ /Zero.VM/) {
		log.warning "WARNING: BAD JVM = Zero VM"
	}
} catch(Throwable error) {
	println error
}

// JAVA_OPTS: -Xmx512m -XX:ActiveProcessorCount=1
['JAVA_OPTS', 'FILEBOT_OPTS'].each{ variable ->
	if (env[variable]) {
		println "$variable: ${env[variable]}"
	}
}

// System Property: net.filebot.web.WebRequest.v1=true
try {
	UserData.restoreUserDefinedSystemProperties{ name, value ->
		println "System Property: $name=$value"
	}
} catch(Throwable error) {
	// ignore
}

// CPU/MEM: 4 Core / 1 GB Max Memory / 15 MB Used Memory
println "CPU/MEM: ${jre.availableProcessors()} Core / ${jre.maxMemory().displaySize} Max Memory / ${(jre.totalMemory() - jre.freeMemory()).displaySize} Used Memory"

// Windows 7 (x86)
println "OS: ${sys.'os.name'} (${sys.'os.arch'})"


// Linux diskstation 3.2.40 #23739 Fri Jun 8 12:48:05 CST 2018 armv7l GNU/Linux synology_armada370_213j
try {
	println 'HW: ' + ['uname', '-a'].execute().text.trim()

	def info = [] as Set
	csv('/proc/cpuinfo').each{ k, v ->
		if (k =~ /^Hardware|^model.name/) {
			info << v
		}
	}

	def meminfo = [] as Set
	csv('/proc/meminfo').each{ k, v ->
		if (k =~ /^Mem|^Swap/) {
			def space = v.match(/\d+/).toLong().multiply(1024)
			if (space > 0) {
				info << "${k}: ${space.displaySize}"
			}
		}
	}

	println "CPU/MEM: ${info.join(' / ')}"
} catch(Throwable error) {
	// silently fail on non-Unix platforms
}


// DOCKER: 524 MB Max Memory
try {
	def maxMemory = text('/sys/fs/cgroup/memory.max').toLong()
	println "DOCKER: ${maxMemory.displaySize} Max Memory"

	// check if cgroup limit is lower than JVM limit
	if (maxMemory < jre.maxMemory()) {
		log.warning 'WARNING: cgroup memory limit is smaller than JRE memory limit'
	}
	if (maxMemory < 2e9) {
		log.warning 'WARNING: cgroup memory limit is smaller than 2 GB'
	}
} catch(Throwable error) {
	// silently fail on non-Unix platforms
}


// apfs [/] @ 30 GB | smbfs [/Volumes/Media] @ 1.4 TB
try {
	print 'STORAGE: '
	println any{
		FileSystems.Default.FileStores.findResults{ drive ->
			def fs = drive.type()
			def label = any{ drive.toString().replaceTrailingBrackets() }{ drive }
			def space = any{ drive.usableSpace }{ 0 }
			// exclude clutter
			if (fs =~ /rootfs|tmpfs/ || label =~ /usr|etc|tmp|var|lib|boot|snap|private|docker|timemachine|backup|System|Recovery|Backups|GoogleDrive|home[0-9]+$/ || space == 0) {
				return null
			}
			return "$fs [$label] @ $space.displaySize"
		}.join(' | ')
	}{ 'NONE' }
} catch(Throwable error) {
	println error
}


// uid=1024(admin) gid=100(users) groups=100(users),101(administrators)
if (!Settings.WindowsApp && !Settings.MacApp) {
	try {
		println 'UID/GID: ' + ['id'].execute().text.trim()
	} catch(Throwable error) {
		// cannot happen on Unix platforms
	}
}


// C:\Users\FileBot\AppData\Roaming\FileBot
if (!Settings.UWP) {
	println 'DATA: ' + ApplicationFolder.AppData.getDirectory()
}


// SPK
println 'Package: ' + Settings.ApplicationDeployment.upper()


// Confinement: Devmode
if (env.'SNAP') {
	if (FileSystems.Default.FileStores[0]) {
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
try {
	def update = xml('https://app.filebot.net/update.xml')

	def rev = update.revision.text() as int
	def app = update.name.text()

	if (rev > Settings.ApplicationRevisionNumber) {
		println '\n' + " UPDATE AVAILABLE: $app (r$rev) ".center(80, '-') + '\n'
	}
} catch(Throwable error) {
	println error

	if (error =~ /UnknownHost|SSL/) {
		println "DNS Network Error: Please contact your ISP or use CloudFlare DNS 1.1.1.1 instead of the default DNS provided by your ISP."
	}
}
