# FileBot Script Repository

FileBot makes scripting and automation as easy as it gets. 
Just script everything in Groovy instead of bothering with 
cumbersome cmd and bash scripts. It's the same on all platforms 
and much more powerful. The provided functions are the same as 
in the CLI and parameter usage is also exactly the same.

## Links
* [Usage and Examples](https://www.filebot.net/forums/viewtopic.php?t=5)
* [AMC Script Manual](https://www.filebot.net/amc.html)

## Clone
If you want to run scripts from a local file or if you want to make your own modifications just clone the repository into your local filesystem:
```
git clone https://github.com/filebot/scripts.git
```
Update to the latest revision and merge with local changes:
```
git pull
```
See local changes:
```
git diff
```
Discard local changes:
```
git reset --hard
```
You may call local Groovy scripts by specifiying the file path:
```
filebot -script /path/to/script.groovy
```
