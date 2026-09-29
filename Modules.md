## Gramophone Lyrics parser

Commit: 6d71d69fb036a91decc65ed362cde84a3468c2c6

Source: https://github.com/FoedusProgramme/Gramophone

Path: /app/src/main/java/org/akanework/gramophone/logic

Notes: 
- The lyrics parser has been modified to work with OuterTune (usually) where denoted


## TreeDocumentFile

Commit: 1d17c1928669610eb315310418c406d3bc7df981 

Source: https://android.googlesource.com/platform/frameworks/support/

path: \app\src\main\java\androidx\documentfile\provider


## Gramophone ALAC decoder

Commit: 41f82847b23a9dec8326fda404bfaf423fc672c3

Source: https://github.com/FoedusProgramme/Gramophone

Path: /misc/alacdecoder/src/main/java

Notes:
- AlacRenderer.getOutputFormat() no longer sets the output channel mask, as the media3 version used here has no Format.channelMask
- The license text in /app/config/licenses/alacdecoder-bsd-3-clause.json is taken from license.txt of https://github.com/soiaf/Java-Apple-Lossless-decoder at commit afd5bf30ba51a80835f1eb4797c0bfe434e112de
