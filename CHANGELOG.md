<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Behat Go To File

## Unreleased

## [1.7.0]
- Strip surrounding quotes from filenames in Gherkin steps
- Demo project added

## [1.6.4]
- Nop release for testing auto update

## [1.6.3]
- Nop release for testing auto update

## [1.6.2]
- Nop release for testing auto update

## [1.6.0]
- Plugin auto updates from GitHub repository

## [1.5.1]
- Fixed: IDE freeze on file rename when GherkinElementFactory.createScenarioFromText fails
- Fixed: Handle null language and empty scenarios in rename refactoring

## [1.5.0]
- Performance: Pattern.compile extracted to companion object
- Performance: VFS recursive traversal replaced with direct path + FilenameIndex
- Performance: Added ProgressManager.checkCanceled to prevent EDT freezes
- Performance: Added ProjectFileIndex.isInContent filter to exclude vendor files

## [1.4.0]
- Support all file extensions (not limited to json, xml, txt)
- Minimum extension length of 2 characters to avoid false positives

## [1.3.0]
- Added support for 2025.2 and up
- Removed Rename File quick action


## [1.2.0]

- Support all files in directory alongside .feature file
- Support all files in all directories alongside .feature file
- Support relative paths (responses/response.json)
- Support multiple files in one step
- Supports txt, json and xml files
- Removed Inspection
- Removed Go To File action


## [1.0.0]

### Added
- Support line "ответ содержит данные значения, аналогичные файлу (.+)"
- Support folder "expected_responses"
- Change filename in gherkin step when file renamed
- Check that file exists or show quick fix "create file $filename"
- Go to file context action
- Find usages on file
